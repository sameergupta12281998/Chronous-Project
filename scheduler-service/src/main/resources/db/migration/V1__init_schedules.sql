CREATE TABLE IF NOT EXISTS schedules (
    id                    UUID PRIMARY KEY,
    job_id                UUID NOT NULL UNIQUE,
    owner_id              UUID NOT NULL,
    task_type             VARCHAR(100) NOT NULL,
    payload               TEXT,
    schedule_type         VARCHAR(20) NOT NULL,
    recurrence_frequency  VARCHAR(20),
    next_run_at           TIMESTAMP NOT NULL,
    last_run_at           TIMESTAMP,
    max_attempts          INTEGER NOT NULL,
    active                BOOLEAN NOT NULL,
    created_at            TIMESTAMP NOT NULL,
    updated_at            TIMESTAMP NOT NULL,
    version               BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_schedules_due ON schedules(next_run_at) WHERE active = true;

CREATE TABLE IF NOT EXISTS processed_events (
    event_id        VARCHAR(100) NOT NULL,
    consumer_group  VARCHAR(100) NOT NULL,
    processed_at    TIMESTAMP    NOT NULL,
    PRIMARY KEY (event_id, consumer_group)
);
