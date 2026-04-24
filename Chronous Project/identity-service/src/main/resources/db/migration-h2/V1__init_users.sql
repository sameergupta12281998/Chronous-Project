CREATE TABLE IF NOT EXISTS users (
    id            UUID PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
