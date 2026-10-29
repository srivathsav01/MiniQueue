package com.sri.miniqueue.scheduler;

import com.sri.miniqueue.repository.PublishRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Forgets idempotency keys after their retention, so the table does not grow forever. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PublishRequestCleanup {

    private final PublishRequestRepository publishRequests;

    @Value("${miniqueue.idempotency.retention:24h}")
    private Duration retention;

    @Scheduled(fixedDelay = 3_600_000) // hourly
    public void deleteExpiredKeys() {
        int deleted = publishRequests.deleteOlderThan(retention);
        if (deleted > 0) {
            log.info("Forgot {} idempotency keys older than {}", deleted, retention);
        }
    }
}