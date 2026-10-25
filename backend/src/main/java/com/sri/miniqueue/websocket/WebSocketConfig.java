package com.sri.miniqueue.websocket;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final MiniQueueWebSocketHandler miniQueueWebSocketHandler;

    private final MonitorWebSocketHandler monitorWebSocketHandler;

    // The same origins as the REST API, so another website cannot open a socket from a signed-in browser.
    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry) {
        registry.addHandler(miniQueueWebSocketHandler,"/ws/consume")
                .setAllowedOrigins(allowedOrigins);

        registry.addHandler(monitorWebSocketHandler,"/ws/monitor")
                .setAllowedOrigins(allowedOrigins);
    }
}
