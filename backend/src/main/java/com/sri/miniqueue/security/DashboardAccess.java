package com.sri.miniqueue.security;

import com.sri.miniqueue.utils.Scopes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Turns a GitHub login into dashboard scopes. */
@Slf4j
@Component
public class DashboardAccess {

    private final Set<String> admins;
    private final boolean publicViewer;

    public DashboardAccess(@Value("${miniqueue.security.admins:}") String admins,
                           @Value("${miniqueue.security.public-viewer:true}") boolean publicViewer) {
        this.admins = Arrays.stream(admins.split(","))
                .map(login -> login.trim().toLowerCase(Locale.ROOT))
                .filter(login -> !login.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.publicViewer = publicViewer;
        if (this.admins.isEmpty()) {
            log.warn("MINIQUEUE_ADMINS is empty: nobody can manage topics, queues or clients");
        }
    }

    /** GitHub logins are case-insensitive, so compare lowercased. Empty: not allowed in at all. */
    public Optional<Set<String>> scopesFor(String githubLogin) {
        String login = githubLogin.toLowerCase(Locale.ROOT);
        if (admins.contains(login)) {
            return Optional.of(Set.of(Scopes.ADMIN));
        }
        return publicViewer ? Optional.of(Set.of(Scopes.VIEW)) : Optional.empty();
    }
}