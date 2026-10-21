-- Names are the lookup key for every API call, so each one must exist and be unique.
-- The service lowercases names before saving, so plain UNIQUE also stops case duplicates.
ALTER TABLE topic ALTER COLUMN name SET NOT NULL;
ALTER TABLE queue ALTER COLUMN name SET NOT NULL;
ALTER TABLE topic ADD CONSTRAINT uk_topic_name UNIQUE (name);
ALTER TABLE queue ADD CONSTRAINT uk_queue_name UNIQUE (name);