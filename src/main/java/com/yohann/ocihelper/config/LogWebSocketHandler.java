package com.yohann.ocihelper.config;

import cn.hutool.core.io.file.Tailer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.*;

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
    private final ExecutorService logThreadExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService pushThreadExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean close = false;
    private volatile boolean isSenderRunning = false;

    public LogWebSocketHandler() {
        try {
            startLogTailer("/var/log/oci-helper.log");
        } catch (Exception e) {
            log.error("启动日志监听服务失败：{}", e.getLocalizedMessage());
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        close = false;
        if (currentSession != null && currentSession.isOpen()) {
            try {
                currentSession.close();
            } catch (IOException e) {
                log.error("Error while closing old WebSocket session: {}", e.getLocalizedMessage());
            }
        }
        currentSession = session;
        startMessageSender();
    }

    private void startLogTailer(String filePath) {
        File logFile = new File(filePath);
        if (!logFile.exists() || !logFile.isFile()) {
            log.error("Invalid log file path: {}", filePath);
            return;
        }

        logThreadExecutor.submit(() -> new Tailer(logFile, Charset.defaultCharset(), line -> {
            try {
                if (!close) {
                    messageQueue.put(line); // 将日志行加入队列
                }
            } catch (Exception e) {
                Thread.currentThread().interrupt();
                log.error("Failed to enqueue log line: {}", e.getLocalizedMessage());
            }
        }, 10, 1000).start());
        // 每 1 秒检查一次
    }

    private void startMessageSender() {
        if (isSenderRunning) {
            return;
        }
        isSenderRunning = true;

        pushThreadExecutor.submit(() -> {
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
            if (session == currentSession) {
                currentSession.close();
                currentSession = null;
            } else {
                session.close();
            }
            close = true;
        } catch (IOException e) {
            log.error("WebSocket session closed: {}", session.getId());
        }
    }
}