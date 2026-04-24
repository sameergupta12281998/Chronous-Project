# System Design (Abstract)

## Architecture Style
1. Chronos must be implemented as a domain-oriented, event-driven microservices system.
2. All client traffic must enter through a single API Gateway.
3. Public synchronous APIs are limited to edge-facing use cases.
4. Internal distributed workflows must use Kafka events and eventual consistency.
5. Each service must be independently deployable, independently testable, and independently observable.
6. Each service must own its own database schema and migration history.
7. Cross-service consistency must rely on domain events, transactional outbox, and idempotent consumers rather than distributed transactions.

## Service Landscape
1. API Gateway:
	- Edge routing
	- Authentication enforcement
	- Correlation id propagation
	- Rate-limiting and edge hardening baseline
2. Identity Service:
	- User registration and login
	- JWT issuance and user identity management
	- Security policies for role and ownership claims
3. Job Management Service:
	- External command and query API for jobs
	- Job metadata ownership
	- Job read model for lifecycle, schedule, and execution visibility
	- Idempotent create and reschedule behavior
4. Scheduler Service:
	- Recurrence computation and next-run planning
	- Due-job detection
	- Publication of due-job events
	- Reaction to job create, cancel, and reschedule events
5. Execution Service:
	- Distributed worker execution
	- Job handler registry and task dispatch
	- Execution state publishing
	- Retry orchestration, backoff handling, and dead-letter processing
6. Notification Service:
	- Consumption of terminal failure events
	- Notification persistence and delivery abstraction
	- Notification query support if exposed through the gateway

## Platform Components
1. Kafka as the internal event backbone.
2. PostgreSQL database per service.
3. Flyway migrations per service.
4. Spring Boot Actuator per service.
5. Micrometer metrics and OpenTelemetry-compatible tracing.
6. Containerized local orchestration for distributed end-to-end validation.

## Communication Model
1. External clients communicate only with the API Gateway.
2. The API Gateway routes to edge-exposed services and never contains business logic.
3. JWT validation should happen at the gateway and be enforceable at service boundaries.
4. Core workflow steps must use Kafka events:
	- job creation
	- scheduling
	- due dispatch
	- execution lifecycle
	- retry lifecycle
	- terminal failure notification
5. Synchronous service-to-service HTTP should be minimal and never form long hot-path dependency chains.

## Data Ownership Model
1. Identity Service owns user and credential data.
2. Job Management Service owns public job metadata and query-facing status models.
3. Scheduler Service owns schedule evaluation state.
4. Execution Service owns execution attempts, retry metadata, worker outcomes, and dead-letter state.
5. Notification Service owns notification delivery history.
6. No service may read or write another service's database directly.

## Core Distributed Flow: Job Creation
1. Client authenticates through the gateway and obtains a JWT.
2. Client submits a create-job request through the gateway.
3. Job Management Service validates ownership, idempotency, schedule intent, and task type.
4. Job Management Service persists job state and writes an outbox event.
5. The outbox event is published to Kafka as `JobCreated`.
6. Scheduler Service consumes `JobCreated`, computes next execution time, stores schedule state, and later publishes `JobDue` when execution becomes eligible.

## Core Distributed Flow: Execution and Retry
1. Execution Service consumes `JobDue`.
2. Execution Service resolves the appropriate handler and starts execution.
3. Execution Service publishes `JobExecutionStarted`.
4. On success, it publishes `JobExecutionSucceeded` and updates its own execution store.
5. On failure, it evaluates retry policy.
6. If retry is allowed, it publishes `JobRetryScheduled` and schedules the next attempt.
7. If retries are exhausted, it publishes `JobTerminalFailure` and optionally routes the failed message to a dead-letter topic.
8. Job Management Service updates the query-facing lifecycle state from execution events.
9. Notification Service consumes `JobTerminalFailure`, persists notification state, and dispatches the configured delivery mechanism.

## Core Distributed Flow: Cancel and Reschedule
1. Client issues cancel or reschedule through the gateway.
2. Job Management Service validates ownership and current job state.
3. Job Management Service emits `JobCancelled` or `JobRescheduled` through an outbox-backed event.
4. Scheduler Service updates schedule state accordingly.
5. Execution Service ignores stale due events by validating current lifecycle state and event version.

## Design Decisions and Industrial Best Practices
1. Use microservices, not a monolith, because scheduling, execution, notifications, and identity scale differently and have distinct failure domains.
2. Use database-per-service to preserve service autonomy and prevent tight coupling.
3. Use Kafka for asynchronous decoupling and horizontal scale.
4. Use transactional outbox to ensure durable state change plus event publication.
5. Use idempotent consumers and deduplication guards to make retries safe.
6. Use versioned REST contracts and versioned event schemas.
7. Use structured JSON logging, correlation ids, trace propagation, and service-level metrics.
8. Use resilience controls for any synchronous calls: timeouts, retries with limits, and circuit-breaking where necessary.
9. Use graceful shutdown, readiness probes, and bounded worker pools to avoid in-flight corruption during deployments.
10. Keep shared libraries minimal and cross-cutting only; do not centralize business logic in a shared module.
11. Prefer constructor injection, immutable DTOs where practical, explicit domain models, and clean package boundaries inside every service.
12. Avoid service discovery frameworks that are unnecessary in modern container orchestration; prefer platform-native service networking when deploying.
