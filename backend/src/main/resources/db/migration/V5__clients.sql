-- A registered application: producer, consumer or both. The API key itself is never
-- stored, only its SHA-256 hash, so a database leak does not leak working keys.
CREATE TABLE client (
    id           uuid          PRIMARY KEY,
    name         varchar(100)  NOT NULL,
    key_prefix   varchar(16)   NOT NULL,
    key_hash     varchar(64)   NOT NULL,
    scopes       varchar(2000) NOT NULL,
    created_at   timestamp(6)  NOT NULL DEFAULT now(),
    last_used_at timestamp(6),
    revoked_at   timestamp(6),
    CONSTRAINT uk_client_key_hash UNIQUE (key_hash)
);

-- Names are unique among ACTIVE clients only, so a revoked client's name can be reused.
-- A partial index does that; a plain UNIQUE constraint would block the name forever.
CREATE UNIQUE INDEX uk_client_active_name ON client (name) WHERE revoked_at IS NULL;