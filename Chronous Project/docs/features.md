# Feature Planning

## Mandatory Technology Baseline
1. Java 21 and Spring Boot 3.x for every service.
2. Maven for the complete multi-service build, test, and verification lifecycle.
3. Spring Cloud Gateway as the edge ingress layer.
4. PostgreSQL with strict database-per-service ownership.
5. Apache Kafka for internal event-driven communication.
6. Spring Security, Spring Validation, Spring Data JPA, Flyway, and Spring Boot Actuator as required platform capabilities.
7. Distributed tracing, structured logging, and metrics exposure as mandatory non-functional features.

## MVP Features
1. Edge and access layer:
   - Single API Gateway for all external traffic
   - JWT authentication and authorization enforcement
   - Correlation id propagation across requests and events
   - Basic gateway routing, request validation, and secure edge behavior
2. Identity service:
   - User registration and login
   - JWT issuance and validation rules
   - Ownership-aware security model for downstream services
3. Job Management service:
   - Create job
   - Get job by id
   - List jobs with filters
   - Cancel job
   - Reschedule job
   - Idempotency protection for create operations
   - External read model for job and execution status queries
4. Scheduler service:
   - Hourly, daily, weekly, and monthly recurrence support
   - Due-job computation and partition-safe scheduling
   - Publish due-job events to Kafka
   - Handle schedule updates and cancellations through events
5. Execution service:
   - Consume due-job events from Kafka
   - Resolve task handler by job type
   - Execute work asynchronously across distributed workers
   - Emit execution lifecycle events
   - Apply bounded retry policy with controlled backoff
   - Route unrecoverable failures to terminal-failure flow and dead-letter handling where appropriate
6. Notification service:
   - Consume terminal-failure events
   - Persist notification history
   - Provide pluggable notification delivery adapters
7. Observability and operations:
   - Health and readiness endpoints on every service
   - Structured JSON logging with correlation ids and trace identifiers
   - Metrics for queue lag, job states, success rate, retry volume, and failure rate
   - OpenAPI documentation for public APIs exposed through the gateway

## Future or Advanced Features
1. Cron expression support with schedule preview.
2. Multi-tenant RBAC and organizational boundaries.
3. Workflow chains, DAGs, and dependency-aware orchestration.
4. Priority queues, fairness policies, and workload partitioning.
5. Pause and resume recurring schedules.
6. Dedicated analytics or reporting service for historical trends.
7. Real email, webhook, SMS, or push notification transport adapters.
8. Geo-redundant deployment and disaster recovery strategy.
9. Policy-driven routing of job types to specialized execution clusters.

## API-Level Conceptual Breakdown
1. Gateway-exposed auth APIs:
   - `POST /api/v1/auth/register`
   - `POST /api/v1/auth/login`
2. Gateway-exposed job APIs:
   - `POST /api/v1/jobs`
   - `GET /api/v1/jobs/{jobId}`
   - `GET /api/v1/jobs`
   - `POST /api/v1/jobs/{jobId}/cancel`
   - `POST /api/v1/jobs/{jobId}/reschedule`
3. Gateway-exposed execution APIs:
   - `GET /api/v1/jobs/{jobId}/executions`
   - `GET /api/v1/executions/{executionId}`
4. Gateway-exposed notification APIs:
   - `GET /api/v1/notifications`
   - `GET /api/v1/notifications/{notificationId}`
5. Operations APIs:
   - `GET /actuator/health` on each service
   - `GET /actuator/info` on each service
   - Metrics endpoint exposure according to environment policy

## Internal Event Contract Breakdown
1. `JobCreated`
2. `JobCancelled`
3. `JobRescheduled`
4. `JobDue`
5. `JobExecutionStarted`
6. `JobExecutionSucceeded`
7. `JobExecutionFailed`
8. `JobRetryScheduled`
9. `JobTerminalFailure`
10. `NotificationDispatched`
