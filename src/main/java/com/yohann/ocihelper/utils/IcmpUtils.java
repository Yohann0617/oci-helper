package com.yohann.ocihelper.utils;

import cn.hutool.core.util.RuntimeUtil;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @ClassName IcmpUtils
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-04-30 13:58
 **/
public class IcmpUtils {

    /**
     * 跨平台 Ping 方法
     *
     * @param hostOrIp 域名、IPv4 或 IPv6
     * @param count    发送次数
     * @param timeout  单位：毫秒
     * @return Ping 命令输出结果
     */
    public static String ping(String hostOrIp, int count, int timeout) {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        boolean isIpv6 = isIpv6Address(hostOrIp);

        String cmd;
        if (isWindows) {
            // Windows 使用 -n 指定次数，-w 指定超时（每次）
            if (isIpv6) {
                cmd = String.format("ping -n %d -w %d %s", count, timeout, hostOrIp);
            } else {
                cmd = String.format("ping -n %d -w %d %s", count, timeout, hostOrIp);
            }
        } else {
            // Linux/macOS 使用 -c 指定次数，-W/-w 指定超时（单位秒）
            int timeoutSec = Math.max(1, timeout / 1000);  // 转换为秒，最小1秒
            if (isIpv6) {
                cmd = String.format("ping6 -c %d -W %d %s", count, timeoutSec, hostOrIp);
            } else {
                cmd = String.format("ping -c %d -W %d %s", count, timeoutSec, hostOrIp);
            }
        }

        try {
            return RuntimeUtil.execForStr(cmd);
        } catch (Exception e) {
            return "Ping 执行失败：" + e.getMessage();
        }
    }

    /**
     * 判断是否为 IPv6 地址
     */
    private static boolean isIpv6Address(String address) {
        try {
            InetAddress inetAddress = InetAddress.getByName(address);
            return inetAddress instanceof java.net.Inet6Address;
        } catch (UnknownHostException e) {
            return address.contains(":"); // fallback 判断
        }
    }

    /**
     * 通用判断 ping 是否 100% 丢包
     *
     * 兼容中英文、Windows/Linux/Mac 的 ping 输出。
     *
     * @param pingOutput ping 命令的完整输出
     * @return true 表示完全丢包
     */
    public static boolean isPacketLoss100(String pingOutput) {
        if (pingOutput == null || pingOutput.isEmpty()) {
            return true;
        }

        // 使用通用正则匹配丢包统计行：如 Sent = 4, Received = 0 或 Packets: Sent = 4, Received = 0
        Pattern pattern = Pattern.compile(
                "(?i)(Sent|发送|Transmitted)\\s*=*\\s*(\\d+)[^\\d]+(Received|接收|received)\\s*=*\\s*(\\d+)",
                Pattern.MULTILINE);

        Matcher matcher = pattern.matcher(pingOutput);
        while (matcher.find()) {
            try {
                int sent = Integer.parseInt(matcher.group(2));
                int received = Integer.parseInt(matcher.group(4));
                return sent > 0 && received == 0;
            } catch (NumberFormatException e) {
                return false;  // 避免意外匹配失败
            }
        }

        // 如果没有匹配上，也可以尝试最后一行 packet loss 的百分比作为兜底
        Pattern fallbackLoss = Pattern.compile("([1-9][0-9]*)%\\s*packet loss");
        Matcher m2 = fallbackLoss.matcher(pingOutput);
        if (m2.find()) {
            return "100".equals(m2.group(1));
        }

        return false;
    }

    // 示例调用
    public static void main(String[] args) {
        String result = ping("yohann.nyc.mn", 4, 3000);
        System.out.println(result);

        if (isPacketLoss100(result)) {
            System.out.println("⚠️ 网络不可达，100% 丢包");
        } else {
            System.out.println("✅ 网络可达，部分或无丢包");
        }
    }
}
