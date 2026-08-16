package com.sri.miniqueue.websocket;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
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

    @Override
    public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry) {
        registry.addHandler(miniQueueWebSocketHandler,"/ws/consume")
                .setAllowedOrigins("*");

        registry.addHandler(monitorWebSocketHandler,"/ws/monitor")
                .setAllowedOrigins("*");
    }
}
