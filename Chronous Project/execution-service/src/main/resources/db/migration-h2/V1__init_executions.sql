CREATE TABLE IF NOT EXISTS executions (
    id              UUID PRIMARY KEY,
    job_id          UUID NOT NULL,
    owner_id        UUID NOT NULL,
    task_type       VARCHAR(100) NOT NULL,
    payload         TEXT,
    attempt         INTEGER NOT NULL,
    max_attempts    INTEGER NOT NULL,
    status          VARCHAR(30) NOT NULL,
    error           TEXT,
    started_at      TIMESTAMP NOT NULL,
    finished_at     TIMESTAMP,
    next_attempt_at TIMESTAMP,
    correlation_id  VARCHAR(100)
);
CREATE INDEX IF NOT EXISTS idx_executions_job ON executions(job_id);

CREATE TABLE IF NOT EXISTS processed_events (
    event_id        VARCHAR(100) NOT NULL,
    consumer_group  VARCHAR(100) NOT NULL,
    processed_at    TIMESTAMP    NOT NULL,
    PRIMARY KEY (event_id, consumer_group)
);
