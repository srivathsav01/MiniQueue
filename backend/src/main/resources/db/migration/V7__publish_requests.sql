-- One row per idempotent publish: "this publisher already sent this key, and it produced
-- these messages". The primary key is what makes a retry impossible to store twice.
CREATE TABLE publish_request (
    client_id       uuid         NOT NULL,   -- who published (API key client, or dashboard session)
    idempotency_key varchar(200) NOT NULL,
    request_hash    varchar(64)  NOT NULL,   -- SHA-256 of topic + payload, to catch a reused key
    message_ids     text         NOT NULL,   -- comma-separated, one per bound queue
    created_at      timestamp(6) NOT NULL DEFAULT now(),
    PRIMARY KEY (client_id, idempotency_key)
);

-- For the hourly cleanup of keys past their retention.
CREATE INDEX idx_publish_request_created_at ON publish_request (created_at);