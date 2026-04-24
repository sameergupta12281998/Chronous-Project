CREATE TABLE IF NOT EXISTS jobs (
    id                    UUID PRIMARY KEY,
    owner_id              UUID NOT NULL,
    name                  VARCHAR(200) NOT NULL,
    description           VARCHAR(1000),
    task_type             VARCHAR(100) NOT NULL,
    payload               TEXT,
    schedule_type         VARCHAR(20)  NOT NULL,
    recurrence_frequency  VARCHAR(20),
    scheduled_at          TIMESTAMP    NOT NULL,
    status                VARCHAR(20)  NOT NULL,
    max_attempts          INTEGER      NOT NULL,
    created_at            TIMESTAMP    NOT NULL,
    updated_at            TIMESTAMP    NOT NULL,
    version               BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_jobs_owner ON jobs(owner_id);
CREATE INDEX IF NOT EXISTS idx_jobs_status ON jobs(status);
CREATE INDEX IF NOT EXISTS idx_jobs_owner_status ON jobs(owner_id, status);

CREATE TABLE IF NOT EXISTS job_outbox (
    id              UUID PRIMARY KEY,
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    topic           VARCHAR(100) NOT NULL,
    payload         TEXT NOT NULL,
    correlation_id  VARCHAR(100),
    created_at      TIMESTAMP NOT NULL,
    sent_at         TIMESTAMP,
    attempt_count   INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_outbox_unsent ON job_outbox(sent_at) WHERE sent_at IS NULL;

CREATE TABLE IF NOT EXISTS processed_events (
    event_id        VARCHAR(100) NOT NULL,
    consumer_group  VARCHAR(100) NOT NULL,
    processed_at    TIMESTAMP    NOT NULL,
    PRIMARY KEY (event_id, consumer_group)
);
