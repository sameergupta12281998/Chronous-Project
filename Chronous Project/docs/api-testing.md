# API Testing Plan

## Postman Collection Structure
1. Collection folders:
   - Gateway Auth
   - Gateway Jobs
   - Gateway Executions
   - Gateway Notifications
   - Ops Gateway Health
   - Ops Service Health
2. Shared variables:
   - Gateway base URL
   - Identity service health URL
   - Job service health URL
   - Scheduler service health URL
   - Execution service health URL
   - Notification service health URL
   - Access token
   - Primary user id
   - Secondary user id
   - Idempotency key
   - Correlation id
   - Job id
   - Execution id
   - Notification id
3. Pre-request setup:
   - Register or log in a user
   - Acquire JWT through the gateway
   - Generate correlation id and idempotency key values
4. Test scripts:
   - Status assertions
   - Contract assertions
   - Correlation id header assertions
   - Eventual-consistency polling assertions
   - Authorization boundary checks
   - Retry and terminal-failure visibility checks

## Endpoint Coverage Plan
1. Gateway Auth:
   - `POST /api/v1/auth/register`
   - `POST /api/v1/auth/login`
   - Invalid credentials and duplicate registration behavior
2. Gateway Jobs:
   - `POST /api/v1/jobs` for immediate execution
   - `POST /api/v1/jobs` for future one-time scheduling
   - `POST /api/v1/jobs` for recurring scheduling
   - `GET /api/v1/jobs/{jobId}`
   - `GET /api/v1/jobs` with filters
   - `POST /api/v1/jobs/{jobId}/cancel`
   - `POST /api/v1/jobs/{jobId}/reschedule`
3. Gateway Executions:
   - `GET /api/v1/jobs/{jobId}/executions`
   - `GET /api/v1/executions/{executionId}`
4. Gateway Notifications:
   - `GET /api/v1/notifications`
   - `GET /api/v1/notifications/{notificationId}`
5. Operations:
   - `GET /actuator/health` on Gateway, Identity, Job, Scheduler, Execution, and Notification services
   - `GET /actuator/info` where enabled

## Request and Response Expectations
1. All protected gateway endpoints reject missing or invalid JWTs.
2. Create-job requests return stable resource identifiers and an initial asynchronous lifecycle state.
3. Async flows are validated through polling rather than assuming immediate terminal state.
4. Repeated create-job requests with the same idempotency key must not create duplicate logical jobs.
5. Invalid payloads return consistent validation errors.
6. Invalid state transitions return deterministic business-rule errors.
7. Cross-user access returns forbidden responses.
8. Execution history returns attempt count, failure reason, and retry metadata.
9. Terminal failures expose notification linkage and delivery state.
10. Health endpoints for every service must report a ready and healthy state once the platform is up.
