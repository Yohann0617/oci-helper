package com.yohann.ocihelper.config.ws;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.jwt.JWTUtil;
import com.yohann.ocihelper.exception.OciException;
import com.yohann.ocihelper.utils.CommonUtils;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;

import java.io.IOException;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.yohann.ocihelper.service.impl.OciServiceImpl.TEMP_MAP;

/**
 * @author Yohann
 * @date 2024-12-25 16:23:39
 */
@Slf4j
@Component
@ServerEndpoint("/metrics/{token}")
public class MetricsWebSocketHandler {

    private static final ConcurrentHashMap<String, Session> SESSION_MAP = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> IS_OPEN_MAP = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Future<?>> FUTURE_MAP = new ConcurrentHashMap<>();
    /**
     * Reused across calls so CPU tick-delta is meaningful
     */
    private CentralProcessor processor;
    private long[] prevCpuTicks;
    Map<String, Object> metrics = new HashMap<>();
    List<String> timestamps = new LinkedList<>();
    List<Double> inRates = new LinkedList<>();
    List<Double> outRates = new LinkedList<>();
    int interval = 5;
    int size = 15;

    private boolean validateToken(String token) {
        return !CommonUtils.isTokenExpired(token) && JWTUtil.verify(token, ((String) TEMP_MAP.get("password")).getBytes());
    }

    @OnOpen
    public void onOpen(Session session, @PathParam(value = "token") String token) {
        if (token == null || !validateToken(token)) {
            throw new OciException(-1, "无效的token");
        }

        // 如果已存在旧的 session，先关闭它
        Session oldSession = SESSION_MAP.get(token);
        if (oldSession != null) {
            try {
                oldSession.close();
            } catch (IOException e) {
                log.error("Close old session error", e);
            }
        }

        SESSION_MAP.put(token, session);
        IS_OPEN_MAP.put(token, true);

        genCpuMemData(token);
        execGenTrafficData(token);
    }

    @OnClose
    public void onClose(Session session, @PathParam(value = "token") String token) {
        SESSION_MAP.remove(token);
        IS_OPEN_MAP.remove(token);
        // 取消正在运行的任务
        Future<?> future = FUTURE_MAP.remove(token);
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
    }

    @OnMessage
    public void onMessage(String message) {
        log.info("【WebSocket消息】收到客户端消息：" + message);
    }

    /**
     * 此为单点消息
     *
     * @param message 消息
     */
    public void sendOneMessage(Session session, String message) {
        if (session != null && session.isOpen()) {
            try {
                synchronized (session) {
                    session.getAsyncRemote().sendText(message);
                }
            } catch (Exception e) {
                log.error("仪表盘数据推送失败", e);
            }
        }
    }

    private void genCpuMemData(String token) {
        // --- CPU ---
        // processor is initialised once per connection (in execGenTrafficData) so that
        // prevCpuTicks and the current ticks are separated by a real time interval.
        double cpu = 0.0;
        if (processor != null && prevCpuTicks != null) {
            cpu = processor.getSystemCpuLoadBetweenTicks(prevCpuTicks) * 100;
        }
        // Take the next baseline snapshot immediately after reading
        if (processor != null) {
            prevCpuTicks = processor.getSystemCpuLoadTicks();
        }
        String cpuUsage = String.format("%.2f", cpu);
        metrics.put("cpuUsage", MapUtil.builder()
                .put("used", cpuUsage)
                .put("free", String.format("%.2f", 100 - Double.parseDouble(cpuUsage)))
                .build());

        // --- Memory ---
        GlobalMemory memory = new SystemInfo().getHardware().getMemory();
        long totalMemory = memory.getTotal();
        long availableMemory = memory.getAvailable();
        long usedMemory = totalMemory - availableMemory;
        double usedMemoryPercentage = ((double) usedMemory / totalMemory) * 100;
        double freeMemoryPercentage = ((double) availableMemory / totalMemory) * 100;

        metrics.put("memoryUsage", MapUtil.builder()
                .put("used", String.format("%.2f", usedMemoryPercentage))
                .put("free", String.format("%.2f", freeMemoryPercentage))
                .build());

        metrics.put("trafficData", MapUtil.builder()
                .put("timestamps", timestamps)
                .put("inbound", inRates)
                .put("outbound", outRates)
                .build());

        Session userSession = SESSION_MAP.get(token);
        if (userSession != null && userSession.isOpen()) {
            sendOneMessage(userSession, JSONUtil.toJsonStr(metrics));
        }
    }

    private void execGenTrafficData(String token) {
        Future<?> future = Executors.newSingleThreadExecutor().submit(() -> {
            SystemInfo systemInfo = new SystemInfo();
            HardwareAbstractionLayer hardware = systemInfo.getHardware();

            // Initialise the shared CPU processor and take the first tick baseline.
            // The first genCpuMemData() call (interval seconds later) will then have
            // a meaningful delta to calculate real CPU usage.
            processor = hardware.getProcessor();
            prevCpuTicks = processor.getSystemCpuLoadTicks();

            List<NetworkIF> networkIFs = hardware.getNetworkIFs();
            NetworkIF networkIF = networkIFs.stream()
                    .filter(NetworkIF::isConnectorPresent)
                    .filter(iface -> !Arrays.asList(iface.getIPv4addr()).isEmpty() || !Arrays.asList(iface.getIPv6addr()).isEmpty())
                    .filter(iface -> iface.getName().startsWith("e"))
                    .min((a, b) -> Long.compare(b.getSpeed(), a.getSpeed()))
                    .orElse(null);

            if (null != networkIF) {
                // First snapshot — raw bytes, no division yet
                networkIF.updateAttributes();
                long previousRxBytes = networkIF.getBytesRecv();
                long previousTxBytes = networkIF.getBytesSent();

                while (IS_OPEN_MAP.getOrDefault(token, false)) {
                    try {
                        Thread.sleep(interval * 1000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }

                    // Record the timestamp right after waking up
                    LocalTime now = LocalTime.now();
                    String timestamp = String.format("%02d:%02d:%02d",
                            now.getHour(), now.getMinute(), now.getSecond());

                    networkIF.updateAttributes();
                    long currentRxBytes = networkIF.getBytesRecv();
                    long currentTxBytes = networkIF.getBytesSent();

                    // Rate in KB/s: delta bytes / interval seconds / 1024
                    double rxRate = (currentRxBytes - previousRxBytes) / (double) interval / 1024.0;
                    double txRate = (currentTxBytes - previousTxBytes) / (double) interval / 1024.0;

                    previousRxBytes = currentRxBytes;
                    previousTxBytes = currentTxBytes;

                    // Keep sliding window
                    if (inRates.size() == size) inRates.remove(0);
                    if (outRates.size() == size) outRates.remove(0);
                    if (timestamps.size() == size) timestamps.remove(0);

                    inRates.add(Double.parseDouble(String.format("%.2f", rxRate)));
                    outRates.add(Double.parseDouble(String.format("%.2f", txRate)));
                    timestamps.add(timestamp);

                    genCpuMemData(token);
                }
            }
        });

        FUTURE_MAP.put(token, future);
    }
}
