# Chronos Operations Runbook

This runbook covers day-2 operations: building, running, troubleshooting, and rotating
configuration for the Chronos microservices platform.

## Build

```bash
mvn clean verify          # full hermetic test (H2 + EmbeddedKafka)
mvn -pl job-service -am test
mvn clean package -DskipTests   # produces fat jars in each module's target/
```

## Run locally

### Option A — Docker Compose (recommended)

```bash
mvn clean package -DskipTests
docker compose up --build
```

Brings up:
- `postgres` (5432) — auto-provisions `identity_db / job_db / scheduler_db / execution_db / notification_db`
- `kafka` (9092) — KRaft mode, auto-create topics enabled
- All six services on ports 8080–8085

### Option B — Bare metal

Requires PostgreSQL 16 and Kafka 3.7 running locally, then:

```bash
psql -U postgres -f deploy/postgres-init/00-create-databases.sql
java -jar identity-service/target/identity-service-*.jar &
java -jar job-service/target/job-service-*.jar &
java -jar scheduler-service/target/scheduler-service-*.jar &
java -jar execution-service/target/execution-service-*.jar &
java -jar notification-service/target/notification-service-*.jar &
java -jar api-gateway/target/api-gateway-*.jar &
```

## Required environment variables

| Variable                | Default (dev only)                                                | Notes                                        |
|-------------------------|-------------------------------------------------------------------|----------------------------------------------|
| `CHRONOS_JWT_SECRET`    | `change-me-please-this-is-a-development-only-secret-32+`          | **Must** be ≥ 32 chars. Rotate before prod.  |
| `KAFKA_BOOTSTRAP`       | `localhost:9092`                                                  | Comma-separated brokers in HA setups         |
| `IDENTITY_DB_URL`       | `jdbc:postgresql://localhost:5432/identity_db`                    | Override per-service in production           |
| `JOB_DB_URL`            | `jdbc:postgresql://localhost:5432/job_db`                         |                                              |
| `SCHEDULER_DB_URL`      | `jdbc:postgresql://localhost:5432/scheduler_db`                   |                                              |
| `EXECUTION_DB_URL`      | `jdbc:postgresql://localhost:5432/execution_db`                   |                                              |
| `NOTIFICATION_DB_URL`   | `jdbc:postgresql://localhost:5432/notification_db`                |                                              |

DB user/password vars follow the same per-service pattern (e.g. `JOB_DB_USER`,
`JOB_DB_PASSWORD`).

## Health checks

Each service exposes:

- `GET /actuator/health` — liveness/readiness
- `GET /actuator/prometheus` — Prometheus metrics
- `GET /actuator/info`

## Smoke test sequence

```bash
BASE=http://localhost:8080

# Register
curl -fs -X POST $BASE/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","email":"a@b.c","password":"S3cret!Pass"}'

# Login
TOKEN=$(curl -fs -X POST $BASE/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"S3cret!Pass"}' | jq -r .accessToken)

# Schedule
JOB=$(curl -fs -X POST $BASE/api/v1/jobs \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"name\":\"hello\",\"taskType\":\"EMAIL\",\"payload\":\"{}\",\"scheduleType\":\"ONE_TIME\",\"scheduledAt\":\"$(date -u -v+5S +%Y-%m-%dT%H:%M:%SZ)\",\"maxAttempts\":3}" | jq -r .id)

sleep 10

# Inspect — job status should be COMPLETED
curl -fs -H "Authorization: Bearer $TOKEN" $BASE/api/v1/jobs/$JOB | jq

# Notifications
curl -fs -H "Authorization: Bearer $TOKEN" $BASE/api/v1/notifications | jq
```

Expect to see one `JobCreated` notification and one `ExecutionSucceeded` notification.

## Troubleshooting

### `BUILD FAILURE: secret too short`
`JwtTokenService` enforces a 32-char minimum. Set `CHRONOS_JWT_SECRET` to a longer value.

### Job stays `SCHEDULED`, never `EXECUTING`
- Confirm `scheduler-service` is up (check `/actuator/health`)
- Tail its logs — the `DueJobScanner` emits a debug line per scan tick
- Verify `chronos.jobs.due.v1` is being produced (`kafka-console-consumer.sh`)

### Execution fails immediately and never retries
- Check that the task type has a registered handler (`EMAIL`, `WEBHOOK`)
- Verify `chronos.execution.retry-poll-ms` is set (default 1000ms)

### Notifications missing after success
- Verify the consumer group `notification.events` is committing offsets:
  `kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group notification.events`
- Check the `processed_events` table in `notification_db` — duplicates are silently
  dropped by design

### Gateway returns 401 for every request
- Token expired (TTL is 1h by default). Re-login.
- `CHRONOS_JWT_SECRET` mismatch between identity and gateway. Both must share the same
  value.

## Rotating the JWT secret

1. Issue a new secret (≥ 32 chars).
2. Roll all services with the new `CHRONOS_JWT_SECRET` simultaneously.
3. Existing tokens become invalid; clients must re-login. There is no dual-key support
   today — add one in production by extending `JwtTokenService` to accept a second
   verification key.

## Backup / restore

Each service's PostgreSQL DB can be backed up independently with `pg_dump`. There is no
cross-DB foreign-key relationship, so restores can proceed per-service.
