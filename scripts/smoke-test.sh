#!/usr/bin/env bash
# End-to-end smoke test against a RUNNING MiniQueue: local, or the Render deployment.
# It checks the things a deploy can break: health, the deployed commit, authentication,
# scopes, publish -> consume -> ack, revocation, metrics, CORS and the dashboard's routes.
#
# Usage:
#   BACKEND_URL=https://miniqueue-backend.onrender.com \
#   DASHBOARD_URL=https://miniqueue.onrender.com \
#   ADMIN_KEY=mq_... \
#   [EXPECTED_COMMIT=<git sha>] \
#   ./scripts/smoke-test.sh
#
# ADMIN_KEY is an admin-scoped API key created on the dashboard's Clients page.
# Needs curl and jq (both preinstalled on GitHub's ubuntu runners).
#
# Safe to run repeatedly against production: it reuses one topic and one queue, drains
# leftovers first, and revokes the temporary client it creates at the end.
set -euo pipefail

: "${BACKEND_URL:?Set BACKEND_URL, e.g. https://miniqueue-backend.onrender.com}"
: "${ADMIN_KEY:?Set ADMIN_KEY to an admin-scoped API key}"
DASHBOARD_URL="${DASHBOARD_URL:-}"
EXPECTED_COMMIT="${EXPECTED_COMMIT:-}"

B="${BACKEND_URL%/}"
TOPIC="smoke.topic"
QUEUE="smoke-queue"
CLIENT="smoke-test"

PASSED=0
BODY="$(mktemp)"
trap 'rm -f "$BODY"' EXIT

ok()   { PASSED=$((PASSED + 1)); echo "  ok    $1"; }
fail() { echo "  FAIL  $1" >&2; exit 1; }

# call METHOD URL [curl args...]: prints the HTTP status, saves the body to $BODY.
# A connection failure prints 000 instead of aborting, so callers can retry.
call() {
    local method="$1" url="$2"
    shift 2
    curl -sS -o "$BODY" -w '%{http_code}' -X "$method" "$url" "$@" || true
}

# expect STATUS DESCRIPTION METHOD URL [curl args...]
expect() {
    local want="$1" what="$2"
    shift 2
    local got
    got="$(call "$@")"
    if [[ "$got" == "$want" ]]; then
        ok "$what ($got)"
    else
        fail "$what: expected HTTP $want, got $got. Body: $(head -c 300 "$BODY")"
    fi
}

ADMIN=(-H "Authorization: Bearer $ADMIN_KEY")
JSON=(-H "Content-Type: application/json")

echo "MiniQueue smoke test against $B"

echo "1. Health"
# Render's free tier sleeps when idle and takes up to a minute to wake: retry for 10 minutes.
for attempt in $(seq 1 40); do
    if [[ "$(call GET "$B/actuator/health")" == 200 ]] && jq -e '.status == "UP"' "$BODY" >/dev/null 2>&1; then
        break
    fi
    [[ "$attempt" == 40 ]] && fail "the backend did not report UP within 10 minutes"
    sleep 15
done
ok "health is UP"

if [[ -n "$EXPECTED_COMMIT" ]]; then
    echo "2. Deployed version"
    # Render reports the deployed commit through /actuator/info. Wait until the new deploy is live.
    running=""
    for attempt in $(seq 1 60); do
        call GET "$B/actuator/info" >/dev/null
        running="$(jq -r '.commit // empty' "$BODY" 2>/dev/null || true)"
        [[ "$running" == "$EXPECTED_COMMIT" ]] && break
        [[ "$attempt" == 60 ]] && fail "running commit is '$running', expected $EXPECTED_COMMIT after 15 minutes"
        sleep 15
    done
    ok "running commit $EXPECTED_COMMIT"
fi

echo "3. Authentication"
expect 401 "no credentials are refused" GET "$B/api/v1/topics"
expect 401 "a made-up key is refused" GET "$B/api/v1/topics" -H "Authorization: Bearer mq_never-issued-0123456789abcdefghijklmnop"
expect 200 "the admin key is accepted" GET "$B/api/v1/auth/me" "${ADMIN[@]}"
redirect="$(curl -sS -o /dev/null -w '%{redirect_url}' "$B/api/v1/auth/github/login" || true)"
if [[ "$redirect" == https://github.com/login/oauth/authorize* ]]; then
    ok "GitHub sign-in redirects to GitHub"
else
    fail "GitHub sign-in redirected to '$redirect' (is GITHUB_CLIENT_ID set?)"
fi

echo "4. Setup (topic, queue, temporary client)"
code="$(call POST "$B/api/v1/topics" "${ADMIN[@]}" "${JSON[@]}" -d "{\"name\":\"$TOPIC\"}")"
if [[ "$code" == 200 || "$code" == 409 ]]; then ok "topic $TOPIC exists"; else fail "create topic returned $code"; fi
code="$(call POST "$B/api/v1/queues" "${ADMIN[@]}" "${JSON[@]}" -d "{\"name\":\"$QUEUE\",\"topic_name\":\"$TOPIC\"}")"
if [[ "$code" == 200 || "$code" == 409 ]]; then ok "queue $QUEUE exists"; else fail "create queue returned $code"; fi

code="$(call POST "$B/api/v1/clients" "${ADMIN[@]}" "${JSON[@]}" \
    -d "{\"name\":\"$CLIENT\",\"scopes\":[\"publish:$TOPIC\",\"consume:$QUEUE\"]}")"
if [[ "$code" == 409 ]]; then
    # A previous run died before revoking it: take it over with a fresh key.
    code="$(call POST "$B/api/v1/clients/$CLIENT/rotate" "${ADMIN[@]}")"
fi
[[ "$code" == 200 ]] || fail "could not register the smoke client: $code $(head -c 300 "$BODY")"
KEY="$(jq -r '.response_body.api_key' "$BODY")"
CLIENT_AUTH=(-H "Authorization: Bearer $KEY")
ok "registered client $CLIENT with scopes publish:$TOPIC consume:$QUEUE"

# Leftovers from a run that failed half-way would confuse the checks below.
for _ in $(seq 1 50); do
    code="$(call POST "$B/api/v1/queues/$QUEUE/consume" "${CLIENT_AUTH[@]}")"
    [[ "$code" == 204 ]] && break
    [[ "$code" == 200 ]] || fail "draining the queue: consume returned $code"
    leftover="$(jq -r '.response_body.message_id' "$BODY")"
    call POST "$B/api/v1/messages/$leftover/ack" "${CLIENT_AUTH[@]}" >/dev/null
done
ok "queue drained"

echo "5. Publish, consume, ack"
PAYLOAD="smoke-$(date +%s)-$RANDOM"
expect 200 "publish with a scoped key" POST "$B/api/v1/topics/$TOPIC/messages" "${CLIENT_AUTH[@]}" "${JSON[@]}" -d "{\"payload\":\"$PAYLOAD\"}"
expect 403 "publish outside the key's scopes is forbidden" POST "$B/api/v1/topics/not-$TOPIC/messages" "${CLIENT_AUTH[@]}" "${JSON[@]}" -d '{"payload":"x"}'
expect 200 "consume claims the message" POST "$B/api/v1/queues/$QUEUE/consume" "${CLIENT_AUTH[@]}"
if [[ "$(jq -r '.response_body.payload' "$BODY")" == "$PAYLOAD" ]]; then
    ok "payload round-trips unchanged"
else
    fail "consumed the wrong payload: $(head -c 300 "$BODY")"
fi
MESSAGE_ID="$(jq -r '.response_body.message_id' "$BODY")"
expect 200 "ack" POST "$B/api/v1/messages/$MESSAGE_ID/ack" "${CLIENT_AUTH[@]}"
expect 409 "a second ack is a conflict" POST "$B/api/v1/messages/$MESSAGE_ID/ack" "${CLIENT_AUTH[@]}"
expect 204 "the queue is empty again" POST "$B/api/v1/queues/$QUEUE/consume" "${CLIENT_AUTH[@]}"

echo "6. Dashboard API, metrics, WebSocket ticket"
expect 200 "dashboard overview" GET "$B/api/v1/dashboard/overview" "${ADMIN[@]}"
expect 401 "metrics need a key" GET "$B/actuator/prometheus"
expect 200 "metrics with an admin key" GET "$B/actuator/prometheus" "${ADMIN[@]}"
expect 200 "a WebSocket ticket is issued" POST "$B/api/v1/auth/ws-ticket" "${ADMIN[@]}"

echo "7. Revocation"
expect 200 "revoke the smoke client" DELETE "$B/api/v1/clients/$CLIENT" "${ADMIN[@]}"
expect 401 "the revoked key stops working at once" POST "$B/api/v1/topics/$TOPIC/messages" "${CLIENT_AUTH[@]}" "${JSON[@]}" -d '{"payload":"x"}'

if [[ -n "$DASHBOARD_URL" ]]; then
    D="${DASHBOARD_URL%/}"
    echo "8. Dashboard ($D)"
    for path in / /login /login/callback /manage; do
        expect 200 "serves $path" GET "$D$path"
        grep -q 'id="root"' "$BODY" || fail "$path did not return the React app (missing SPA rewrite rule?)"
    done
    allowed="$(curl -sS -o /dev/null -D - -X OPTIONS "$B/api/v1/auth/me" \
        -H "Origin: $D" -H "Access-Control-Request-Method: GET" -H "Access-Control-Request-Headers: authorization" \
        | tr -d '\r' | awk -F': ' 'tolower($1) == "access-control-allow-origin" { print $2 }' || true)"
    if [[ "$allowed" == "$D" ]]; then
        ok "CORS allows the dashboard's origin"
    else
        fail "CORS allowed '$allowed', expected $D (check CORS_ALLOWED_ORIGINS)"
    fi
fi

echo
echo "ALL $PASSED CHECKS PASSED"
