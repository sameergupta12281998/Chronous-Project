-- Bootstraps per-service databases for the Chronos platform. PostgreSQL spins this
-- up on first container start. Each microservice owns one logical database.
CREATE DATABASE identity_db;
CREATE DATABASE job_db;
CREATE DATABASE scheduler_db;
CREATE DATABASE execution_db;
CREATE DATABASE notification_db;
