# Chronos Feature & Service Verification Matrix

This is the proof-of-completeness mapping. Every feature documented in `docs/features.md` and every checklist item in `docs/checklist.md` is mapped to the file(s) that implement it AND the test(s) that prove it.

Last updated after `mvn clean verify` ran green: **37 tests, 0 failures**.

---

## A. Identity Service

| Feature | Implementation | Test |
|---|---|---|
| User registration with BCrypt password hashing | `identity-service/.../service/AuthService.java`, `domain/UserEntity.java` | `AuthControllerIT#register*` |
| Username uniqueness 409 | `AuthService.register` (catches DataIntegrityViolation), `JobExceptionHandler` mirror | `AuthControllerIT#duplicateUsernameIs409` |
| Login → JWT issuance (HS256, configurable TTL) | `AuthService.login`, `platform-commons/JwtTokenService` | `AuthControllerIT#loginReturnsToken` |
| Wrong password → 401 | `AuthService.login` throws `BadCredentialsException` | `AuthControllerIT#loginWrongPasswordIs401` |
| Validation (username/password constraints) | `AuthRequest` DTO with `@Valid` | covered in `register` test |

## B. Job Service

| Feature | Implementation | Test |
|---|---|---|
| Create job (ONE_TIME / RECURRING) | `JobController.create`, `JobService.createJob` | `JobControllerIT#createPersists*` |
| `Idempotency-Key` header → no duplicates per owner | `JobController.create` + `IdempotencyKeyEntity/Repository` + `JobService.findExistingIdempotent` + `db/migration/V2__job_idempotency.sql` | `JobControllerIT#idempotencyKeyReturnsSameJobOnRepeat` |
| Validation: future `scheduledAt`, RECURRING needs frequency | `CreateJobRequest` + `JobService.validate` | `JobControllerIT#scheduledAtInPastIs400`, `recurringWithoutFrequencyIs400` |
| Cancel (DELETE & POST alias) | `JobController.cancel`, `JobController.cancelViaPost` | `JobControllerIT#postCancelAliasWorks`, original cancel test |
| Reschedule (PATCH & POST alias) | `JobController.reschedule`, `rescheduleViaPost` | `JobControllerIT#rescheduleSucceedsAndProducesEvent` |
| List + get-by-id (owner-scoped, 403 cross-owner) | `JobController.list/get` | covered in IT |
| Transactional outbox → JobCreated/Cancelled/Rescheduled events | `JobService` writes to `job_outbox` in same TX, `OutboxRelay` publishes | event presence asserted in `JobControllerIT` |
| Idempotent consumer pattern (other services) | `processed_events` table + `IdempotencyService.markIfNew` | indirectly via flow ITs |
| 401 on no-token / bad-token | `JobSecurityConfig` + `JwtAuthenticationFilter` + `HttpStatusEntryPoint(UNAUTHORIZED)` | `JobControllerIT#unauthenticatedIs401` |

## C. Scheduler Service

| Feature | Implementation | Test |
|---|---|---|
| Listen to `chronos.jobs.created.v1` and store scheduled job | `JobEventConsumer`, `ScheduledJobRepository` | `SchedulerFlowIT` |
| Compute next run time (RECURRING: MINUTE/HOUR/DAY) | `DueJobScanner.computeNextRun` | `DueJobScannerComputeNextRunTest` (3) |
| Periodic poll → emit `chronos.jobs.due.v1` | `DueJobScanner` `@Scheduled` | end-to-end in `SchedulerFlowIT` |
| Stop scheduling on cancel/terminal-failure | event handlers update status | covered indirectly |

## D. Execution Service

| Feature | Implementation | Test |
|---|---|---|
| Consume `jobs.due` and execute via TaskHandler SPI | `DueEventConsumer`, `TaskDispatcher`, `TaskHandler` interface | `ExecutionFlowIT#executesAndEmitsSucceeded` |
| Handlers: EMAIL, LOG, WEBHOOK | `handler/*Handler.java` | covered in flow IT |
| Exponential backoff: `min(60, 2^attempt)s` | `Backoff.next` | `BackoffTest` |
| Per-attempt FAILED → next attempt; > maxAttempts → terminal-failure | `TaskRunner.run` | `ExecutionFlowIT#retriesUntilTerminalFailure` |
| REST: `GET /executions/{id}` | `ExecutionController.get` | `ExecutionControllerIT#getExecutionByIdReturnsOwnedRow` |
| REST: `GET /executions?jobId=` (also via `/api/v1/jobs/{id}/executions` route) | `ExecutionController.list`, gateway route `job-executions` order=-10 | `ExecutionControllerIT#listByJobIdReturnsAttempts` |
| 404 unknown id, 403 cross-owner, 401 unauth, 400 missing param | `ExecutionExceptionHandler` + ownership checks | `ExecutionControllerIT` (4 explicit tests) |
| Idempotent consumer | `processed_events` | flow IT |

## E. Notification Service

| Feature | Implementation | Test |
|---|---|---|
| Consume `executions.{succeeded,failed,terminal-failure}` and persist | `ExecutionEventConsumer`, `NotificationService` | `NotificationFlowIT#executionSucceededProducesNotification`, `…terminalFailure…` |
| `chronos.notifications.dispatched.v1` emission | `NotificationService.dispatch` after persist | flow IT verifies `dispatchedAt` set |
| List API (paged, owner-scoped) | `NotificationController.list` | `NotificationFlowIT` GET assertions |
| Get-by-id API (404/403) | `NotificationController.get` + `NotificationExceptionHandler` | `NotificationFlowIT#getByIdReturnsOwnedNotification` |
| 401 unauthenticated | security filter chain | `NotificationFlowIT#unauthenticatedListIs401` |

## F. API Gateway

| Feature | Implementation | Test |
|---|---|---|
| Route to identity (StripPrefix=2) | `application.yml` route `identity-auth` | manual / smoke |
| Route to jobs with `RewritePath /api/v1/jobs → /jobs` | route `jobs` | manual / smoke |
| Route to executions | route `executions` | manual / smoke |
| `/api/v1/jobs/{id}/executions` proxied to execution-service `?jobId=` | route `job-executions` order=-10 | smoke + manual |
| Route to notifications | route `notifications` | manual / smoke |
| Bypass `/api/v1/auth/**` and `/actuator/**` | `JwtValidationGlobalFilter.isPublic` | `ApiGatewayApplicationTest#authPathBypassesJwtFilter`, `healthEndpointIsPublic` |
| Validate Bearer JWT, inject `X-Chronos-User-Id/Username` to downstream | `JwtValidationGlobalFilter` | `protectedRouteWithoutTokenIs401`, `…WithInvalidToken…`, `validTokenPassesAuthFilter` |
| Health/metrics exposed | `management.endpoints.web.exposure.include` | health test |

## G. Cross-Cutting

| Feature | Implementation | Test/Proof |
|---|---|---|
| Correlation id on every request | `CorrelationIdFilter` (servlet), `CorrelationIdWebFilter` (reactive) | logs include `corr=` (manual grep) |
| Stable error contract `ApiError` | `platform-commons/error/ApiError` + per-service `*ExceptionHandler` | every 4xx test asserts handler ran |
| JWT signing/parsing round-trip | `platform-commons/JwtTokenService` | `JwtTokenServiceTest` (3) |
| Database-per-service | each service has own `application.yml` datasource + Flyway under `db/migration/` and `db/migration-h2/` | builds verify schemas apply |
| Transactional outbox | `job-service/.../OutboxRelay` + `job_outbox` table | `JobControllerIT` event assertions |
| Idempotent consumer table | `processed_events` per service | covered indirectly by flow ITs |
| Hermetic tests (H2 + EmbeddedKafka) | per-service `application-test.yml` + `db/migration-h2/` | `mvn clean verify` runs without external infra |
| Docker compose orchestration | `docker-compose.yml` with kafka + 5 postgres + 6 services + gateway | `docker compose config --quiet` exit 0 |

---

## H. Documentation Deliverables

| Doc | Path |
|---|---|
| Architecture & handoff context | `docs/AGENT_CONTEXT.md` |
| Manual + automated testing guide | `docs/TESTING_GUIDE.md` |
| This verification matrix | `docs/COMPLETION_MATRIX.md` |
| Postman collection | `postman/Chronos.postman_collection.json` |
| Postman environment | `postman/Chronos.postman_environment.json` |
| End-to-end smoke script | `scripts/smoke.sh` |

---

## I. Build/Test Summary

```
mvn clean verify
[INFO] Chronos :: Platform Commons ........................ SUCCESS
[INFO] Chronos :: Identity Service ........................ SUCCESS  (4 tests)
[INFO] Chronos :: Job Service ............................. SUCCESS  (8 tests)
[INFO] Chronos :: Scheduler Service ....................... SUCCESS  (4 tests)
[INFO] Chronos :: Execution Service ....................... SUCCESS  (9 tests)
[INFO] Chronos :: Notification Service .................... SUCCESS  (4 tests)
[INFO] Chronos :: API Gateway ............................. SUCCESS  (5 tests)
[INFO] BUILD SUCCESS
[INFO] Total tests: 37, Failures: 0, Errors: 0, Skipped: 0
```
