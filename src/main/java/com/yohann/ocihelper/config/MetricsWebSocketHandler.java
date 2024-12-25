package com.yohann.ocihelper.config;

import cn.hutool.core.map.MapUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.jwt.JWTUtil;
import com.yohann.ocihelper.exception.OciException;
import com.yohann.ocihelper.utils.CommonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
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
    Map<String, Object> metrics = new HashMap<>();
    List<String> timestamps = new ArrayList<>();
    List<Double> inRates = new LinkedList<>();
    List<Double> outRates = new LinkedList<>();

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
        System.out.println("发送消息->" + session.getId());
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
        SystemInfo systemInfo = new SystemInfo();

        // 获取 CPU 使用率
        HardwareAbstractionLayer hardware = systemInfo.getHardware();
        CentralProcessor processor = hardware.getProcessor();
        long[] systemCpuLoadTicks = processor.getSystemCpuLoadTicks();
        double cpu = processor.getSystemCpuLoadBetweenTicks(systemCpuLoadTicks) * 100;
        String cpuUsage = String.format("%.2f", cpu);
        metrics.put("cpuUsage", MapUtil.builder()
                .put("used", cpuUsage)
                .put("free", String.valueOf(100 - Double.parseDouble(cpuUsage)))
                .build());

        // 获取内存使用情况
        GlobalMemory memory = systemInfo.getHardware().getMemory();
        long totalMemory = memory.getTotal();
        long availableMemory = memory.getAvailable();
        long usedMemory = totalMemory - availableMemory;
        // 计算内存使用率百分比
        double usedMemoryPercentage = ((double) usedMemory / totalMemory) * 100;
        double freeMemoryPercentage = ((double) availableMemory / totalMemory) * 100;

        metrics.put("memoryUsage", MapUtil.builder()
                .put("used", String.format("%.2f", usedMemoryPercentage))
                .put("free", String.format("%.2f", freeMemoryPercentage))
                .build());

        Collections.reverse(timestamps);
        metrics.put("trafficData", MapUtil.builder()
                .put("timestamps", timestamps)
                .put("inbound", inRates)
                .put("outbound", outRates)
                .build());

        // 发送消息时使用对应的 session
        Session userSession = SESSION_MAP.get(token);
        if (userSession != null && userSession.isOpen()) {
            sendOneMessage(userSession, JSONUtil.toJsonStr(metrics));
        }
    }

    private void execGenTrafficData(String token) {
        Future<?> future = Executors.newSingleThreadExecutor().submit(() -> {
            int interval = 5;
            SystemInfo systemInfo = new SystemInfo();
            List<NetworkIF> networkIFs = systemInfo.getHardware().getNetworkIFs();

            NetworkIF networkIF = null;
            for (NetworkIF x : networkIFs) {
                if (x.getName().startsWith("eth3")) {
                    networkIF = x;
                    break;
                }
            }

            if (null != networkIF) {
                networkIF.updateAttributes();
                double previousRxBytes = networkIF.getBytesRecv();
                double previousTxBytes = networkIF.getBytesSent();

                double currentRxBytes = networkIF.getBytesRecv() / 1024.0;
                double currentTxBytes = networkIF.getBytesSent() / 1024.0;

                // 计算当前秒的流量速率（单位：KB/s）
                double rxRate = (currentRxBytes - previousRxBytes) / 1024.0;
                double txRate = (currentTxBytes - previousTxBytes) / 1024.0;

                // 更新上一秒的字节数
                previousRxBytes = currentRxBytes;
                previousTxBytes = currentTxBytes;

                Calendar calendar = Calendar.getInstance();

                while (IS_OPEN_MAP.getOrDefault(token, false)) {
                    try {
                        Thread.sleep(interval * 1000); // 每秒更新一次
                    } catch (InterruptedException e) {

                    }
                    networkIF.updateAttributes();

                    currentRxBytes = networkIF.getBytesRecv() / 1024.0;
                    currentTxBytes = networkIF.getBytesSent() / 1024.0;

                    // 计算当前秒的流量速率（单位：KB/s）
                    rxRate = (currentRxBytes - previousRxBytes) / 1024.0;
                    txRate = (currentTxBytes - previousTxBytes) / 1024.0;

                    // 更新上一秒的字节数
                    previousRxBytes = currentRxBytes;
                    previousTxBytes = currentTxBytes;

                    // 维护队列大小为10
                    if (inRates.size() == 10) {
                        inRates.remove(0);
                    }
                    if (outRates.size() == 10) {
                        outRates.remove(0);
                    }
                    if (timestamps.size() == 10) {
                        timestamps.remove(0);
                    }
                    inRates.add(Double.valueOf(String.format("%.2f", rxRate)));
                    outRates.add(Double.valueOf(String.format("%.2f", txRate)));
                    timestamps.add(String.format("%02d:%02d:%02d",
                            calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), calendar.get(Calendar.SECOND)));
                    calendar.add(Calendar.SECOND, -interval);

                    genCpuMemData(token);
                }
            }
        });

        FUTURE_MAP.put(token, future);
    }
}
