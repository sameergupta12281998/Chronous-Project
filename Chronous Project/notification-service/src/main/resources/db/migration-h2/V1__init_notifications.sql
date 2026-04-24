CREATE TABLE IF NOT EXISTS notifications (
    id            UUID PRIMARY KEY,
    owner_id      UUID NOT NULL,
    job_id        UUID,
    type          VARCHAR(50) NOT NULL,
    message       VARCHAR(500) NOT NULL,
    created_at    TIMESTAMP NOT NULL,
    dispatched_at TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_notifications_owner ON notifications(owner_id);

CREATE TABLE IF NOT EXISTS processed_events (
    event_id        VARCHAR(100) NOT NULL,
    consumer_group  VARCHAR(100) NOT NULL,
    processed_at    TIMESTAMP    NOT NULL,
    PRIMARY KEY (event_id, consumer_group)
);
