package com.sri.miniqueue.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sri.miniqueue.event.BrokerActivityEvent;
import com.sri.miniqueue.event.NewMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class MonitorEventListener {

    private final MonitorWebSocketHandler monitorWebSocketHandler;

    private final ObjectMapper objectMapper;

    @EventListener
    private void onBrokerActivity(BrokerActivityEvent event) {
        List<WebSocketSession> sessions = monitorWebSocketHandler.getSessions();
        if(!sessions.isEmpty()){
            try {
                String message = objectMapper.writeValueAsString(event);
                sessions.forEach(session -> {
                    try {
                        session.sendMessage(new TextMessage(message));
                    } catch (IOException e) {
                        log.error("Failed to send message to session : {}", session.getId());
                    }
                });
            } catch (JsonProcessingException e) {
                log.error("Failed to serialise the event : {}",event);
            }
        }
        else{
            log.info("No active WebSocket consumer for monitoring");
        }
    }
}
