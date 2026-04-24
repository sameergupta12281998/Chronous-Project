# Agentic Execution Design

## Execution Goal
Claude Opus 4.7 must execute the complete backend implementation autonomously as a Java 21, Spring Boot 3.x, Maven-based distributed microservices system. The implementation must include service decomposition, event contracts, persistence ownership, security, testing, validation, and final readiness checks without partial completion.

## Architecture Lock
1. The implementation must be microservices-based, not monolithic.
2. The required services are:
	- API Gateway
	- Identity Service
	- Job Management Service
	- Scheduler Service
	- Execution Service
	- Notification Service
3. Kafka is mandatory for internal event-driven workflows.
4. PostgreSQL database-per-service is mandatory.
5. Flyway, Spring Security, Actuator, structured logging, metrics, and tracing are mandatory.
6. Do not replace Maven, Java, Spring Boot, Kafka, or the microservices architecture with simpler monolithic alternatives.

## Execution Lifecycle
1. Read all documentation before implementation.
2. Build a requirement traceability map and a service responsibility matrix.
3. Define public REST contracts, internal event contracts, and data ownership before writing code.
4. Establish the Maven multi-service build plan.
5. Implement API Gateway and Identity Service first so security and routing foundations are stable.
6. Implement Job Management Service with external APIs, idempotency, and read-model ownership.
7. Implement Scheduler Service with recurrence logic, scheduling state, and due-job publication.
8. Implement Execution Service with handler registry, async workers, retries, and dead-letter behavior.
9. Implement Notification Service for terminal failure processing.
10. Implement transactional outbox or equivalent durable event publication pattern where state changes emit Kafka events.
11. Implement service-level observability, health checks, and graceful shutdown behavior.
12. Implement automated tests at unit, slice, contract, integration, and end-to-end levels.
13. Run the full Maven validation cycle for the entire workspace.
14. Resolve all failures and rerun validation until green.
15. Finalize only when all quality gates pass.

## Context Handling Strategy
1. Treat the checklist as the binding acceptance contract.
2. Maintain active maps for:
	- requirement to service mapping
	- REST contract ownership
	- event contract ownership
	- database ownership
	- validation evidence
3. Track assumptions explicitly and keep them minimal.
4. Re-check service boundaries before each major implementation phase.
5. Reject any implementation shortcut that collapses multiple bounded contexts into one deployable.

## Coding and Design Standards
1. Use clean or hexagonal architecture within each service.
2. Use constructor injection only; no field injection.
3. Keep DTOs, persistence models, and domain models clearly separated.
4. Keep services cohesive and avoid cyclic package or module dependencies.
5. Keep shared code minimal and cross-cutting only.
6. Use consistent exception handling and consistent API error contracts.
7. Use immutable event payloads where practical.
8. Use backward-compatible contract evolution rules.
9. Use externalized configuration and secret injection, never hard-coded secrets.

## Error Recovery Strategy
1. Classify failures as build, contract, schema, messaging, runtime, logic, or test failures.
2. Fix root causes, not symptoms.
3. If service boundaries become unclear, stop and restore the boundary before adding more behavior.
4. If asynchronous flows become unreliable, fix idempotency, outbox behavior, or event versioning before proceeding.
5. Re-run affected tests and relevant distributed regression checks after each fix.
6. If blocked by ambiguity, apply the least-risk assumption, document it, and continue.

## Validation Strategy
1. Validate each service independently first.
2. Validate gateway-exposed flows next.
3. Validate Kafka-backed cross-service workflows after that.
4. Validate retries, dead-letter behavior, and notification flow under repeated failure.
5. Validate duplicate prevention, eventual consistency, and stale-event rejection.
6. Validate Actuator health, metrics, and trace propagation.
7. Do not stop until checklist gates are fully green.
