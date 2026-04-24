# Context Engineering

## Read Order
1. checklist.md
2. project.md
3. features.md
4. architecture.md
5. testing.md
6. api-testing.md
7. agent.md
8. master-prompt.md

## Technology and Architecture Lock After Reading
1. Java 21 LTS is mandatory.
2. Spring Boot 3.x is mandatory.
3. Maven is mandatory.
4. Distributed microservices architecture is mandatory.
5. API Gateway, Identity, Job Management, Scheduler, Execution, and Notification services are mandatory architectural units.
6. Kafka is mandatory for internal event workflows.
7. PostgreSQL database-per-service ownership is mandatory.
8. Flyway, Spring Security, Actuator, structured logging, metrics, and tracing are mandatory.
9. The implementation must not collapse into a monolith or a shared-database system.

## Priority Model
1. Highest: checklist acceptance criteria, architecture lock, service boundaries, and data ownership.
2. High: MVP features, distributed workflow rules, and event contracts.
3. Medium: testing, contract validation, and operational readiness.
4. Supporting: agent execution mechanics.

## Working Memory Strategy
1. Maintain active context blocks for:
   - requirement traceability
   - service catalog and responsibilities
   - REST contract ownership
   - event contract ownership
   - database ownership map
   - risk register
   - validation status
2. Update context after each major service or platform milestone.
3. Keep unresolved issues explicit and actionable.
4. Produce a final matrix mapping each requirement to service implementation, tests, and runtime evidence.

## Drift and Hallucination Prevention
1. Never invent requirements outside the docs.
2. Never drift away from Java, Spring Boot, Maven, Kafka, or microservices.
3. Never merge multiple bounded contexts into one deployable for convenience.
4. Never introduce a shared database or direct cross-service SQL access.
5. Never rely on distributed transactions for cross-service consistency.
6. Prefer outbox-driven event publication, idempotent consumption, and eventual consistency.
7. Keep synchronous service-to-service dependencies minimal and avoid deep call chains.
8. Cross-check every major decision against project, architecture, and checklist.
9. If data is missing, make the smallest defensible assumption and document it.
10. Never mark complete without a full checklist pass.
