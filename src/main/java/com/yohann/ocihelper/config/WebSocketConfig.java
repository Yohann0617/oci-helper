package com.yohann.ocihelper.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.config
 * @className: WebSocketConfig
 * @author: Yohann
 * @date: 2024/11/17 18:35
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new LogWebSocketHandler(), "/logs")
                .setAllowedOrigins("*");
    }
}