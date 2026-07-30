package com.sri.miniqueue.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sri.miniqueue.event.NewMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final WebSocketSessionRegistry webSocketSessionRegistry;

    private final ObjectMapper objectMapper;

    @EventListener
    private void listenNewMessage(NewMessageEvent event) {
        Optional< WebSocketSession> session = webSocketSessionRegistry.getNextSession(event.getQueueName());
        if(session.isPresent()){
            try{
                String payload = event.getMessage().getPayload();
                String preview = payload.length()>25? payload.substring(0,25)+"..." : payload;
                Map<String,String> notification = Map.of(
                        "queue", event.getQueueName(),
                        "event", preview
                );
                String json = objectMapper.writeValueAsString(notification);
                session.get().sendMessage(new TextMessage(json));
            } catch (Exception e) {
                log.error("Failed to send message to consumer on queue: {}", event.getQueueName(), e);
            }
        }
        else{
            log.info("No active WebSocket consumer for queue: {}. Message stays PENDING for polling.", event.getQueueName());
        }
    }

}
