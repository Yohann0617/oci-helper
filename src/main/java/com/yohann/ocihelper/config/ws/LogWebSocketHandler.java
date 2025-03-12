package com.yohann.ocihelper.config.ws;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.Tailer;
import cn.hutool.jwt.JWTUtil;
import com.yohann.ocihelper.utils.CommonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.TailerListener;
import org.apache.commons.io.input.TailerListenerAdapter;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.*;

import static com.yohann.ocihelper.service.impl.OciServiceImpl.TEMP_MAP;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.utils
 * @className: LogWebSocketHandler
 * @author: Yohann
 * @date: 2024/11/17 18:21
 */
@Slf4j
@Component
public class LogWebSocketHandler extends TextWebSocketHandler {
    private static WebSocketSession currentSession;
    private final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
    private final ExecutorService pushThreadExecutor = Executors.newSingleThreadExecutor();
    private final Deque<String> recentLogs = new ConcurrentLinkedDeque<>();
    private static final int MAX_RECENT_LOGS = 200;
    Tailer tailer;
    Future<?> logPushTask;
    private volatile boolean close = false;
    private volatile boolean isSenderRunning = false;

    private String getTokenFromSession(WebSocketSession session) {
        // 解析 URI 中的 token 参数
        String query = session.getUri().getQuery();
        if (query != null && query.contains("token=")) {
            return query.replaceAll(".*token=([^&]*).*", "$1");
        }
        return null;
    }

    private boolean validateToken(String token) {
        return !CommonUtils.isTokenExpired(token) && JWTUtil.verify(token, ((String) TEMP_MAP.get("password")).getBytes());
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String token = getTokenFromSession(session);
        if (token == null || !validateToken(token)) {
            return;
        }

        close = false;
        if (currentSession != null && currentSession.isOpen()) {
            try {
                currentSession.close();
            } catch (IOException e) {
                log.error("Error while closing old WebSocket session: {}", e.getLocalizedMessage());
            }
        }
        currentSession = session;

        try {
            startLogTailer(CommonUtils.LOG_FILE_PATH);
        } catch (Exception e) {
            if (e.getLocalizedMessage().contains("Negative seek offset")) {
                List<String> readUtf8Lines = FileUtil.readUtf8Lines(CommonUtils.LOG_FILE_PATH);
                readUtf8Lines.add("\n");
                FileUtil.writeUtf8Lines(new ArrayList<>(MAX_RECENT_LOGS), CommonUtils.LOG_FILE_PATH);
                FileUtil.writeUtf8Lines(readUtf8Lines, CommonUtils.LOG_FILE_PATH);
                tailer.stop();
                startLogTailer(CommonUtils.LOG_FILE_PATH);
            } else {
                log.error("启动日志监听服务失败：{}", e.getLocalizedMessage(), e);
            }
        }

        sendRecentLogs();
        startMessageSender();
    }

    private void sendRecentLogs() {
        if (currentSession == null || !currentSession.isOpen() || !close) {
            return;
        }

        synchronized (recentLogs) {
            recentLogs.forEach(recentLog -> {
                try {
                    currentSession.sendMessage(new TextMessage(recentLog));
                } catch (IOException e) {
                    log.error("Error while sending recent log: {}", e.getLocalizedMessage());
                }
            });
        }
    }

    private void startLogTailer(String filePath) {
        File logFile = new File(filePath);
        if (!logFile.exists() || !logFile.isFile()) {
            log.error("Invalid log file path: {}", filePath);
            return;
        }

        tailer = new Tailer(logFile, Charset.defaultCharset(), line -> {
            try {
                if (!close) {
                    messageQueue.put(line);

                    synchronized (recentLogs) {
                        if (recentLogs.size() >= MAX_RECENT_LOGS) {
                            recentLogs.pollFirst();
                        }
                        recentLogs.addLast(line);
                    }
                }
            } catch (Exception e) {
                Thread.currentThread().interrupt();
                log.error("Failed to enqueue log line: {}", e.getLocalizedMessage());
            }
        }, MAX_RECENT_LOGS, 1000);
        tailer.start(true);
    }

    private void startMessageSender() {
        if (isSenderRunning) {
            return;
        }
        isSenderRunning = true;

        logPushTask = pushThreadExecutor.submit(() -> {
            try {
                while (!close) {
                    String message = messageQueue.take();
                    synchronized (LogWebSocketHandler.class) {
                        if (currentSession != null && currentSession.isOpen()) {
                            currentSession.sendMessage(new TextMessage(message));
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error while sending WebSocket message: {}", e.getLocalizedMessage());
            } finally {
                isSenderRunning = false;
            }
        });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        try {
            tailer.stop();
            if (logPushTask != null) {
                logPushTask.cancel(false);
            }
            if (session == currentSession) {
                currentSession.close();
                currentSession = null;
            } else {
                session.close();
            }
            close = true;
        } catch (Exception e) {
            log.error("WebSocket session closed: {}", session.getId());
        }
    }
}