# Testing Strategy (Planning Only)

## Testing Tooling Baseline
1. JUnit 5 for unit and integration execution.
2. Mockito for isolated service and policy testing.
3. Spring Boot Test per service.
4. MockMvc or WebTestClient for gateway and HTTP contract validation.
5. Testcontainers for PostgreSQL and Kafka.
6. Awaitility or equivalent polling assertions for eventual consistency checks.
7. Spring Cloud Contract or equivalent contract-testing approach for internal HTTP integrations where they exist.
8. Maven Surefire and Failsafe across the complete multi-service build.

## Unit Testing Plan
1. Domain state transitions in Job, Scheduler, Execution, and Notification services.
2. Recurrence calculation and next-run logic, including timezone-aware behavior.
3. Retry policy behavior:
   - attempt counting
   - backoff computation
   - retry exhaustion
4. Idempotency handling for external commands and internal event consumers.
5. Event mapping, event version validation, and stale-event rejection rules.
6. Authorization decisions for ownership checks.
7. Gateway filters, token parsing helpers, and correlation id propagation logic.
8. Notification routing and delivery strategy selection.

## Integration Testing Plan
1. Per-service controller-to-database persistence tests.
2. Gateway routing and security behavior through realistic HTTP calls.
3. Kafka producer and consumer integration including outbox publication flow.
4. Job create to scheduler consume to due-event publish flow.
5. Due-event consume to execution success flow.
6. Failure and retry path integration with controlled failing handlers.
7. Terminal failure to notification persistence flow.
8. Flyway migration verification during service startup.
9. Actuator health visibility and startup readiness for every service.

## Contract Testing Plan
1. Gateway-to-service HTTP contracts for all public routes.
2. Event contract compatibility between Job, Scheduler, Execution, and Notification services.
3. Error response contract consistency for auth, validation, and domain failures.
4. Backward-compatibility checks for versioned API and event evolution.

## End-to-End Testing Plan
1. Start the full distributed platform with all services, Kafka, and PostgreSQL instances.
2. Submit an immediate job through the gateway and verify final success state.
3. Submit a future one-time scheduled job and verify delayed execution.
4. Submit recurring jobs across all supported frequencies and verify at least one due execution per recurrence type.
5. Trigger a controlled execution failure and verify retries plus terminal failure notification.
6. Cancel a pending job and verify that stale due events do not execute it.
7. Reschedule a pending job and verify schedule recalculation and eventual execution.
8. Scale execution-service replicas and confirm there is no duplicate execution for the same logical attempt.

## Manual Testing Strategy
1. Boot the full distributed environment locally.
2. Verify health endpoints for Gateway, Identity, Job, Scheduler, Execution, and Notification services.
3. Execute the critical user journeys through the gateway only.
4. Simulate service restarts and confirm the system recovers without corrupting state.
5. Simulate broker delay or worker failure and verify retry safety and dead-letter behavior.
6. Confirm logs, correlation ids, metrics, and trace propagation across services.

## API Validation Approach
1. Validate gateway-exposed request and response schemas.
2. Validate success and error status codes.
3. Validate lifecycle semantics for create, cancel, reschedule, and status queries.
4. Validate eventual consistency through polling-based assertions where async processing is expected.
5. Validate idempotency using repeated create requests with the same idempotency key.
6. Validate security behavior for unauthenticated and cross-user scenarios.
7. Full Maven verification for the entire distributed workspace must pass before the project is considered complete.
