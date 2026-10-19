package com.sri.miniqueue.websocket;

import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/**
 * A raw WebSocketSession is NOT safe to send on from several threads at once: two
 * concurrent sendMessage calls on the same session fail with
 * IllegalStateException (TEXT_PARTIAL_WRITING).
 *
 * Broker events are published from whichever request thread did the work, and with
 * SKIP LOCKED many consumes really do run in parallel, so every session we store is
 * wrapped in Spring's ConcurrentWebSocketSessionDecorator. It lets one thread send at a
 * time; other threads add their message to a buffer and return immediately, so a slow
 * client never blocks a broker request thread.
 */
final class ThreadSafeSessions {

    /** If a single send takes longer than this, the client is too slow and is closed. */
    private static final int SEND_TIME_LIMIT_MS = 10_000;

    /** If this many bytes pile up unsent for one client, it is closed instead of growing memory forever. */
    private static final int BUFFER_SIZE_LIMIT_BYTES = 512 * 1024;

    private ThreadSafeSessions() {
    }

    static WebSocketSession wrap(WebSocketSession session) {
        return new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT_BYTES);
    }
}