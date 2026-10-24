package com.sri.miniqueue.security;

import com.sri.miniqueue.utils.Names;

import java.util.Set;
import java.util.UUID;

/** The authenticated caller of the current request: who they are and what they may do. */
public record ClientPrincipal(UUID id, String name, Set<String> scopes) {

    /**
     * The consumer id stored on messages this caller claims. It always starts with the
     * client name taken from the key, so one client can never act as another.
     *
     * The optional instance tells replicas of the same service apart ("email-service@pod-2"),
     * which keeps fencing working: if pod-1 stalls and the message is redelivered to pod-2,
     * a late ack from pod-1 is still rejected. Names cannot contain '@', so the split is unambiguous.
     */
    public String consumerId(String instance) {
        if (instance == null || instance.isBlank()) {
            return name;
        }
        return name + "@" + Names.normalize(instance, "Instance");
    }
}