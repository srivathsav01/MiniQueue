package com.sri.miniqueue.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RequiredArgsConstructor
@Component
@Slf4j
public class MiniQueueWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionRegistry webSocketSessionRegistry;

    private String extractQueueName(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) return null;
        List<String> queueNames = UriComponentsBuilder.fromUri(uri)
                .build()
                .getQueryParams()
                .get("queue");
        return (queueNames != null && !queueNames.isEmpty()) ? queueNames.getFirst() : null;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session){
        String queueName = extractQueueName(session);
        if(queueName!=null){
            log.info("Consumer connected to queue: {}",queueName);
            webSocketSessionRegistry.registerSession(queueName,session);
        }
    }

    @Override
    public void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message){
        // to be implemented
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status){
        String queueName = extractQueueName(session);
        if(queueName!=null){
            log.info("Consumer disconnected from queue: {}",queueName);
            webSocketSessionRegistry.removeSession(queueName,session);
        }
    }
}
