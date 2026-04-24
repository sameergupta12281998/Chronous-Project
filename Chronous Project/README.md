# Chronos — Distributed Job Scheduler

A production-grade backend for **Chronos**, a distributed job scheduling platform built as
six independent Java microservices on a Kafka event backbone, with database-per-service,
stateless JWT authentication, the transactional outbox pattern, idempotent consumers, and
exponential-backoff retries with terminal-failure handling.

> Built with Java 21, Spring Boot 3.3, Spring Cloud Gateway, Spring Kafka, Spring Data JPA,
> Flyway, PostgreSQL, and JJWT. Test stack: JUnit 5, Awaitility, H2 (PostgreSQL mode), and
> Spring's `@EmbeddedKafka`.

---

## Architecture

```
                   ┌────────────────────────┐
   Client ──HTTPS──►   api-gateway (8080)   │ JWT validation, route, header injection
                   └─────────┬──────────────┘
                             │
   ┌─────────────────────────┼──────────────────────────────────────────────┐
   │                         │                                              │
   ▼                         ▼                                              ▼
┌────────────────┐   ┌────────────────┐                              ┌─────────────────────┐
│ identity (8081)│   │   job (8082)   │── outbox ──► chronos.jobs.* ─► scheduler (8083)    │
│  register/     │   │  CRUD / cancel │                              │   schedules due      │
│  login → JWT   │   │  reschedule    │                              │   emits jobs.due     │
└────────────────┘   └────────────────┘                              └────────┬────────────┘
                                                                              │ chronos.jobs.due.v1
                                                                              ▼
                                                                  ┌────────────────────────┐
                                                                  │ execution (8084)       │
                                                                  │ run task, retry,       │
                                                                  │ emit executions.*      │
                                                                  └────────┬───────────────┘
                              chronos.executions.{succeeded,failed,terminal-failure}.v1
                                                                              │
                                                                              ▼
                                                                  ┌────────────────────────┐
                                                                  │ notification (8085)    │
                                                                  │ persist + dispatch +   │
                                                                  │ emit dispatched event  │
                                                                  └────────────────────────┘
```

Each service owns its database (`identity_db`, `job_db`, `scheduler_db`, `execution_db`,
`notification_db`). Cross-service state moves only through versioned Kafka topics carrying
an `EventEnvelope` (`eventId`, `eventType`, `schemaVersion`, `occurredAt`, `correlationId`,
`aggregateId`, `payload`).

---

## Modules

| Module                | Port | Responsibility                                                |
|-----------------------|------|---------------------------------------------------------------|
| `platform-commons`    | —    | Shared: events envelope, topics, JWT service, error model     |
| `identity-service`    | 8081 | User registration / login, issues HS256 JWTs                  |
| `job-service`         | 8082 | Job CRUD; persists schedule intent; transactional outbox      |
| `scheduler-service`   | 8083 | Maintains schedules; emits `chronos.jobs.due.v1`              |
| `execution-service`   | 8084 | Runs handlers, retries with backoff, emits execution events   |
| `notification-service`| 8085 | Reacts to job/execution events; persists + dispatches alerts  |
| `api-gateway`         | 8080 | Single entry, validates JWT, routes to backend services       |

### Kafka topics (versioned)

`chronos.jobs.created.v1`, `chronos.jobs.cancelled.v1`, `chronos.jobs.rescheduled.v1`,
`chronos.jobs.due.v1`, `chronos.executions.started.v1`, `chronos.executions.succeeded.v1`,
`chronos.executions.failed.v1`, `chronos.executions.terminal-failure.v1`,
`chronos.notifications.dispatched.v1`.

---

## Prerequisites

- Java 21 (Temurin recommended)
- Maven 3.9+
- Docker 24+ (for `docker compose`)

## Build & Test (no Docker required)

Each service uses H2 in PostgreSQL mode + `@EmbeddedKafka` for tests so the entire
multi-module build is hermetic:

```bash
mvn clean verify
```

You should see `BUILD SUCCESS` with all 7 reactor modules green and roughly 19+ tests
passing (unit + integration with Awaitility-based assertions for async event flows).

## Run locally with Docker Compose

```bash
mvn clean package -DskipTests
docker compose up --build
```

This brings up PostgreSQL, Kafka (KRaft single-node), and all six services. The gateway
is exposed on `http://localhost:8080`.

## Smoke test

```bash
# 1. Register
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","email":"alice@example.com","password":"S3cret!Pass"}'

# 2. Login
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"S3cret!Pass"}' | jq -r .accessToken)

# 3. Schedule an EMAIL job 5 seconds from now
curl -s -X POST http://localhost:8080/api/v1/jobs \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"hello\",\"taskType\":\"EMAIL\",\"payload\":\"{\\\"to\\\":\\\"a@b.c\\\"}\",\"scheduleType\":\"ONE_TIME\",\"scheduledAt\":\"$(date -u -v+5S +%Y-%m-%dT%H:%M:%SZ)\",\"maxAttempts\":3}"

# 4. After ~10s, your notification appears
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/notifications | jq
```

See [docs/RUNBOOK.md](docs/RUNBOOK.md) for operations details and
[docs/api-testing.md](docs/api-testing.md) for full endpoint coverage.

---

## Key design choices

- **Database-per-service.** No shared schemas. Cross-service joins are forbidden.
- **Transactional outbox.** `job-service` writes domain row + outbox row in one
  transaction; a scheduled publisher ships outbox rows to Kafka and marks them sent.
  No "publish-then-DB-fails" lost events.
- **Idempotent consumers.** Every consumer keeps a `processed_events (event_id, group)`
  table and skips duplicates, so at-least-once Kafka delivery becomes effectively-once.
- **Retries with exponential backoff.** Failed executions transition to `RETRY_SCHEDULED`
  with `next_attempt_at = now + min(60, 2^attempt)s`. After `maxAttempts`, the execution
  becomes `TERMINAL_FAILURE` and emits `chronos.executions.terminal-failure.v1`.
- **Stateless JWT auth.** Identity service signs HS256 tokens; the gateway validates and
  injects `X-Chronos-User-Id` / `X-Chronos-Username` into downstream requests so backend
  services need no session state.
- **Observability.** Spring Boot actuator (`/actuator/health`, `/actuator/prometheus`),
  JSON logs with `correlationId` MDC propagated through filters and Kafka headers.
- **Hexagonal-ish layout per service:** `domain/`, `service/`, `web/`, `consumer/`,
  `outbox/`, `dedupe/`, `config/`, `security/`, `runner/`.

## Repository layout

```
.
├── pom.xml                       # parent reactor
├── platform-commons/             # shared primitives
├── identity-service/
├── job-service/
├── scheduler-service/
├── execution-service/
├── notification-service/
├── api-gateway/
├── deploy/postgres-init/         # multi-DB bootstrap
├── docker-compose.yml
└── docs/
    ├── project.md
    ├── features.md
    ├── architecture.md
    ├── api-testing.md
    ├── testing.md
    ├── checklist.md
    ├── master-prompt.md
    └── RUNBOOK.md
```
