# Project Definition

## Problem Statement
Chronos is a backend-only distributed job scheduling platform to be implemented as a set of Java 21, Spring Boot 3.x microservices built with Maven. The platform must accept job commands through a single edge entry point, orchestrate one-time and recurring schedules, dispatch executions through asynchronous distributed workflows, manage retries and terminal failures, and expose operational visibility suitable for production-grade deployment.

## Required Technology Baseline
1. Java 21 LTS is mandatory.
2. Spring Boot 3.x is mandatory for every service.
3. Maven is the only allowed build and dependency management tool.
4. The system must be implemented as independently deployable microservices, not as a monolith.
5. Spring Cloud Gateway is the preferred edge gateway.
6. PostgreSQL must be used with database-per-service ownership.
7. Apache Kafka must be used as the internal event backbone for distributed workflows.
8. Spring Security with JWT-based authentication and authorization is required.
9. Flyway-based database migrations are required per service.
10. Spring Boot Actuator, Micrometer metrics, structured logging, and OpenTelemetry-compatible tracing are mandatory operational standards.

## Engineering Principles
1. Domain-oriented microservices with explicit bounded contexts.
2. Database per service and no cross-service table access.
3. Event-driven choreography for asynchronous workflows.
4. Clean or hexagonal architecture inside each service.
5. API-first contracts with backward-compatible evolution.
6. Transactional outbox, idempotent consumers, and retry-safe message handling.
7. Observability, security, and resilience treated as first-class concerns.
8. Twelve-factor configuration, secret externalization, and secure defaults.

## Target Users
1. Backend engineers integrating scheduled execution into products.
2. Platform teams offering job scheduling as an internal platform capability.
3. Operations teams running distributed periodic maintenance and operational tasks.
4. API clients and future frontend systems consuming gateway-exposed job APIs.

## Core Use Cases
1. Submit a job for immediate execution through the API Gateway.
2. Submit a one-time job for future execution.
3. Submit recurring jobs using hourly, daily, weekly, or monthly schedules.
4. View job status, execution history, retry history, and next run information.
5. Cancel pending jobs or disable future occurrences of recurring jobs.
6. Reschedule a pending or recurring job before its next execution.
7. Auto-retry failed jobs with policy-driven behavior across distributed workers.
8. Notify users after retry exhaustion.
9. Track health, backlog, throughput, and failure behavior across services.

## Assumptions
1. Jobs are predefined task types executed by trusted Spring-managed worker handlers rather than arbitrary user-uploaded code.
2. Public clients interact only through the gateway; internal services are not directly exposed to end users.
3. Services communicate asynchronously through Kafka for core job lifecycle workflows and use synchronous HTTP only when strictly necessary.
4. Each microservice owns its schema, migrations, and persistence rules.
5. Time is stored in UTC, with timezone-aware schedule intent preserved where required.
6. Idempotency is mandatory for both external create operations and internal event consumption.
7. Notification delivery may start with persistent notification records plus a pluggable transport adapter.
8. Local development and validation must support a realistic distributed environment, typically via containerized local orchestration.

## Scope Boundaries
In scope:
1. API Gateway, Identity, Job Management, Scheduler, Execution, and Notification microservices.
2. Kafka-based distributed workflows for job lifecycle events.
3. Database-per-service persistence and per-service migrations.
4. Retry orchestration, dead-letter handling, terminal failure handling, and notification recording.
5. Logging, metrics, tracing, health, and readiness visibility across all services.
6. Secure service interactions, ownership-based authorization, and Maven-driven validation workflow.

Out of scope:
1. Frontend implementation.
2. Billing, quotas, and monetization.
3. Arbitrary untrusted code execution sandboxing.
4. Multi-region active-active deployment design.
5. Service mesh implementation, if not required to complete the MVP.
6. Replacing Maven, Java, Spring Boot, Kafka, or the microservices architecture with a monolithic alternative.
