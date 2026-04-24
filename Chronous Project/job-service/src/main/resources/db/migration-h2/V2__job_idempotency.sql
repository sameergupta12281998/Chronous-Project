CREATE TABLE IF NOT EXISTS job_idempotency_keys (
    idempotency_key  VARCHAR(200) NOT NULL,
    owner_id         UUID         NOT NULL,
    job_id           UUID         NOT NULL,
    created_at       TIMESTAMP    NOT NULL,
    PRIMARY KEY (idempotency_key, owner_id)
);

CREATE INDEX IF NOT EXISTS idx_idempotency_owner ON job_idempotency_keys(owner_id);
