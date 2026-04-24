# Chronos – Agent Handoff & Architecture Context

> **Purpose:** Single source of truth for any future contributor (human or AI) picking up this codebase. Reading this file end-to-end should give you everything required to build, run, extend, and debug Chronos without re-reading every source file.

---

## 1. Mission

Chronos is a distributed job scheduling platform composed of 6 Spring Boot microservices + 1 reactive API gateway. Users register/login, submit one-off or recurring jobs, and the system schedules, executes (with retry/backoff), and notifies them of outcomes — all via JWT-secured REST APIs and Kafka event streams.

---

## 2. Module Map

| Module | Port | DB | Responsibility |
|---|---|---|---|
| `platform-commons` | n/a | n/a | Shared records: `EventEnvelope`, `Topics`, `JwtTokenService`, `AuthenticatedUser`, `GatewayHeaders`, `CorrelationIds`, `ApiError`, `BadRequest/NotFound/Forbidden/ConflictException` |
| `identity-service` | 8081 | `identity_db` | Register/login, BCrypt + JWT issuance |
| `job-service` | 8082 | `job_db` | CRUD jobs + transactional outbox → Kafka |
| `scheduler-service` | 8083 | `scheduler_db` | Polls due jobs, emits `chronos.jobs.due.v1`, computes next run for recurring jobs |
| `execution-service` | 8084 | `execution_db` | Consumes due jobs, dispatches to task handlers, retries with exp. backoff, exposes execution history REST |
| `notification-service` | 8085 | `notification_db` | Listens to execution outcomes, writes notifications, exposes list/get REST |
| `api-gateway` | 8080 | n/a | Spring Cloud Gateway: validates JWT, injects user headers, routes to services |

---

## 3. Tech Stack

- **Java 21**, **Maven 3.9.10**, **Spring Boot 3.3.4**, **Spring Cloud 2023.0.3**
- **Spring Cloud Gateway** (WebFlux), **Spring Security** (servlet stack on services), **Spring Kafka**, **Spring Data JPA**
- **PostgreSQL 16** (per-service DB), **Flyway 10** (per-service migrations under `db/migration/` for prod, `db/migration-h2/` for tests)
- **Kafka 3.7 KRaft** (single-node in compose)
- **JJWT 0.12.6 HS256**, **BCrypt** (Spring Security `BCryptPasswordEncoder`)
- **Tests**: JUnit 5, AssertJ, Awaitility, **H2** in PostgreSQL mode + `@EmbeddedKafka` for hermetic IT tests

---

## 4. Authentication & Authorization

- **Identity service** `POST /auth/register` returns `UserResponse(id, username, email, createdAt)` and `POST /auth/login` returns `AuthTokenResponse(accessToken, tokenType="Bearer", expiresInSeconds, userId, username)`
- All other endpoints require `Authorization: Bearer <jwt>` header.
- **Gateway** validates JWT (HS256, shared secret env `CHRONOS_JWT_SECRET`, ≥32 chars), then strips `Authorization` and **adds gateway-injected headers**:
  - `X-Chronos-User-Id`: the user UUID (from `sub` claim)
  - `X-Chronos-Username`: from custom `username` claim
- Each downstream service (`job`, `execution`, `notification`) has a `JwtAuthenticationFilter` that:
  1. Trusts a Bearer JWT if directly hit (dev/local), OR
  2. Trusts the gateway headers (production path)
- Returns `401` when no token / invalid token (via `HttpStatusEntryPoint(UNAUTHORIZED)` — Spring's default would be 403).
- Returns `403` when authenticated but not the resource owner.

---

## 5. Endpoints (via Gateway, `BASE = http://localhost:8080`)

### Identity

| Method | Path | Auth | Body / Query | Returns |
|---|---|---|---|---|
| POST | `/api/v1/auth/register` | none | `{username,email,password}` | `201 UserResponse` |
| POST | `/api/v1/auth/login` | none | `{username,password}` | `200 AuthTokenResponse` |

### Jobs

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/v1/jobs` | Bearer | Optional `Idempotency-Key` header (per-owner). 201 first time, 200 on replay. |
| GET | `/api/v1/jobs` | Bearer | `?page=0&size=20`. Lists caller's jobs only |
| GET | `/api/v1/jobs/{id}` | Bearer | 404 if missing, 403 if not owner |
| PATCH | `/api/v1/jobs/{id}` | Bearer | reschedule – body `{scheduledAt}` |
| POST | `/api/v1/jobs/{id}/reschedule` | Bearer | alias for PATCH |
| DELETE | `/api/v1/jobs/{id}` | Bearer | cancel – 200 |
| POST | `/api/v1/jobs/{id}/cancel` | Bearer | alias for DELETE |
| GET | `/api/v1/jobs/{id}/executions` | Bearer | proxied to execution-service `/executions?jobId=…` (route order=-10) |

`CreateJobRequest`:
```json
{
  "name": "string (1-200)",
  "taskType": "EMAIL | LOG | WEBHOOK",
  "payload": "json string",
  "scheduleType": "ONE_TIME | RECURRING",
  "scheduledAt": "RFC3339 instant — required, must be in the future",
  "recurrenceFrequency": "MINUTE|HOUR|DAY (only for RECURRING)",
  "maxAttempts": "1..10 (default 3)"
}
```

### Executions

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/v1/executions/{id}` | Bearer | 404/403 |
| GET | `/api/v1/executions?jobId=UUID` | Bearer | Lists attempts for a job; 400 if `jobId` missing |

### Notifications

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/v1/notifications` | Bearer | `?page=0&size=20` paged |
| GET | `/api/v1/notifications/{id}` | Bearer | 404/403 |

### Ops

| Method | Path | Auth |
|---|---|---|
| GET | `/actuator/health` (gateway and each service direct port) | none |
| GET | `/actuator/prometheus` | none |

---

## 6. Kafka Topics (versioned, all under `Topics` constants)

| Topic | Producer | Consumer(s) | Purpose |
|---|---|---|---|
| `chronos.jobs.created.v1` | job-service (outbox) | scheduler-service | Acknowledge new job |
| `chronos.jobs.cancelled.v1` | job-service (outbox) | scheduler-service | Stop scheduling cancelled job |
| `chronos.jobs.rescheduled.v1` | job-service (outbox) | scheduler-service | Update next-run time |
| `chronos.jobs.due.v1` | scheduler-service | execution-service | Trigger execution |
| `chronos.executions.started.v1` | execution-service | (audit) | Heartbeat |
| `chronos.executions.succeeded.v1` | execution-service | notification-service | Success notification |
| `chronos.executions.failed.v1` | execution-service | notification-service | Per-attempt failure (for transient retries) |
| `chronos.executions.terminal-failure.v1` | execution-service | notification-service, scheduler-service | Final failure after maxAttempts |
| `chronos.notifications.dispatched.v1` | notification-service | (audit) | Confirms notification was written |

**Envelope:** every payload is `EventEnvelope<T>{ eventId, eventType, version, occurredAt, aggregateId, correlationId, data }` serialized as JSON. `eventId` is used by every consumer for idempotency via the `processed_events(event_id, consumer_group)` table.

---

## 7. Database-Per-Service

Each service owns its database; no cross-DB joins. Schemas evolve independently via Flyway.

- `identity_db` — `users(id, username UNIQUE, password_hash, created_at)`
- `job_db` — `jobs`, `job_outbox` (transactional outbox), `job_idempotency_keys(idempotency_key, owner_id, job_id, created_at)`, `processed_events`
- `scheduler_db` — `scheduled_jobs(id, owner_id, name, task_type, payload, schedule_type, recurrence_frequency, next_run_at, max_attempts, status)`, `processed_events`
- `execution_db` — `executions(id, job_id, owner_id, task_type, payload, attempt, max_attempts, status, error, started_at, finished_at, next_attempt_at, correlation_id)`, `processed_events`
- `notification_db` — `notifications(id, owner_id, job_id, type, message, created_at, dispatched_at)`, `processed_events`

---

## 8. Conventions & Patterns

1. **Constructor injection** everywhere. No `@Autowired` on fields.
2. **Hexagonal layout** per service: `web/` (controllers + DTOs + ExceptionHandler), `service/` (use cases), `domain/` (entities + repositories + enums), `kafka/` (producers/consumers), `config/` (Spring beans), `security/` (filters).
3. **Transactional outbox** (`job-service`): writes to `jobs` and `job_outbox` in same TX; a scheduled relay polls outbox and publishes to Kafka.
4. **Idempotent consumers**: every Kafka consumer wraps handler in a "have I seen `event_id` before for this consumer group?" check (`processed_events` table).
5. **Idempotent POST /jobs**: per-owner unique `Idempotency-Key` header → returns existing job on replay.
6. **Retry policy** (execution): `nextAttemptAt = now + min(60, 2^attempt) seconds`; emits `terminal-failure` when `attempt > maxAttempts`.
7. **Correlation IDs**: `CorrelationIdFilter` (servlet) and a WebFlux equivalent stamp `MDC[correlationId]` on every request and propagate through Kafka envelopes.
8. **API errors**: every service uses `ApiError(status, error, message, path, timestamp, correlationId, details)` from `platform-commons`.
9. **Spring Security default 403 → 401 override**: every service uses `.exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))`.
10. **Stateless sessions**: every service `SessionCreationPolicy.STATELESS` + CSRF disabled.
11. **Test profile**: H2 in PostgreSQL mode + `@EmbeddedKafka`. Tests load `application-test.yml` (NOT `application.yml` — see Gotchas).

---

## 9. How to Add a New Task Type

1. Add a value to `TaskType` enum (or string convention) used by `CreateJobRequest`.
2. In `execution-service`, add a `TaskHandler` bean implementing the `TaskHandler` SPI: `boolean supports(String type); ExecutionResult run(JobContext ctx)`.
3. The dispatcher auto-discovers handlers by `supports()`.

## 10. How to Add a New Event Type

1. Add a constant to `Topics` (`chronos.<aggregate>.<event>.v1`).
2. Define a Java record for the payload data.
3. Producer writes to outbox (if mutation-derived) or directly via `KafkaTemplate`.
4. Consumer subscribes via `@KafkaListener(topics = Topics.X)`, deserializes `EventEnvelope`, checks `processed_events`, handles, marks processed.

---

## 11. Environment Variables

| Var | Default | Used by | Purpose |
|---|---|---|---|
| `CHRONOS_JWT_SECRET` | a 56-char dev string | identity, job, execution, notification, gateway | HS256 signing key, **must be ≥32 chars** |
| `IDENTITY_URL` | `http://localhost:8081` | gateway | route target |
| `JOB_URL` | `http://localhost:8082` | gateway | route target |
| `EXECUTION_URL` | `http://localhost:8084` | gateway | route target |
| `NOTIFICATION_URL` | `http://localhost:8085` | gateway | route target |
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | per-service compose values | each service | Postgres connection |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `kafka:9092` (compose) | all services | Kafka cluster |
| `GATEWAY_PORT` | 8080 | gateway | bind port |

---

## 12. Build & Run

```bash
# Build & test everything (37 tests, hermetic — no Postgres or Kafka needed)
mvn clean verify

# Start full stack (Postgres, Kafka, all 6 services + gateway) on Docker
docker compose up --build

# Smoke test (after compose is up)
./scripts/smoke.sh
```

---

## 13. Gotchas (real bugs we hit; future-you will thank us)

1. **`Map.of(...)` forbids null values.** When building optional payload fields, use `HashMap` + `put`. Bit us in `JobService.createJob` for nullable `recurrenceFrequency`.
2. **Spring Security default = 403 for unauthenticated.** Override to 401 with `HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)` in every `SecurityFilterChain`.
3. **Failsafe needs `<build><plugins>`, not `<pluginManagement>`** in the parent pom for IT execution to actually run.
4. **Stale `application.yml` in `target/test-classes` shadows main yaml** even after rename. Always `mvn clean` after renaming/moving resource files.
5. **Spring Cloud Gateway routes only run if a route matches first.** A 404 from gateway means no predicate matched, NOT a downstream issue. Patterns like `Path=/api/v1/jobs/**` do NOT match `/api/v1/jobs` (no trailing); use `/api/v1/jobs/anything`.
6. **Route ordering matters.** The `job-executions` route (`/api/v1/jobs/{jobId}/executions`) must have a lower `order` (e.g., `-10`) than the broader `/api/v1/jobs/**` route, else the latter would swallow the path.
7. **Package-private test access**: shipping a test that pokes a service-private repo? Make sure the test class is in the same package (`src/test/java/com/airtribe/chronos/<svc>/...`).
8. **JJWT 0.12.x**: `Jwts.builder()` returns a `JwtBuilder`, parser is `Jwts.parser().verifyWith(key).build()`. Don't follow stale 0.11.x docs.
9. **Idempotency race**: after the pre-check returning the existing job, two concurrent first-creators can still race. We catch `DataIntegrityViolationException` on insert, then re-read.
10. **H2 in PostgreSQL mode**: works for most JPA, but a few PG-only constructs (e.g., `JSONB`, partial indexes) need separate `db/migration-h2/` files mirroring the prod migration.

---

## 14. Test Inventory (what each test proves)

| Module | Test class | What it covers |
|---|---|---|
| platform-commons | `JwtTokenServiceTest` (3) | issue/parse round-trip, expired-token rejection, tampered signature rejection |
| identity-service | `AuthControllerIT` (4) | register 201, duplicate 409, login 200, login wrong-password 401 |
| job-service | `JobControllerIT` (8) | create+JobCreated event, cancel+JobCancelled event, reschedule+JobRescheduled event, POST cancel alias, idempotency-key returns same job, scheduledAt-in-past 400, RECURRING without freq 400, list+get-by-id |
| scheduler-service | `DueJobScannerComputeNextRunTest` (3), `SchedulerFlowIT` (1) | cron-style next-run math, end-to-end JobCreated → due event |
| execution-service | `BackoffTest` (1), `ExecutionFlowIT` (3), `ExecutionControllerIT` (6) | backoff math, due → succeeded path, retry → terminal-failure, REST get-by-id, 403 cross-owner, 404 unknown, 401 unauthenticated, list-by-jobId, missing-param 400 |
| notification-service | `NotificationFlowIT` (4) | succeeded → notification written, terminal-failure message includes error, list 401 unauthenticated, get-by-id with 200/403/404 paths |
| api-gateway | `ApiGatewayApplicationTest` (5) | health public, no token 401, invalid token 401, auth path bypassed, valid token reaches upstream stage |

**Total: 37 tests, all green via `mvn clean verify`.**

---

## 15. Where to look first when something breaks

- **401 on a request that should pass** → check token in `Authorization`; `JwtValidationGlobalFilter` in gateway logs at DEBUG; service `JwtAuthenticationFilter`.
- **404 from gateway** → no route matched. Inspect `application.yml` predicates and route ordering.
- **403** → user is authenticated but not the resource owner; check repo/service ownership guards.
- **Job created but never executed** → check `job_outbox` table is being drained; check Kafka topic `chronos.jobs.due.v1` for the event; check scheduler logs.
- **Execution looping forever** → check `attempt` and `maxAttempts` on the row; the runner should emit terminal-failure when `attempt > maxAttempts`.
- **Notification not received** → check execution-service produced an `executions.{succeeded|terminal-failure}.v1` event; check notification consumer logs and `processed_events` for that `event_id`.
- All logs include `corr=<correlationId>` — grep across services to follow a single request.
