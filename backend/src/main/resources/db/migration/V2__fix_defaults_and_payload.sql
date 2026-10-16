-- publishedAt and createdAt are mapped with insertable = false, so the application
-- never writes them. Without a database default they were silently NULL, which made
-- ORDER BY published_at (oldest-first delivery) meaningless.
ALTER TABLE message ALTER COLUMN published_at SET DEFAULT now();
ALTER TABLE topic   ALTER COLUMN created_at   SET DEFAULT now();

-- Backfill rows created before the default existed, so ordering has a value to sort on.
UPDATE message SET published_at = now() WHERE published_at IS NULL;
UPDATE topic   SET created_at   = now() WHERE created_at   IS NULL;

-- Payloads were capped at 255 characters (Hibernate's default for String).
-- Any real JSON message longer than that failed to publish.
ALTER TABLE message ALTER COLUMN payload TYPE TEXT;