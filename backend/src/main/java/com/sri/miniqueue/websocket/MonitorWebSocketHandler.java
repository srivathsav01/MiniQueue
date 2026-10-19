package com.sri.miniqueue.websocket;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class MonitorWebSocketHandler extends TextWebSocketHandler {

    // Keyed by session id. We store the thread-safe wrapper, not the raw session, and the
    // wrapper is a different object, so removing by object would never match. Removing by
    // id always does.
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        log.info("New connection open : {}", session.getId());
        sessions.put(session.getId(), ThreadSafeSessions.wrap(session));
    }

    @Override
    public void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) {
        // Monitor clients don't send messages
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        log.info("Connection closes : {}", session.getId());
        sessions.remove(session.getId());
    }

    /** A snapshot, so callers can iterate safely while dashboards connect and disconnect. */
    public List<WebSocketSession> getSessions() {
        return List.copyOf(sessions.values());
    }
}