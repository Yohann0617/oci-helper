package com.yohann.ocihelper.telegram.handler.impl;

import com.yohann.ocihelper.telegram.builder.KeyboardBuilder;
import com.yohann.ocihelper.telegram.handler.AbstractCallbackHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import oshi.SystemInfo;
import oshi.hardware.*;
import oshi.software.os.FileSystem;
import oshi.software.os.OperatingSystem;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * System metrics callback handler
 * Query server resource usage (CPU, Memory, Disk, Network, etc.)
 * 
 * @author yohann
 */
@Slf4j
@Component
public class SystemMetricsHandler extends AbstractCallbackHandler {
    
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        try {
            String metricsInfo = collectSystemMetrics();
            
            // Build keyboard with refresh button
            List<InlineKeyboardRow> keyboard = List.of(
                    new InlineKeyboardRow(
                            KeyboardBuilder.button("🔄 刷新", "system_metrics")
                    ),
                    KeyboardBuilder.buildBackToMainMenuRow(),
                    KeyboardBuilder.buildCancelRow()
            );
            
            return buildEditMessage(
                callbackQuery,
                metricsInfo,
                new InlineKeyboardMarkup(keyboard)
            );
            
        } catch (Exception e) {
            log.error("获取系统资源信息失败", e);
            
            List<InlineKeyboardRow> keyboard = List.of(
                    new InlineKeyboardRow(
                            KeyboardBuilder.button("🔄 重试", "system_metrics")
                    ),
                    KeyboardBuilder.buildBackToMainMenuRow(),
                    KeyboardBuilder.buildCancelRow()
            );
            
            return buildEditMessage(
                callbackQuery,
                "❌ 获取系统资源信息失败: " + e.getMessage(),
                new InlineKeyboardMarkup(keyboard)
            );
        }
    }
    
    /**
     * Collect system metrics information
     * 
     * @return formatted metrics string
     */
    private String collectSystemMetrics() {
        SystemInfo systemInfo = new SystemInfo();
        HardwareAbstractionLayer hardware = systemInfo.getHardware();
        OperatingSystem os = systemInfo.getOperatingSystem();
        
        StringBuilder sb = new StringBuilder();
        sb.append("📊 系统资源监控\n\n");
        
        // System Info
        sb.append(getSystemInfo(os, hardware));
        sb.append("\n");
        
        // CPU Info
        sb.append(getCpuInfo(hardware.getProcessor()));
        sb.append("\n");
        
        // Memory Info
        sb.append(getMemoryInfo(hardware.getMemory()));
        sb.append("\n");
        
        // Disk Info
        sb.append(getDiskInfo(os.getFileSystem()));
        sb.append("\n");
        
        // Network Info
        sb.append(getNetworkInfo(hardware.getNetworkIFs()));
        sb.append("\n");
        
        // Uptime
        sb.append(getUptimeInfo(os));
        
        sb.append("\n");
        sb.append("⏰ 更新时间: ");
        sb.append(Instant.now().atZone(ZoneId.systemDefault()).format(TIME_FORMATTER));
        
        return sb.toString();
    }
    
    /**
     * Get system basic information
     */
    private String getSystemInfo(OperatingSystem os, HardwareAbstractionLayer hardware) {
        StringBuilder sb = new StringBuilder();
        sb.append("🖥️ 系统信息\n");
        
        // Try to detect if running in Docker
        boolean isDocker = isRunningInDocker();
        if (isDocker) {
            sb.append("  环境: Docker 容器\n");
            
            // Try to read host OS info from mounted file
            String hostOs = getHostOsInfo();
            if (hostOs != null) {
                sb.append("  宿主机: ").append(hostOs).append("\n");
            }
        }
        
        sb.append("  容器OS: ").append(os.getFamily()).append(" ").append(os.getVersionInfo().getVersion()).append("\n");
        sb.append("  架构: ").append(System.getProperty("os.arch")).append("\n");
        sb.append("  处理器: ").append(hardware.getProcessor().getProcessorIdentifier().getName()).append("\n");
        return sb.toString();
    }
    
    /**
     * Detect if running in Docker container
     * 
     * @return true if running in Docker
     */
    private boolean isRunningInDocker() {
        try {
            // Check for .dockerenv file
            File dockerEnv = new File("/.dockerenv");
            if (dockerEnv.exists()) {
                return true;
            }
            
            // Check cgroup for docker
            File cgroupFile = new File("/proc/self/cgroup");
            if (cgroupFile.exists()) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(cgroupFile), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("docker") || line.contains("containerd")) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("检测Docker环境失败", e);
        }
        return false;
    }
    
    /**
     * Try to get host OS information
     * 
     * @return host OS info or null
     */
    private String getHostOsInfo() {
        try {
            // Try to read from environment variable (if set when starting container)
            String hostOs = System.getenv("HOST_OS");
            if (hostOs != null && !hostOs.isEmpty()) {
                return hostOs;
            }
            
            // Try to read from mounted host file (e.g., -v /etc/os-release:/host/os-release:ro)
            File hostOsFile = new File("/host/os-release");
            if (hostOsFile.exists()) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(hostOsFile), StandardCharsets.UTF_8))) {
                    String line;
                    String prettyName = null;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("PRETTY_NAME=")) {
                            prettyName = line.substring("PRETTY_NAME=".length()).replaceAll("\"", "");
                            break;
                        }
                    }
                    if (prettyName != null) {
                        return prettyName;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("读取宿主机OS信息失败", e);
        }
        return null;
    }
    
    /**
     * Get CPU usage information
     */
    private String getCpuInfo(CentralProcessor processor) {
        StringBuilder sb = new StringBuilder();
        sb.append("💻 服务器 CPU 使用率\n");
        
        // Get CPU usage
        long[] prevTicks = processor.getSystemCpuLoadTicks();
        try {
            Thread.sleep(1000); // Wait 1 second to calculate usage
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        double cpuUsage = processor.getSystemCpuLoadBetweenTicks(prevTicks) * 100;
        
        sb.append("  核心数: ").append(processor.getLogicalProcessorCount()).append("\n");
        sb.append("  使用率: ").append(String.format("%.2f", cpuUsage)).append("%\n");
        sb.append("  空闲率: ").append(String.format("%.2f", 100 - cpuUsage)).append("%\n");
        
        // Visual progress bar
        sb.append("  ").append(generateProgressBar(cpuUsage, 100));
        
        return sb.toString();
    }
    
    /**
     * Get memory usage information
     */
    private String getMemoryInfo(GlobalMemory memory) {
        StringBuilder sb = new StringBuilder();
        sb.append("💾 服务器内存使用\n");
        
        long totalMemory = memory.getTotal();
        long availableMemory = memory.getAvailable();
        long usedMemory = totalMemory - availableMemory;
        double usedPercentage = ((double) usedMemory / totalMemory) * 100;
        
        sb.append("  总容量: ").append(formatBytes(totalMemory)).append("\n");
        sb.append("  已使用: ").append(formatBytes(usedMemory)).append("\n");
        sb.append("  可用: ").append(formatBytes(availableMemory)).append("\n");
        sb.append("  使用率: ").append(String.format("%.2f", usedPercentage)).append("%\n");
        sb.append("  ").append(generateProgressBar(usedPercentage, 100));
        
        return sb.toString();
    }
    
    /**
     * Get disk usage information
     */
    private String getDiskInfo(FileSystem fileSystem) {
        StringBuilder sb = new StringBuilder();
        sb.append("💿 磁盘使用\n");
        
        // Only monitor root directory
        File rootDir = new File("/");
        
        if (!rootDir.exists()) {
            sb.append("  ⚠️ 根目录不存在\n");
            return sb.toString();
        }
        
        // Get disk usage for root path
        long total = rootDir.getTotalSpace();
        long usable = rootDir.getUsableSpace();
        long used = total - usable;
        
        if (total == 0) {
            sb.append("  ⚠️ 无法获取磁盘信息\n");
            return sb.toString();
        }
        
        double usedPercentage = ((double) used / total) * 100;
        
        sb.append("  总容量: ").append(formatBytes(total)).append("\n");
        sb.append("  已使用: ").append(formatBytes(used)).append("\n");
        sb.append("  可用: ").append(formatBytes(usable)).append("\n");
        sb.append("  使用率: ").append(String.format("%.2f", usedPercentage)).append("%\n");
        sb.append("  ").append(generateProgressBar(usedPercentage, 100));
        
        return sb.toString();
    }
    
    /**
     * Get network interface information
     */
    private String getNetworkInfo(List<NetworkIF> networkIFs) {
        StringBuilder sb = new StringBuilder();
        sb.append("🌐 网络信息\n");
        
        // Get public IP address
        String publicIp = getPublicIpAddress();
        if (publicIp != null) {
            sb.append("  公网IP: ").append(publicIp).append("\n");
        }
        
        // Find primary network interface
        NetworkIF primaryIF = networkIFs.stream()
                .filter(NetworkIF::isConnectorPresent)
                .filter(iface -> !Arrays.asList(iface.getIPv4addr()).isEmpty() || !Arrays.asList(iface.getIPv6addr()).isEmpty())
                .findFirst()
                .orElse(null);
        
        if (primaryIF != null) {
            primaryIF.updateAttributes();
            
            sb.append("  接口名称: ").append(primaryIF.getDisplayName()).append("\n");
            
            String[] ipv4 = primaryIF.getIPv4addr();
            if (ipv4.length > 0) {
                sb.append("  内网IP: ").append(String.join(", ", ipv4)).append("\n");
            }
            
            sb.append("  接收: ").append(formatBytes(primaryIF.getBytesRecv())).append("\n");
            sb.append("  发送: ").append(formatBytes(primaryIF.getBytesSent())).append("\n");
            sb.append("  收包: ").append(primaryIF.getPacketsRecv()).append("\n");
            sb.append("  发包: ").append(primaryIF.getPacketsSent()).append("\n");
            
            if (primaryIF.getInErrors() > 0 || primaryIF.getOutErrors() > 0) {
                sb.append("  错误: ").append("入=").append(primaryIF.getInErrors())
                  .append(" 出=").append(primaryIF.getOutErrors()).append("\n");
            }
        } else {
            sb.append("  ⚠️ 未检测到活动网络接口\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Get public IP address from external service
     * 
     * @return public IP address or null if failed
     */
    private String getPublicIpAddress() {
        try {
            // Try multiple services in case one is down
            String[] services = {
                "https://api.ipify.org",
                "https://ifconfig.me/ip",
                "https://icanhazip.com"
            };
            
            for (String service : services) {
                try {
                    java.net.URL url = new java.net.URL(service);
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(3000);
                    conn.setReadTimeout(3000);
                    
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        String ip = reader.readLine();
                        if (ip != null && !ip.isEmpty()) {
                            return ip.trim();
                        }
                    }
                } catch (Exception e) {
                    // Try next service
                    continue;
                }
            }
        } catch (Exception e) {
            log.warn("获取公网IP失败", e);
        }
        return null;
    }
    
    /**
     * Get system uptime information
     */
    private String getUptimeInfo(OperatingSystem os) {
        StringBuilder sb = new StringBuilder();
        sb.append("⏱️ 系统运行时间\n");
        
        long uptimeSeconds = os.getSystemUptime();
        long days = uptimeSeconds / 86400;
        long hours = (uptimeSeconds % 86400) / 3600;
        long minutes = (uptimeSeconds % 3600) / 60;
        long seconds = uptimeSeconds % 60;
        
        sb.append("  ");
        if (days > 0) {
            sb.append(days).append(" 天 ");
        }
        sb.append(hours).append(" 小时 ");
        sb.append(minutes).append(" 分钟 ");
        sb.append(seconds).append(" 秒\n");
        
        return sb.toString();
    }
    
    /**
     * Format bytes to human-readable format
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        return String.format("%.2f %s", bytes / Math.pow(1024, exp), units[exp]);
    }
    
    /**
     * Format bits per second to human-readable format
     */
    private String formatBits(long bps) {
        if (bps < 1000) {
            return bps + " bps";
        }
        int exp = (int) (Math.log(bps) / Math.log(1000));
        String[] units = {"bps", "Kbps", "Mbps", "Gbps", "Tbps"};
        return String.format("%.2f %s", bps / Math.pow(1000, exp), units[exp]);
    }
    
    /**
     * Generate progress bar
     * 
     * @param value current value
     * @param max maximum value
     * @return progress bar string
     */
    private String generateProgressBar(double value, double max) {
        int totalBars = 10;
        int filledBars = (int) Math.round((value / max) * totalBars);
        
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < totalBars; i++) {
            if (i < filledBars) {
                bar.append("█");
            } else {
                bar.append("░");
            }
        }
        bar.append("]\n");
        
        return bar.toString();
    }
    
    @Override
    public String getCallbackPattern() {
        return "system_metrics";
    }
}
