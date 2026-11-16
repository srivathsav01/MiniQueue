-- Per-queue lease length in seconds. NULL means the broker default.
ALTER TABLE queue ADD COLUMN lease_seconds INTEGER;
ALTER TABLE queue ADD CONSTRAINT queue_lease_seconds_check
    CHECK (lease_seconds IS NULL OR lease_seconds BETWEEN 5 AND 43200);

-- When the current consumer's claim on a message runs out.
ALTER TABLE message ADD COLUMN lease_expires_at TIMESTAMP(6);

-- Messages already UNACKED keep the deadline they had under the old 10-minute rule.
UPDATE message
SET lease_expires_at = unacked_at + INTERVAL '10 minutes'
WHERE status = 'UNACKED';

-- The scheduler now looks up expired leases instead of old unacked_at values.
DROP INDEX IF EXISTS idx_message_status_unacked;
CREATE INDEX idx_message_status_lease ON message (status, lease_expires_at);