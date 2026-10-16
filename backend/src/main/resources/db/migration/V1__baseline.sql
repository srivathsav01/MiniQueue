-- Baseline: the schema exactly as Hibernate's ddl-auto created it.
-- Existing databases (local, Render) skip this file via baseline-on-migrate.
-- Fresh databases (Testcontainers, new environments) run it.

CREATE TABLE topic (
                       id         UUID NOT NULL,
                       created_at TIMESTAMP(6),
                       name       VARCHAR(255),
                       CONSTRAINT topic_pkey PRIMARY KEY (id)
);

CREATE TABLE queue (
                       id       UUID NOT NULL,
                       name     VARCHAR(255),
                       topic_id UUID,
                       CONSTRAINT queue_pkey PRIMARY KEY (id),
                       CONSTRAINT fk_queue_topic FOREIGN KEY (topic_id) REFERENCES topic (id)
);

CREATE TABLE message (
                         id           UUID         NOT NULL,
                         consumer_id  VARCHAR(255),
                         payload      VARCHAR(255) NOT NULL,
                         published_at TIMESTAMP(6),
                         retry_count  INTEGER,
                         status       VARCHAR(255) NOT NULL,
                         unacked_at   TIMESTAMP(6),
                         queue_id     UUID         NOT NULL,
                         CONSTRAINT message_pkey PRIMARY KEY (id),
                         CONSTRAINT message_status_check CHECK (status IN ('PENDING', 'UNACKED', 'ACKED', 'DEAD')),
                         CONSTRAINT fk_message_queue FOREIGN KEY (queue_id) REFERENCES queue (id)
);