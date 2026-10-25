package com.sri.miniqueue.utils;

import com.sri.miniqueue.exception.CustomException;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * What a client may do:
 *   admin              everything, including managing clients
 *   publish:<topic>    publish to one topic      publish:*   to any topic
 *   consume:<queue>    consume from one queue    consume:*   from any queue
 *   view               read-only dashboard access
 *   metrics            scrape /actuator/prometheus
 */
public final class Scopes {

    public static final String ADMIN = "admin";
    public static final String PUBLISH = "publish";
    public static final String CONSUME = "consume";
    /** Read-only dashboard access: overview, queues, messages, DLQ list, live feed. */
    public static final String VIEW = "view";
    /** Scraping /actuator/prometheus. */
    public static final String METRICS = "metrics";

    // Same name rules as Names, so a scope can always name a real topic or queue.
    private static final Pattern VALID =
            Pattern.compile("admin|view|metrics|(publish|consume):(\\*|[a-z0-9][a-z0-9._-]{0,99})");

    private Scopes() {
    }

    public static Set<String> normalize(Collection<String> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new CustomException("At least one scope is required");
        }
        Set<String> scopes = new TreeSet<>(); // sorted and de-duplicated, so storage is stable
        for (String scope : raw) {
            String s = scope == null ? "" : scope.trim().toLowerCase(Locale.ROOT);
            if (!VALID.matcher(s).matches()) {
                throw new CustomException("Invalid scope '" + s + "': use admin, publish:<topic>, consume:<queue>, or * in place of a name");
            }
            scopes.add(s);
        }
        return scopes;
    }

    public static boolean allows(Collection<String> granted, String action, String name) {
        return granted.contains(ADMIN)
                || granted.contains(action + ":*")
                || granted.contains(action + ":" + name);
    }
}