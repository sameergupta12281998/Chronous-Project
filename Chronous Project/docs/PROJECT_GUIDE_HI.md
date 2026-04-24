# Chronos Project Ki Hindi Deep-Dive Guide

Yeh document is project ko step by step samjhane ke liye banaya gaya hai. Iska goal sirf high-level overview dena nahi hai, balki yeh batana hai ki project kis architecture par bana hai, request system me kaise travel karti hai, har service ka role kya hai, aur repo ki important files kyon exist karti hain.

Is guide me `target/` jaise generated build artifacts, IDE temporary files, aur auto-generated compiled output ko detail me include nahi kiya gaya hai, kyunki unse architecture samajhne me value nahi milti. Focus source-controlled aur hand-written files par hai.

---

## 1. Sabse Pehle Samjho: Yeh Project Kya Hai?

Chronos ek distributed job scheduling platform hai.

Simple language me:

1. User register ya login karta hai.
2. User ek job create karta hai.
3. Job ya to one-time hoti hai ya recurring hoti hai.
4. Scheduler service check karti hai ki kaunsi job due ho gayi.
5. Execution service us job ko run karti hai.
6. Result ke basis par notification service user ke liye notification store karti hai.
7. API Gateway sab external traffic ke liye single entry point ka kaam karta hai.

Matlab project ka business purpose hai: "user ke scheduled tasks ko reliable, secure, aur scalable tareeke se chalana".

---

## 2. Kaunsi Architecture Use Hui Hai?

Is project me ek hi architecture nahi, balki multiple proven enterprise patterns ka combination use hua hai.

### 2.1 Microservices Architecture

Project ko alag-alag services me toda gaya hai:

1. `identity-service`
2. `job-service`
3. `scheduler-service`
4. `execution-service`
5. `notification-service`
6. `api-gateway`
7. `platform-commons` shared library hai, runtime service nahi.

Iska fayda:

1. Har service ka clear responsibility hota hai.
2. Alag-alag service independently change ho sakti hai.
3. Scaling targeted ho sakti hai.
4. Failure isolation better hota hai.

### 2.2 API Gateway Pattern

External client directly har service ko call nahi karta. Pehle request `api-gateway` par aati hai.

Gateway ka role:

1. JWT validate karna
2. Request ko sahi downstream service tak route karna
3. User identity headers inject karna
4. Public aur protected routes ko separate handle karna

### 2.3 Event-Driven Architecture

Services sirf synchronous REST calls par depend nahi karti. Internal workflow Kafka events se driven hai.

Example:

1. Job create hui
2. Job service ne event publish kiya
3. Scheduler ne event consume kiya
4. Jab job due hui to scheduler ne naya event publish kiya
5. Execution service ne consume kiya
6. Execution result event notification service ne consume kiya

Yeh system ko loosely coupled banata hai.

### 2.4 Database-Per-Service Pattern

Har service ki apni alag database hai:

1. `identity_db`
2. `job_db`
3. `scheduler_db`
4. `execution_db`
5. `notification_db`

Iska matlab koi shared database monolith nahi hai. Har service apne data ki owner hai.

### 2.5 Transactional Outbox Pattern

`job-service` me outbox pattern use hua hai. Jab job create/cancel/reschedule hoti hai, tab business data aur event dono ek transactional boundary me store hote hain. Baad me outbox publisher Kafka me safely event push karta hai.

Iska fayda:

1. DB save hua lekin event publish na ho aisa mismatch avoid hota hai.
2. Eventual consistency reliable hoti hai.

### 2.6 Idempotent Consumer Pattern

Kafka consumers duplicate event dobara process na karein, isliye `processed_events` table aur processed-event entities/repositories use hue hain.

Iska matlab same Kafka event agar dobara aa jaye to system duplicate side effects nahi karega.

### 2.7 Stateless JWT Security

Session based auth nahi hai. JWT token hai. Har request me token aata hai. Gateway validate karta hai. Services ya to JWT ya gateway injected headers trust karti hain.

### 2.8 Hermetic Testing Strategy

Tests ke liye external Kafka/Postgres mandatory nahi hai. H2 + EmbeddedKafka use karke integration tests reliable aur fast banaye gaye hain.

---

## 3. High-Level System Flow Diagram Socho

Yeh logical flow hai:

```text
Client
  |
  v
API Gateway (JWT validate + route)
  |
  +--> Identity Service  -> identity_db
  |
  +--> Job Service       -> job_db -> outbox -> Kafka
  |                                        |
  |                                        v
  |                                 Scheduler Service -> scheduler_db -> Kafka
  |                                                           |
  |                                                           v
  |                                                  Execution Service -> execution_db -> Kafka
  |                                                           |
  |                                                           v
  |                                                Notification Service -> notification_db
  |
  +--> Read APIs via gateway back to client
```

Important baat: external world mostly gateway se baat karta hai, services aapas me mostly Kafka events ke through coordinate karti hain.

---

## 4. Agar Project Ko Pehli Baar Samajhna Hai To Reading Order Kya Ho?

Recommended order:

1. Root `pom.xml`
2. `docs/architecture.md`
3. `docs/AGENT_CONTEXT.md`
4. `platform-commons`
5. `identity-service`
6. `job-service`
7. `scheduler-service`
8. `execution-service`
9. `notification-service`
10. `api-gateway`
11. `docker-compose.yml`
12. `scripts/smoke.sh`
13. `postman/` collection

Reason:

1. Pehle overall build aur shared contracts samajh aate hain.
2. Phir auth samajh aata hai.
3. Uske baad write-side flow (job create) samajh aata hai.
4. Phir asynchronous processing samajh aati hai.
5. End me gateway aur testing tools samajh aate hain.

---

## 5. Step-by-Step Runtime Flow: Request System Me Kaise Chalti Hai?

## 5.1 User Register/Login Flow

Step by step:

1. Client `POST /api/v1/auth/register` ya `POST /api/v1/auth/login` call karta hai.
2. Gateway auth endpoints ko public maan kar JWT check skip karta hai.
3. Request `identity-service` me jaati hai.
4. `AuthController` request DTO receive karta hai.
5. `UserService` ya auth logic password validate karta hai.
6. New user hone par password BCrypt se hash hota hai.
7. Register path par user persist hota hai aur `UserResponse(id, username, email, createdAt)` return hota hai.
8. Login path par JWT token `JwtTokenService` generate karta hai aur response me `accessToken`, `tokenType`, `expiresInSeconds`, `userId`, `username` aata hai.

Key files involved:

1. `identity-service/.../web/AuthController.java`
2. `identity-service/.../service/UserService.java`
3. `platform-commons/.../security/JwtTokenService.java`
4. `identity-service/.../domain/UserEntity.java`
5. `identity-service/.../domain/UserRepository.java`

## 5.2 Job Create Flow

Step by step:

1. Client `POST /api/v1/jobs` call karta hai.
2. Gateway JWT validate karta hai.
3. Gateway user ID aur username ko headers me inject karta hai.
4. Request `job-service` tak route hoti hai.
5. `JwtAuthenticationFilter` downstream request ko authenticate karta hai.
6. `JobController` request accept karta hai.
7. `JobService` validation karta hai:
   - scheduled time future me honi chahiye
   - recurring job me recurrence frequency honi chahiye
   - user owner set hota hai
8. Agar `Idempotency-Key` aaya hai to pehle dedupe check hota hai.
9. Job DB me save hoti hai.
10. Saath me outbox record save hota hai.
11. Response client ko mil jata hai.
12. Thodi der me `OutboxPublisher` outbox record ko Kafka event me convert karke publish karta hai.

Important insight: create job ke waqt scheduler ko direct REST call nahi hoti. Event-driven handoff hota hai.

## 5.3 Scheduler Flow

Step by step:

1. `scheduler-service` Kafka se jobs lifecycle events consume karta hai.
2. New job aane par local schedule read model me entry banti hai.
3. `DueJobScanner` periodic scan karta hai.
4. Jis job ka `next_run_at` due ho jata hai, uske liye `chronos.jobs.due.v1` event publish hota hai.
5. Recurring job ho to next run compute hota hai.
6. Cancelled/terminal failure events ke basis par scheduling stop bhi ho sakti hai.

## 5.4 Execution Flow

Step by step:

1. `execution-service` `chronos.jobs.due.v1` consume karta hai.
2. Execution row create/update hoti hai.
3. `TaskHandlerRegistry` task type ke hisaab se handler choose karta hai.
4. Example handlers:
   - `EmailTaskHandler`
   - `WebhookTaskHandler`
5. `ExecutionRunner` actual execution run karta hai.
6. Success par success event publish hota hai.
7. Failure par retry/backoff logic run hoti hai.
8. Max attempts cross hone par terminal failure event publish hota hai.
9. Execution history REST API se user later fetch kar sakta hai.

## 5.5 Notification Flow

Step by step:

1. `notification-service` execution result events consume karta hai.
2. Notification entity create hoti hai.
3. Dispatcher abstraction ke through notification dispatch hoti hai.
4. Abhi default implementation logging-based hai.
5. Notification DB me persist rehti hai.
6. User `GET /api/v1/notifications` aur `GET /api/v1/notifications/{id}` se dekh sakta hai.

## 5.6 Read APIs Flow

Create ke baad user data dekhna chahe to:

1. Gateway token validate karta hai.
2. Read request specific service ko route hoti hai.
3. Controller owner check karta hai.
4. Data DTO me map hota hai.
5. Response client ko milta hai.

Examples:

1. `GET /api/v1/jobs/{id}` -> `job-service`
2. `GET /api/v1/jobs/{id}/executions` -> gateway rewrites to execution query endpoint
3. `GET /api/v1/executions/{id}` -> `execution-service`
4. `GET /api/v1/notifications/{id}` -> `notification-service`

## 5.7 Failure Aur Retry Flow

Agar execution fail hoti hai:

1. Failure record hota hai.
2. Retry backoff calculate hota hai.
3. `min(60, 2^attempt)` seconds ka backoff lagta hai.
4. Retry scheduled hoti hai.
5. Agar max attempts cross ho gaye to terminal failure event publish hota hai.
6. Notification service failure notification store karti hai.

---

## 6. Root Level Repo Map: Top-Level Files Aur Folders Ka Kya Kaam Hai?

### 6.1 Root Files

1. `pom.xml` - parent Maven build file; sab modules ko aggregate karta hai; plugin versions aur shared build rules yahin se aate hain.
2. `docker-compose.yml` - full local stack orchestration; Kafka, PostgreSQL instances, services, gateway sab ko start karta hai.
3. `.gitignore` - build outputs aur local files ko git se ignore karta hai.
4. `.editorconfig` - formatting/editor consistency ke liye.
5. `README.md` - quick start, smoke commands, top-level usage.

### 6.2 Root Folders

1. `platform-commons/` - shared library used by all modules.
2. `identity-service/` - authentication service.
3. `job-service/` - job write/read lifecycle service.
4. `scheduler-service/` - due scanning and schedule calculation.
5. `execution-service/` - task execution engine.
6. `notification-service/` - notification persistence and access.
7. `api-gateway/` - single public entry point.
8. `deploy/` - infra bootstrap files.
9. `docs/` - design, testing, feature, handoff docs.
10. `postman/` - API collection for manual testing.
11. `scripts/` - shell scripts, especially smoke testing.
12. `target/` - generated build output; source understanding ke liye ignore karo.
13. `.vscode/` - editor settings/tasks.

---

## 7. `deploy/` Folder Ka Role

1. `deploy/postgres-init/00-create-databases.sql` - Docker Postgres bootstrap ke waqt alag-alag service databases create karta hai.

Yeh file important hai kyunki architecture database-per-service pattern follow karta hai.

---

## 8. `docs/` Folder Ka Purpose Kya Hai?

1. `docs/architecture.md` - architecture overview.
2. `docs/context.md` - project context aur reasoning notes.
3. `docs/project.md` - overall project explanation.
4. `docs/features.md` - feature list aur expected APIs.
5. `docs/checklist.md` - delivery/completeness checklist.
6. `docs/testing.md` - testing notes.
7. `docs/api-testing.md` - API testing guide/postman flow.
8. `docs/RUNBOOK.md` - operational run steps.
9. `docs/agent.md` - agent-oriented repo notes.
10. `docs/master-prompt.md` - task/prompt guidance file.
11. `docs/AGENT_CONTEXT.md` - future contributors/agents ke liye technical handoff doc.
12. `docs/TESTING_GUIDE.md` - automated aur manual testing ka detailed guide.
13. `docs/COMPLETION_MATRIX.md` - kaunsi feature kis file/test se prove hoti hai uska matrix.
14. `docs/PROJECT_GUIDE_HI.md` - yeh current Hindi deep-dive guide.

---

## 9. `postman/` Aur `scripts/` Folder Ka Purpose

1. `postman/Chronos.postman_collection.json` - manual API testing collection.
2. `postman/Chronos.postman_environment.json` - Postman environment variables.
3. `scripts/smoke.sh` - end-to-end smoke validation script.

---

## 10. `platform-commons` Module: Iski Har File Kya Karti Hai?

`platform-commons` project ka shared foundation hai. Har service isse depend karti hai.

### 10.1 Build File

1. `platform-commons/pom.xml` - shared library ka Maven definition.

### 10.2 `correlation/`

1. `CorrelationIds.java` - correlation ID ke constants aur helpers. Logs aur distributed tracing ke liye important.

### 10.3 `error/`

1. `ApiError.java` - standard API error response model.
2. `BadRequestException.java` - 400 errors ko represent karta hai.
3. `ConflictException.java` - 409 conflicts ke liye.
4. `ForbiddenException.java` - 403 authorization/ownership issues ke liye.
5. `NotFoundException.java` - 404 not found cases ke liye.

### 10.4 `event/`

1. `EventEnvelope.java` - Kafka events ka standard wrapper; correlation, aggregate, versioning sab yahan hota hai.
2. `Topics.java` - project ke saare Kafka topic names ka single source of truth.

### 10.5 `security/`

1. `AuthenticatedUser.java` - authenticated principal model.
2. `GatewayHeaders.java` - gateway se inject hone wale custom header names.
3. `InvalidJwtException.java` - invalid JWT parse hone par custom error.
4. `JwtTokenService.java` - token create aur validate dono karta hai.

### 10.6 Tests

1. `platform-commons/src/test/java/com/airtribe/chronos/commons/security/JwtTokenServiceTest.java` - token issue/parse/expiry/tamper cases verify karta hai.

---

## 11. `identity-service` Module: File-by-File Samjho

Purpose: user registration, login, aur JWT issue karna.

### 11.1 Build Aur Entry

1. `identity-service/pom.xml` - module dependencies aur build config.
2. `IdentityServiceApplication.java` - Spring Boot main entry point.

### 11.2 `config/`

1. `IdentityConfig.java` - beans define karta hai, jaise password encoder aur JWT service.

### 11.3 `domain/`

1. `UserEntity.java` - database me user ka shape define karta hai.
2. `UserRepository.java` - user ko query/save karne ka repository layer.

### 11.4 `service/`

1. `UserService.java` - actual business logic: register, login, password match, token issue.

### 11.5 `web/`

1. `AuthController.java` - public HTTP endpoints expose karta hai.
2. `AuthTokenResponse.java` - successful auth response DTO.
3. `LoginRequest.java` - login input model.
4. `RegisterRequest.java` - registration input model.
5. `UserResponse.java` - user details response model.
6. `CorrelationIdFilter.java` - incoming request me correlation ID manage karta hai.
7. `GlobalExceptionHandler.java` - exceptions ko stable JSON errors me convert karta hai.

### 11.6 Resources

1. `src/main/resources/application.yml` - port, datasource, JWT secret, Flyway config.
2. `src/main/resources/logback-spring.xml` - logging format aur levels.
3. `src/main/resources/db/migration/V1__init_users.sql` - users table create karta hai.
4. `src/main/resources/db/migration-h2/V1__init_users.sql` - H2 test DB ke liye compatible schema.

### 11.7 Tests

1. `src/test/resources/application.yml` - test profile config.
2. `src/test/java/com/airtribe/chronos/identity/AuthControllerIT.java` - register/login end-to-end integration test.

---

## 12. `job-service` Module: Yeh Sabse Important Write-Side Service Hai

Purpose: jobs create, fetch, reschedule, cancel karna; outbox events generate karna.

### 12.1 Build Aur Entry

1. `job-service/pom.xml` - dependencies aur module build definition.
2. `JobServiceApplication.java` - Spring Boot entry point.

### 12.2 `config/`

1. `JacksonConfig.java` - JSON serialization/deserialization behavior customize karta hai.
2. `JobKafkaConfig.java` - Kafka producer/consumer related beans configure karta hai.
3. `JobSecurityConfig.java` - security chain define karta hai; authenticated requests aur 401 behavior yahin control hota hai.

### 12.3 `consumer/`

1. `ExecutionEventConsumer.java` - execution result events consume karke job state ko update karta hai.
2. `ProcessedEventEntity.java` - consumed event dedupe record ka entity.
3. `ProcessedEventId.java` - composite key model for processed event table.
4. `ProcessedEventRepository.java` - dedupe lookup/save operations.

### 12.4 `domain/`

1. `JobEntity.java` - jobs table ka domain model.
2. `JobRepository.java` - DB access layer for jobs.
3. `JobStatus.java` - job states enum.
4. `RecurrenceFrequency.java` - recurring jobs ki frequency enum.
5. `ScheduleType.java` - one-time ya recurring type enum.

### 12.5 `idempotency/`

1. `IdempotencyKeyEntity.java` - per-owner idempotency key store karta hai.
2. `IdempotencyKeyRepository.java` - key lookup aur persist operations.

### 12.6 `outbox/`

1. `OutboxEntity.java` - pending domain events ka DB representation.
2. `OutboxRepository.java` - unsent outbox entries fetch karta hai.
3. `OutboxPublisher.java` - scheduled publisher jo outbox se Kafka me event bhejta hai.

### 12.7 `security/`

1. `AuthenticatedUserResolver.java` - request se current authenticated user resolve karta hai.
2. `JwtAuthenticationFilter.java` - token ya gateway headers ko trust karke security context populate karta hai.

### 12.8 `service/`

1. `JobService.java` - core business rules yahin hain: create job, cancel, reschedule, ownership validation, idempotency handling, outbox integration.

### 12.9 `web/`

1. `CreateJobRequest.java` - create job API input.
2. `JobController.java` - REST endpoints expose karta hai, including aliases like POST cancel/reschedule.
3. `JobExceptionHandler.java` - API exceptions ko normalize karta hai.
4. `JobResponse.java` - job output DTO.
5. `RescheduleRequest.java` - reschedule endpoint input DTO.
6. `CorrelationIdFilter.java` - request traceability maintain karta hai.

### 12.10 Resources

1. `src/main/resources/application.yml` - port, datasource, Kafka, JWT, outbox settings.
2. `src/main/resources/logback-spring.xml` - logging setup.
3. `src/main/resources/db/migration/V1__init_jobs.sql` - main jobs schema.
4. `src/main/resources/db/migration/V2__job_idempotency.sql` - idempotency support aur related schema.
5. `src/main/resources/db/migration-h2/V1__init_jobs.sql` - H2-compatible initial schema.
6. `src/main/resources/db/migration-h2/V2__job_idempotency.sql` - H2-compatible idempotency schema.

### 12.11 Tests

1. `src/test/resources/application.yml` - test config.
2. `src/test/java/com/airtribe/chronos/job/JobControllerIT.java` - job APIs, validation, event behavior, idempotency aur aliases verify karta hai.

---

## 13. `scheduler-service` Module: Job Ko Due Banane Wali Service

Purpose: schedule read model maintain karna aur due jobs ke events publish karna.

### 13.1 Build Aur Entry

1. `scheduler-service/pom.xml` - scheduler module build config.
2. `SchedulerServiceApplication.java` - Spring Boot entry.

### 13.2 `config/`

1. `SchedulerKafkaConfig.java` - Kafka producer/consumer setup.

### 13.3 `consumer/`

1. `JobLifecycleConsumer.java` - created/cancelled/rescheduled events consume karta hai aur schedule table update karta hai.

### 13.4 `dedupe/`

1. `ProcessedEventEntity.java` - duplicate event process rokne ke liye entity.
2. `ProcessedEventRepository.java` - dedupe lookup repository.

### 13.5 `domain/`

1. `ScheduleEntity.java` - scheduled jobs ka local read model.
2. `ScheduleRepository.java` - due schedules aur save operations.

### 13.6 `scan/`

1. `DueJobScanner.java` - periodic scanner jo due jobs ko detect karke due events publish karta hai; recurring jobs ka next run bhi compute karta hai.

### 13.7 Resources

1. `src/main/resources/application.yml` - port, datasource, Kafka, scan timing.
2. `src/main/resources/logback-spring.xml` - logging config.
3. `src/main/resources/db/migration/V1__init_schedules.sql` - schedule storage schema.
4. `src/main/resources/db/migration-h2/V1__init_schedules.sql` - H2-compatible test schema.

### 13.8 Tests

1. `src/test/resources/application.yml` - test config.
2. `src/test/java/com/airtribe/chronos/scheduler/SchedulerFlowIT.java` - lifecycle-to-due event flow test.
3. `src/test/java/com/airtribe/chronos/scheduler/scan/DueJobScannerComputeNextRunTest.java` - next-run computation logic verify karta hai.

---

## 14. `execution-service` Module: System Ka Execution Engine

Purpose: due jobs ko actual task execution me convert karna.

### 14.1 Build Aur Entry

1. `execution-service/pom.xml` - execution module build config.
2. `ExecutionServiceApplication.java` - Spring Boot main class.

### 14.2 `config/`

1. `ExecutionKafkaConfig.java` - Kafka beans aur messaging setup.
2. `ExecutionWebSecurityConfig.java` - execution REST endpoints ke liye JWT-based security chain.

### 14.3 `consumer/`

1. `JobDueConsumer.java` - due job events ko consume karke execution start trigger karta hai.

### 14.4 `dedupe/`

1. `ProcessedEventEntity.java` - dedupe table mapping entity.
2. `ProcessedEventRepository.java` - already-processed events check karta hai.

### 14.5 `domain/`

1. `ExecutionEntity.java` - execution attempt ka DB model.
2. `ExecutionRepository.java` - query/save operations.
3. `ExecutionStatus.java` - execution state enum.

### 14.6 `handler/`

1. `TaskHandler.java` - task handlers ka contract.
2. `EmailTaskHandler.java` - email-type jobs handle karta hai.
3. `WebhookTaskHandler.java` - webhook-type jobs handle karta hai.
4. `TaskHandlerRegistry.java` - task type se correct handler choose karta hai.
5. `TaskExecutionException.java` - execution failure ko represent karta hai.

### 14.7 `runner/`

1. `ExecutionRunner.java` - handler run karta hai, state transition manage karta hai, events publish karta hai.
2. `RetryScheduler.java` - failed execution ke liye retry/backoff logic chalata hai.

### 14.8 `security/`

1. `JwtAuthenticationFilter.java` - gateway headers ya bearer token se authenticated user build karta hai.

### 14.9 `web/`

1. `ExecutionController.java` - execution history aur execution detail REST endpoints expose karta hai.
2. `ExecutionExceptionHandler.java` - 400/403/404/500 ko standardized ApiError me convert karta hai.

### 14.10 Resources

1. `src/main/resources/application.yml` - port, datasource, Kafka, JWT config, retry settings.
2. `src/main/resources/logback-spring.xml` - logging config.
3. `src/main/resources/db/migration/V1__init_executions.sql` - executions schema.
4. `src/main/resources/db/migration-h2/V1__init_executions.sql` - H2-compatible schema.

### 14.11 Tests

1. `src/test/resources/application.yml` - test config.
2. `src/test/java/com/airtribe/chronos/execution/ExecutionControllerIT.java` - execution REST endpoints verify karta hai.
3. `src/test/java/com/airtribe/chronos/execution/ExecutionFlowIT.java` - due event se execution result tak ka flow verify karta hai.
4. `src/test/java/com/airtribe/chronos/execution/runner/BackoffTest.java` - retry backoff formula verify karta hai.

---

## 15. `notification-service` Module: User-Facing Outcome Store

Purpose: execution outcomes ko user-notifications me convert karna.

### 15.1 Build Aur Entry

1. `notification-service/pom.xml` - module build config.
2. `NotificationServiceApplication.java` - Spring Boot entry point.

### 15.2 `config/`

1. `NotificationConfig.java` - notification module ke beans aur security configuration ka central setup.

### 15.3 `consumer/`

1. `NotificationEventConsumer.java` - execution result events consume karke notifications create karta hai.

### 15.4 `dedupe/`

1. `ProcessedEventEntity.java` - duplicate Kafka events ko track karta hai.
2. `ProcessedEventRepository.java` - dedupe storage access layer.

### 15.5 `dispatch/`

1. `NotificationDispatcher.java` - notification dispatch ka abstraction.
2. `LoggingNotificationDispatcher.java` - simple logging dispatcher; dev-friendly implementation.

### 15.6 `domain/`

1. `NotificationEntity.java` - notifications table ka entity model.
2. `NotificationRepository.java` - fetch/save/paging operations.

### 15.7 `security/`

1. `JwtAuthenticationFilter.java` - JWT ya gateway headers se authenticated user resolve karta hai.

### 15.8 `web/`

1. `NotificationController.java` - list aur get-by-id notification APIs expose karta hai.
2. `NotificationExceptionHandler.java` - exceptions ko standard API error me map karta hai.

### 15.9 Resources

1. `src/main/resources/application.yml` - port, datasource, Kafka, JWT config.
2. `src/main/resources/logback-spring.xml` - logging setup.
3. `src/main/resources/db/migration/V1__init_notifications.sql` - notifications schema.
4. `src/main/resources/db/migration-h2/V1__init_notifications.sql` - H2-compatible test schema.

### 15.10 Tests

1. `src/test/resources/application.yml` - test config.
2. `src/test/java/com/airtribe/chronos/notification/NotificationFlowIT.java` - notification creation aur APIs verify karta hai.

---

## 16. `api-gateway` Module: System Ka Front Door

Purpose: external traffic ka single entry point.

### 16.1 Build Aur Entry

1. `api-gateway/pom.xml` - gateway module dependencies.
2. `ApiGatewayApplication.java` - reactive Spring Boot gateway entry point.

### 16.2 `config/`

1. `GatewayConfig.java` - gateway beans/configuration support.

### 16.3 `security/`

1. `JwtValidationGlobalFilter.java` - gateway par sabse important security filter; JWT validate karta hai, public paths skip karta hai, aur downstream headers inject karta hai.

### 16.4 Resources

1. `src/main/resources/application.yml` - routes define karta hai:
   - auth route
   - jobs route
   - job-executions route
   - executions route
   - notifications route
2. `src/main/resources/logback-spring.xml` - reactive gateway logging config.

### 16.5 Tests

1. `src/test/resources/application-test.yml` - test profile config.
2. `src/test/java/com/airtribe/chronos/gateway/ApiGatewayApplicationTest.java` - health public hai, invalid token 401 deta hai, auth path bypass hota hai, valid token reject nahi hota yeh verify karta hai.

---

## 17. Database Migrations Ko Kaise Samjho?

Har service ke paas usually do migration trees hain:

1. `db/migration/` - real runtime schema for PostgreSQL.
2. `db/migration-h2/` - test-only compatible schema for H2.

Is separation ka reason hai:

1. Production aur test DB behavior me differences ho sakte hain.
2. Integration tests reliable rakhne ke liye H2-specific compatibility helpful hoti hai.

---

## 18. Test Strategy Ko Kaise Samjho?

Project me multiple types ke tests hain:

1. Unit-style test: isolated logic verify karte hain.
2. Integration tests: Spring Boot context ke saath real HTTP/Kafka/DB behavior verify karte hain.
3. EmbeddedKafka tests: asynchronous flows ko bina external Kafka ke test karte hain.
4. H2-backed tests: database behavior local test environment me verify karte hain.
5. Gateway WebTestClient tests: reactive gateway security/routing verify karte hain.

Testing ka key principle yeh hai ki system behavior proof-based ho, dummy nahi.

---

## 19. Important Cross-Cutting Concepts Jo Project Samajhne Ke Liye Bahut Zaroori Hain

### 19.1 Correlation ID

Request ek service se doosri service tak trace karne ke liye correlation ID logs me carry hoti hai.

### 19.2 Ownership Checks

User kisi aur user ka job/execution/notification access na kar sake. Isliye 403 ownership validation bahut jagah enforce hoti hai.

### 19.3 Idempotency

Client same create request dobara bheje to duplicate data create na ho. Yeh especially job creation me important hai.

### 19.4 Event Versioning

Kafka topic names me `.v1` suffix ka matlab hai ki future schema evolution ke liye versioning already sochi gayi hai.

### 19.5 401 vs 403

Project ne deliberately Spring Security default behavior ko override kiya hai, taaki unauthenticated case me 401 aaye, aur authenticated-but-forbidden case me 403 aaye.

---

## 20. Agar Tumhe Is Project Me Naya Feature Add Karna Ho To Kis Service Me Jaaoge?

### Scenario 1: User registration/login related feature

`identity-service`

### Scenario 2: Job creation/update/cancel logic

`job-service`

### Scenario 3: Schedule calculation ya recurring timing

`scheduler-service`

### Scenario 4: Naya task type run karwana

`execution-service/handler/`

### Scenario 5: Notification ka naya dispatch mode

`notification-service/dispatch/`

### Scenario 6: Public API path ya route mapping change

`api-gateway/src/main/resources/application.yml`

### Scenario 7: Shared DTO, JWT, errors, topic names

`platform-commons`

---

## 21. Naye Developer Ya Agent Ke Liye Best Mental Model

Is project ko is tarah socho:

1. `identity-service` bolta hai "user kaun hai?"
2. `job-service` bolta hai "user kya chalana chahta hai?"
3. `scheduler-service` bolta hai "ab kaunsi cheez chalne ka time ho gaya?"
4. `execution-service` bolta hai "task actually chalao"
5. `notification-service` bolta hai "user ko batao kya hua"
6. `api-gateway` bolta hai "sab request mere through aayegi"
7. `platform-commons` bolta hai "shared language aur contracts ye rahenge"

Yeh mental model yaad rakhoge to project ka flow jaldi samajh aa jayega.

---

## 22. Final Summary

Chronos ek clean enterprise-style Java microservices project hai jisme:

1. API Gateway pattern hai.
2. Event-driven workflow hai.
3. Database-per-service design hai.
4. Transactional outbox hai.
5. Idempotent consumers aur idempotent job creation hai.
6. JWT-based stateless security hai.
7. Strong integration testing setup hai.

Project ko samajhne ka golden path hai:

1. Shared contracts (`platform-commons`)
2. Auth (`identity-service`)
3. Write path (`job-service`)
4. Async scheduling (`scheduler-service`)
5. Execution engine (`execution-service`)
6. User outcome store (`notification-service`)
7. Public entry point (`api-gateway`)

Is document ka purpose yeh tha ki tumhe sirf files ka naam na pata chale, balki unka role, placement, aur overall system me contribution bhi clearly samajh aaye.