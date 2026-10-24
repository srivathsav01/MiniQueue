package com.sri.miniqueue.security;

import com.sri.miniqueue.service.ClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Creates (or updates) the admin client named "bootstrap-admin" from
 * MINIQUEUE_BOOTSTRAP_ADMIN_KEY at startup. Changing the env var and restarting rotates it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BootstrapAdminKey {

    private final ClientService clientService;

    @Value("${miniqueue.security.bootstrap-admin-key:}")
    private String bootstrapKey;

    @EventListener(ApplicationReadyEvent.class)
    public void installBootstrapAdmin() {
        if (bootstrapKey.isBlank()) {
            log.warn("MINIQUEUE_BOOTSTRAP_ADMIN_KEY is not set: no admin key exists, so no clients can be registered");
            return;
        }
        clientService.ensureBootstrapAdmin(bootstrapKey);
    }
}