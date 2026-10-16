-- Consume path: WHERE queue_id = ? AND status = 'PENDING' ORDER BY published_at LIMIT 1
-- Postgres walks this index in order and stops at the first unlocked row,
-- instead of scanning and sorting the whole message table on every consume.
CREATE INDEX idx_message_queue_status_published
    ON message (queue_id, status, published_at);

-- Redelivery scheduler: WHERE status = 'UNACKED' AND unacked_at < ? ORDER BY unacked_at
CREATE INDEX idx_message_status_unacked
    ON message (status, unacked_at);