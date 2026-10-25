-- A signed-in dashboard user. As with API keys, the token itself is never stored, only its
-- SHA-256 hash. Sessions expire; logout deletes the row.
CREATE TABLE dashboard_session (
    id         uuid         PRIMARY KEY,
    username   varchar(100) NOT NULL,
    scopes     varchar(100) NOT NULL,
    token_hash varchar(64)  NOT NULL,
    created_at timestamp(6) NOT NULL DEFAULT now(),
    expires_at timestamp(6) NOT NULL,
    CONSTRAINT uk_dashboard_session_token_hash UNIQUE (token_hash)
);

-- For the hourly cleanup of expired sessions.
CREATE INDEX idx_dashboard_session_expires_at ON dashboard_session (expires_at);