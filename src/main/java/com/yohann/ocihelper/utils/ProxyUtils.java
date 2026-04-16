package com.yohann.ocihelper.utils;

import cn.hutool.core.util.StrUtil;
import com.oracle.bmc.http.client.ProxyConfiguration;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;

/**
 * 代理工具类，负责将代理 URL 字符串解析为 OCI SDK 所需的 ProxyConfiguration
 *
 * <p>支持的格式：
 * <ul>
 *   <li>http://host:port</li>
 *   <li>http://user:password@host:port</li>
 *   <li>socks5://host:port</li>
 *   <li>socks5://user:password@host:port</li>
 *   <li>socks4://host:port</li>
 * </ul>
 * </p>
 *
 * @author yohann
 */
@Slf4j
public class ProxyUtils {

    private ProxyUtils() {
    }

    /**
     * 将代理 URL 字符串解析为 {@link ProxyConfiguration}。
     *
     * @param proxyUrl 代理地址字符串，为空时返回 null
     * @return ProxyConfiguration，解析失败或为空时返回 null
     */
    public static ProxyConfiguration parseProxy(String proxyUrl) {
        if (StrUtil.isBlank(proxyUrl)) {
            return null;
        }
        try {
            URI uri = new URI(proxyUrl.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            String host = uri.getHost();
            int port = uri.getPort();
            if (StrUtil.isBlank(host) || port <= 0) {
                log.warn("代理地址格式不正确，已忽略：{}", proxyUrl);
                return null;
            }

            Proxy.Type type;
            if ("http".equals(scheme) || "https".equals(scheme)) {
                type = Proxy.Type.HTTP;
            } else if (scheme.startsWith("socks")) {
                type = Proxy.Type.SOCKS;
            } else {
                log.warn("不支持的代理协议 [{}]，已忽略：{}", scheme, proxyUrl);
                return null;
            }

            Proxy proxy = new Proxy(type, new InetSocketAddress(host, port));
            ProxyConfiguration.Builder builder = ProxyConfiguration.builder().proxy(proxy);

            // 解析认证信息（格式：user:password@host）
            String userInfo = uri.getUserInfo();
            if (StrUtil.isNotBlank(userInfo)) {
                int idx = userInfo.indexOf(':');
                if (idx >= 0) {
                    builder.username(userInfo.substring(0, idx));
                    builder.password(userInfo.substring(idx + 1).toCharArray());
                } else {
                    builder.username(userInfo);
                }
            }

            log.debug("已解析代理配置：协议={}, 主机={}, 端口={}", scheme, host, port);
            return builder.build();
        } catch (Exception e) {
            log.warn("解析代理地址失败，已忽略 [{}]：{}", proxyUrl, e.getMessage());
            return null;
        }
    }
}
