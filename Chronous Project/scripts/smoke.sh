#!/usr/bin/env bash
# Chronos end-to-end smoke test.
# Requires curl + jq. Exit code 0 on success.
set -euo pipefail

BASE="${BASE:-http://localhost:8080}"
USER_NAME="${USER_NAME:-smoke-$(date +%s)}"
EMAIL="${EMAIL:-$USER_NAME@example.test}"
PASSWORD="${PASSWORD:-S3cret!Pass}"

red()   { printf "\033[31m%s\033[0m\n" "$*"; }
green() { printf "\033[32m%s\033[0m\n" "$*"; }
step()  { printf "\n\033[36m== %s ==\033[0m\n" "$*"; }
fail()  { red "FAIL: $*"; exit 1; }

assert_status() {
  local desc=$1 expected=$2 actual=$3
  if [ "$expected" = "$actual" ]; then
    green "  ✓ $desc → $actual"
  else
    fail "$desc expected $expected got $actual"
  fi
}

future_iso() {
  # +seconds, RFC3339 / ISO 8601 in UTC. Cross-platform.
  local secs=$1
  if date -u -v+${secs}S +%Y-%m-%dT%H:%M:%SZ >/dev/null 2>&1; then
    date -u -v+${secs}S +%Y-%m-%dT%H:%M:%SZ           # macOS
  else
    date -u -d "+$secs seconds" +%FT%TZ                # GNU/Linux
  fi
}

step "Health: gateway"
HEALTH=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/actuator/health")
assert_status "gateway health" "200" "$HEALTH"

step "Register user $USER_NAME"
REG=$(curl -s -o /tmp/chronos_reg -w '%{http_code}' -X POST "$BASE/api/v1/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER_NAME\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
assert_status "register" "201" "$REG"
REGISTERED_ID=$(jq -r .id </tmp/chronos_reg)
[ -n "$REGISTERED_ID" ] && [ "$REGISTERED_ID" != "null" ] || fail "no registered user id"
green "  user_id=$REGISTERED_ID"

step "Login"
LOG=$(curl -s -o /tmp/chronos_login -w '%{http_code}' -X POST "$BASE/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER_NAME\",\"password\":\"$PASSWORD\"}")
assert_status "login" "200" "$LOG"
TOKEN=$(jq -r .accessToken </tmp/chronos_login)

step "Create job (Idempotency-Key)"
SCHEDULE=$(future_iso 5)
KEY="smoke-$(date +%s)-$RANDOM"
PAYLOAD="{\"name\":\"smoke\",\"taskType\":\"LOG\",\"payload\":\"{}\",\"scheduleType\":\"ONE_TIME\",\"scheduledAt\":\"$SCHEDULE\",\"maxAttempts\":3}"
C1=$(curl -s -o /tmp/chronos_job1 -w '%{http_code}' -X POST "$BASE/api/v1/jobs" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $KEY" -d "$PAYLOAD")
assert_status "first create" "201" "$C1"
JOB_ID=$(jq -r .id </tmp/chronos_job1)
green "  job_id=$JOB_ID"

step "Replay same Idempotency-Key"
C2=$(curl -s -o /tmp/chronos_job2 -w '%{http_code}' -X POST "$BASE/api/v1/jobs" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $KEY" -d "$PAYLOAD")
assert_status "replay" "200" "$C2"
ID2=$(jq -r .id </tmp/chronos_job2)
[ "$JOB_ID" = "$ID2" ] && green "  ✓ same id" || fail "idempotency replay returned different id $ID2 vs $JOB_ID"

step "Validation: scheduledAt in past → 400"
BAD=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/v1/jobs" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"bad","taskType":"LOG","payload":"{}","scheduleType":"ONE_TIME","scheduledAt":"2000-01-01T00:00:00Z","maxAttempts":3}')
assert_status "past-scheduledAt" "400" "$BAD"

step "Auth: no token → 401"
NA=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/v1/jobs/anything")
assert_status "no token" "401" "$NA"

step "GET job by id"
GJ=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/v1/jobs/$JOB_ID" -H "Authorization: Bearer $TOKEN")
assert_status "get job" "200" "$GJ"

step "Wait for execution to fire (8s)"
sleep 8

step "List executions for job"
EX=$(curl -s -o /tmp/chronos_ex -w '%{http_code}' "$BASE/api/v1/jobs/$JOB_ID/executions" -H "Authorization: Bearer $TOKEN")
assert_status "list executions" "200" "$EX"
EX_COUNT=$(jq '.items | length' </tmp/chronos_ex)
[ "$EX_COUNT" -ge 1 ] && green "  ✓ executions=$EX_COUNT" || fail "expected at least 1 execution, got $EX_COUNT"

step "List notifications"
NOTIF=$(curl -s -o /tmp/chronos_notif -w '%{http_code}' "$BASE/api/v1/notifications" -H "Authorization: Bearer $TOKEN")
assert_status "list notifications" "200" "$NOTIF"
NOTIF_COUNT=$(jq '.items | length' </tmp/chronos_notif)
[ "$NOTIF_COUNT" -ge 1 ] && green "  ✓ notifications=$NOTIF_COUNT" || red "  ! 0 notifications (may be timing-related; not failing)"

step "Cross-owner: 403"
OTHER_USER="smoke-other-$(date +%s)"
OTHER_EMAIL="$OTHER_USER@example.test"
curl -s -o /dev/null -X POST "$BASE/api/v1/auth/register" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$OTHER_USER\",\"email\":\"$OTHER_EMAIL\",\"password\":\"$PASSWORD\"}"
T2=$(curl -s -X POST "$BASE/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$OTHER_USER\",\"password\":\"$PASSWORD\"}" | jq -r .accessToken)
F=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/v1/jobs/$JOB_ID" -H "Authorization: Bearer $T2")
assert_status "cross-owner forbidden" "403" "$F"

step "Cancel via POST alias"
CN=$(curl -s -o /tmp/chronos_cancel -w '%{http_code}' -X POST "$BASE/api/v1/jobs/$JOB_ID/cancel" -H "Authorization: Bearer $TOKEN")
assert_status "cancel" "200" "$CN"
ST=$(jq -r .status </tmp/chronos_cancel)
[ "$ST" = "CANCELLED" ] && green "  ✓ status=$ST" || fail "expected CANCELLED, got $ST"

green "\nSMOKE OK"
