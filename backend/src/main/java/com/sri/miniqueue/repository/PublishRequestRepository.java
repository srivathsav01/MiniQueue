package com.sri.miniqueue.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Plain SQL instead of a JPA entity: the whole point is one exact INSERT ... ON CONFLICT
 * statement, which JPA has no clean way to express. JdbcTemplate joins the surrounding
 * @Transactional, so the claim commits or rolls back together with the messages.
 */
@Repository
@RequiredArgsConstructor
public class PublishRequestRepository {

    public record StoredRequest(String requestHash, List<UUID> messageIds) {
    }

    private final JdbcTemplate jdbc;

    /**
     * Claims the key for this publisher. True: first time, go ahead and publish. False: the
     * key was already used. If another transaction is inserting the same key right now,
     * Postgres makes this statement wait until that transaction ends, so the answer is
     * always based on committed data.
     */
    public boolean claim(UUID clientId, String idempotencyKey, String requestHash) {
        int inserted = jdbc.update(
                "INSERT INTO publish_request (client_id, idempotency_key, request_hash, message_ids) "
                        + "VALUES (?, ?, ?, '') ON CONFLICT DO NOTHING",
                clientId, idempotencyKey, requestHash);
        return inserted == 1;
    }

    public void recordMessages(UUID clientId, String idempotencyKey, List<UUID> messageIds) {
        String ids = messageIds.stream().map(UUID::toString).collect(Collectors.joining(","));
        jdbc.update("UPDATE publish_request SET message_ids = ? WHERE client_id = ? AND idempotency_key = ?",
                ids, clientId, idempotencyKey);
    }

    public Optional<StoredRequest> find(UUID clientId, String idempotencyKey) {
        List<StoredRequest> rows = jdbc.query(
                "SELECT request_hash, message_ids FROM publish_request WHERE client_id = ? AND idempotency_key = ?",
                (rs, rowNum) -> new StoredRequest(rs.getString("request_hash"), parseIds(rs.getString("message_ids"))),
                clientId, idempotencyKey);
        return rows.stream().findFirst();
    }

    /** Uses the database's clock on both sides of the comparison, so app and DB time zones cannot disagree. */
    public int deleteOlderThan(Duration retention) {
        return jdbc.update("DELETE FROM publish_request WHERE created_at < now() - (? * interval '1 second')",
                retention.toSeconds());
    }

    private static List<UUID> parseIds(String ids) {
        if (ids == null || ids.isBlank()) {
            return List.of();
        }
        return Arrays.stream(ids.split(",")).map(UUID::fromString).toList();
    }
}