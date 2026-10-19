package com.sri.miniqueue.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class WebSocketSessionRegistry {

    private final ConcurrentHashMap<String, List<WebSocketSession>> registry = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> robin = new ConcurrentHashMap<>();

    void registerSession(String queueName, WebSocketSession session) {
        // The add happens INSIDE compute, which runs atomically for this key. Adding outside
        // it (computeIfAbsent(...).add(...)) races with removeSession: the list can be
        // dropped from the map between the two calls, and the new session would be added to
        // an orphaned list that nothing ever reads.
        registry.compute(queueName, (k, sessions) -> {
            List<WebSocketSession> list = (sessions != null) ? sessions : new CopyOnWriteArrayList<>();
            list.add(ThreadSafeSessions.wrap(session));
            return list;
        });
    }

    void removeSession(String queueName, WebSocketSession session) {
        // Match by id: the list holds wrapped sessions, which are different objects
        // from the raw session Spring passes in here.
        registry.computeIfPresent(queueName, (k, sessions) -> {
            sessions.removeIf(s -> s.getId().equals(session.getId()));
            return sessions.isEmpty() ? null : sessions;
        });
    }

    Optional<WebSocketSession> getNextSession(String queueName) {
        List<WebSocketSession> sessions = registry.get(queueName);
        if (sessions == null) {
            return Optional.empty();
        }
        // Take a snapshot so the size and the element come from the same version of the
        // list. Reading size() and then get() on the live list can throw
        // IndexOutOfBoundsException if a consumer disconnects in between.
        WebSocketSession[] snapshot = sessions.toArray(new WebSocketSession[0]);
        if (snapshot.length == 0) {
            return Optional.empty();
        }
        AtomicInteger counter = robin.computeIfAbsent(queueName, k -> new AtomicInteger());
        // getAndIncrement is ONE atomic step, so two threads can never pick the same slot.
        // floorMod keeps the index valid even after the counter overflows and goes negative.
        int index = Math.floorMod(counter.getAndIncrement(), snapshot.length);
        return Optional.of(snapshot[index]);
    }
}