package com.sri.miniqueue.security;

import com.sri.miniqueue.utils.ApiKeys;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived codes that work exactly once. Used where a credential has to travel in a URL
 * (a WebSocket handshake, the redirect back from sign-in): by the time a URL shows up in a
 * log, the code in it has been used or has expired.
 *
 * Held in memory, so this works for one broker instance; several instances would need the
 * codes in Postgres or Redis.
 */
public class OneTimeCodes<T> {

    private record Entry<T>(T value, Instant expiresAt) {
    }

    private final String prefix;
    private final Duration ttl;
    private final Map<String, Entry<T>> codes = new ConcurrentHashMap<>();

    public OneTimeCodes(String prefix, Duration ttl) {
        this.prefix = prefix;
        this.ttl = ttl;
    }

    public String issue(T value) {
        Instant now = Instant.now();
        codes.values().removeIf(entry -> entry.expiresAt().isBefore(now)); // tidy up as we go; the map stays tiny
        String code = ApiKeys.randomToken(prefix);
        codes.put(code, new Entry<>(value, now.plus(ttl)));
        return code;
    }

    /** remove() reads and deletes in one atomic step, so two requests can never share one code. */
    public Optional<T> redeem(String code) {
        if (code == null) {
            return Optional.empty();
        }
        Entry<T> entry = codes.remove(code);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    public Duration ttl() {
        return ttl;
    }
}