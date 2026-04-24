# Chronos Testing Guide

This guide tells you exactly **how to test every feature** of Chronos – automated and manual. Follow top-to-bottom; each section is self-contained.

---

## 0. Prerequisites

- **JDK 21+** (`java --version`)
- **Maven 3.9+** (`mvn -v`)
- **Docker + Docker Compose** (only for the manual / integration sections)
- Optional: `jq`, `curl`, [Postman](https://www.postman.com/downloads/)

```bash
cd "Chronous Project"
```

---

## 1. Run the full automated test suite

This is the **fastest signal** — uses H2 in PostgreSQL mode + embedded Kafka, no external infra.

```bash
mvn clean verify
```

Expected: `BUILD SUCCESS`, **37 tests, 0 failures, 0 errors**.

Per-module run:

```bash
mvn -pl identity-service     -am verify    # 4 tests
mvn -pl job-service          -am verify    # 8 tests
mvn -pl scheduler-service    -am verify    # 4 tests
mvn -pl execution-service    -am verify    # 9 tests
mvn -pl notification-service -am verify    # 4 tests
mvn -pl api-gateway          -am verify    # 5 tests
mvn -pl platform-commons     -am verify    # 3 tests
```

If a single test fails, run it isolated:

```bash
mvn -pl job-service test -Dtest=JobControllerIT#idempotencyKeyReturnsSameJobOnRepeat
```

---

## 2. Bring the full stack up locally

```bash
# Build Docker images for all services and start Postgres + Kafka + 7 services
docker compose up --build -d

# Tail logs (Ctrl+C to stop tailing — services keep running)
docker compose logs -f api-gateway job-service execution-service notification-service
```

Verify everything is healthy:

```bash
for s in identity job scheduler execution notification; do
  echo -n "$s: "; curl -s http://localhost:80$( case $s in identity) echo 81;; job) echo 82;; scheduler) echo 83;; execution) echo 84;; notification) echo 85;; esac )/actuator/health
  echo
done
curl -s http://localhost:8080/actuator/health
```

All should return `{"status":"UP"}`.

Tear down:
```bash
docker compose down -v   # -v wipes volumes (Postgres data, Kafka data)
```

---

## 3. End-to-end happy-path manual flow (curl)

Set base URL once:

```bash
export BASE=http://localhost:8080
```

### 3.1 Register a user

```bash
curl -sX POST "$BASE/api/v1/auth/register" \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","email":"alice@example.com","password":"S3cret!Pass"}' | jq
```

Returns `UserResponse{id, username, email, createdAt}`. Capture the token with login:

```bash
export TOKEN=$(curl -sX POST "$BASE/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"S3cret!Pass"}' | jq -r .accessToken)
echo "$TOKEN" | head -c 40 ; echo "..."
```

### 3.2 Create a one-off job (with idempotency)

```bash
SCHEDULE=$(date -u -v+5S +%Y-%m-%dT%H:%M:%SZ)   # macOS; on Linux use: date -u -d '+5 seconds' +%FT%TZ
JOB=$(curl -sX POST "$BASE/api/v1/jobs" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: my-first-job-2026-04-22" \
  -d "{\"name\":\"hello\",\"taskType\":\"EMAIL\",\"payload\":\"{\\\"to\\\":\\\"a@b.c\\\"}\",\"scheduleType\":\"ONE_TIME\",\"scheduledAt\":\"$SCHEDULE\",\"maxAttempts\":3}")
echo "$JOB" | jq
export JOB_ID=$(echo "$JOB" | jq -r .id)
```

Repeat the **same call with the same idempotency key** → response is `200 OK` (not `201`) and the body is the **same** job.

### 3.3 Wait for execution & list executions

```bash
sleep 8
curl -s "$BASE/api/v1/jobs/$JOB_ID/executions" \
  -H "Authorization: Bearer $TOKEN" | jq
```

You should see at least one execution with `status: "SUCCEEDED"`.

### 3.4 Get the notification

```bash
curl -s "$BASE/api/v1/notifications" \
  -H "Authorization: Bearer $TOKEN" | jq
```

Returns paged list including a `type: "ExecutionSucceeded"` row tied to your `JOB_ID`.

### 3.5 Cancel & reschedule

```bash
# Cancel via POST alias
curl -sX POST "$BASE/api/v1/jobs/$JOB_ID/cancel" -H "Authorization: Bearer $TOKEN" | jq

# Or reschedule
NEW=$(date -u -v+60S +%Y-%m-%dT%H:%M:%SZ)
curl -sX POST "$BASE/api/v1/jobs/$JOB_ID/reschedule" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"scheduledAt\":\"$NEW\"}" | jq
```

---

## 4. Negative-path manual checks

### 4.1 401 — no token / bad token

```bash
curl -sw "\nHTTP %{http_code}\n" "$BASE/api/v1/jobs"                               # → 401
curl -sw "\nHTTP %{http_code}\n" "$BASE/api/v1/jobs" -H "Authorization: Bearer xx" # → 401
```

### 4.2 400 — validation

```bash
# scheduledAt in past
curl -sw "\nHTTP %{http_code}\n" -X POST "$BASE/api/v1/jobs" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"x","taskType":"EMAIL","payload":"{}","scheduleType":"ONE_TIME","scheduledAt":"2000-01-01T00:00:00Z","maxAttempts":3}'

# RECURRING without recurrenceFrequency
curl -sw "\nHTTP %{http_code}\n" -X POST "$BASE/api/v1/jobs" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"name\":\"x\",\"taskType\":\"EMAIL\",\"payload\":\"{}\",\"scheduleType\":\"RECURRING\",\"scheduledAt\":\"$(date -u -v+60S +%Y-%m-%dT%H:%M:%SZ)\",\"maxAttempts\":3}"
```

### 4.3 403 — cross-owner

```bash
# Register a second user, get a new token, then try to read alice's job
curl -sX POST "$BASE/api/v1/auth/register" -H 'Content-Type: application/json' \
  -d '{"username":"bob","email":"bob@example.com","password":"S3cret!Pass"}' >/dev/null
T2=$(curl -sX POST "$BASE/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"bob","password":"S3cret!Pass"}' | jq -r .accessToken)
curl -sw "\nHTTP %{http_code}\n" "$BASE/api/v1/jobs/$JOB_ID" -H "Authorization: Bearer $T2"   # → 403
```

### 4.4 Idempotent replay returns same id

```bash
KEY=test-$(date +%s)
SCHEDULE=$(date -u -v+30S +%Y-%m-%dT%H:%M:%SZ)
ID1=$(curl -sX POST "$BASE/api/v1/jobs" -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -H "Idempotency-Key: $KEY" \
  -d "{\"name\":\"i\",\"taskType\":\"LOG\",\"payload\":\"{}\",\"scheduleType\":\"ONE_TIME\",\"scheduledAt\":\"$SCHEDULE\",\"maxAttempts\":3}" | jq -r .id)
ID2=$(curl -sX POST "$BASE/api/v1/jobs" -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -H "Idempotency-Key: $KEY" \
  -d "{\"name\":\"i\",\"taskType\":\"LOG\",\"payload\":\"{}\",\"scheduleType\":\"ONE_TIME\",\"scheduledAt\":\"$SCHEDULE\",\"maxAttempts\":3}" | jq -r .id)
[ "$ID1" = "$ID2" ] && echo OK_idempotent || echo FAIL
```

### 4.5 Terminal failure path

A job whose handler always throws will appear as `status: "FAILED"` for `attempt < maxAttempts`, then `status: "TERMINAL_FAILED"` once `attempt > maxAttempts`. The notification will include the error message. Use `taskType: "WEBHOOK"` pointed at an unreachable URL to reproduce.

---

## 5. Postman

A ready-to-import collection lives under [postman/](../postman/):

- **`Chronos.postman_collection.json`** — every endpoint, with auto-extraction scripts and assertions
- **`Chronos.postman_environment.json`** — variables (`base_url`, `username`, `password`, `access_token`, `job_id`, `idempotency_key`, …)

Import both, choose the environment, then run the **`Happy Path`** folder via Collection Runner. Exit code is non-zero if any assertion fails (works in `newman` too):

```bash
npm install -g newman
newman run postman/Chronos.postman_collection.json -e postman/Chronos.postman_environment.json
```

---

## 6. Smoke script (CI / one-shot validation)

```bash
./scripts/smoke.sh                # uses BASE=http://localhost:8080
BASE=http://staging.example.com ./scripts/smoke.sh
```

Exit code 0 = all assertions passed. The script asserts: register, login, create-job (with idempotency), idempotent replay, GET execution list, GET notification list, cancel, and 401/403 negative cases.

---

## 7. Where to look in logs

Every log line includes `corr=<UUID>`. Grep across services to follow a single request:

```bash
docker compose logs --no-color | grep "corr=4d2c1e9a-..."
```

Each event in Kafka also carries `correlationId` in its `EventEnvelope`, so the same id flows through:

`api-gateway → job-service → outbox → Kafka → scheduler-service → Kafka → execution-service → Kafka → notification-service`.

---

## 8. Known good baselines

| Check | Command | Expected |
|---|---|---|
| Compile | `mvn -DskipTests package` | BUILD SUCCESS |
| All tests | `mvn clean verify` | 37 tests, 0 failures |
| Compose validates | `docker compose config --quiet` | exit 0 |
| Gateway health | `curl -s localhost:8080/actuator/health` | `{"status":"UP"}` |
| Smoke | `./scripts/smoke.sh` | `SMOKE OK` last line |
