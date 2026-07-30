package com.sri.miniqueue.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class WebSocketSessionRegistry {

    private final ConcurrentHashMap<String, List<WebSocketSession>> registry = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> robin = new ConcurrentHashMap<>();

    void registerSession(String queueName, WebSocketSession session){
        registry.computeIfAbsent(queueName,k -> new CopyOnWriteArrayList<>())
                .add(session);
    }

    void removeSession(String queueName, WebSocketSession session){
        registry.computeIfPresent(queueName,(k,v) -> {
            v.remove(session);
            return v.isEmpty() ? null : v;
        });
    }

    Optional<WebSocketSession> getNextSession(String queueName){
        List<WebSocketSession> sessions = registry.get(queueName);
        if(sessions == null || sessions.isEmpty()) return Optional.empty();
        AtomicInteger counter = robin.computeIfAbsent(queueName, k -> new AtomicInteger(0));
        WebSocketSession session = sessions.get(counter.get() % sessions.size());
        counter.incrementAndGet();
        return Optional.ofNullable(session);
    }
}
