#!/usr/bin/env bash
# Sends many publishes and consumes to a locally running MiniQueue AT THE SAME TIME,
# so every connected dashboard receives a burst of events from many threads at once.
# That burst is what used to trigger TEXT_PARTIAL_WRITING on a shared WebSocket session.
#
# Usage:   ./load-test.sh
# Options: BASE=http://localhost:8080 MESSAGES=200 PARALLEL=20 ./load-test.sh
set -euo pipefail : "${ADMIN_KEY:?Set ADMIN_KEY to your bootstrap admin key: ADMIN_KEY=mq_... ./load-test.sh}"

BASE="${BASE:-http://localhost:8080}"
MESSAGES="${MESSAGES:-200}"
PARALLEL="${PARALLEL:-20}"

# Unique names per run, so you can run this as many times as you like.
RUN="$(date +%s)"
TOPIC="load.topic.$RUN"
QUEUE="load-queue-$RUN"

echo "Creating topic '$TOPIC' and queue '$QUEUE' on $BASE"
curl -sf -o /dev/null -X POST "$BASE/api/v1/topics" \
  -H "Authorization: Bearer $ADMIN_KEY" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"$TOPIC\"}"
curl -sf -o /dev/null -X POST "$BASE/api/v1/queues" \
  -H "Authorization: Bearer $ADMIN_KEY" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"$QUEUE\",\"topic_name\":\"$TOPIC\"}"

# A client allowed to publish to this run's topic and consume from this run's queue.
KEY=$(curl -sf -X POST "$BASE/api/v1/clients" \
  -H "Authorization: Bearer $ADMIN_KEY" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"load-$RUN\",\"scopes\":[\"publish:$TOPIC\",\"consume:$QUEUE\"]}" \
  | sed -E 's/.*"api_key":"([^"]+)".*/\1/')

echo
echo "Publishing $MESSAGES messages, $PARALLEL at a time. HTTP status counts:"
seq 1 "$MESSAGES" | xargs -P "$PARALLEL" -I{} \
  curl -s -o /dev/null -w "%{http_code}\n" -X POST "$BASE/api/v1/topics/$TOPIC/messages" \
    -H "Authorization: Bearer $KEY" \
    -H 'Content-Type: application/json' \
    -d "{\"payload\":\"load message {}\"}" \
  | sort | uniq -c

echo
echo "Consuming $MESSAGES messages, $PARALLEL at a time. HTTP status counts:"
seq 1 "$MESSAGES" | xargs -P "$PARALLEL" -I{} \
  curl -s -o /dev/null -w "%{http_code}\n" -X POST "$BASE/api/v1/queues/$QUEUE/consume" \
    -H "Authorization: Bearer $KEY" \
    -H "MiniQueue-Instance: worker-{}" \
  | sort | uniq -c

echo
echo "Done. Expected: almost all 200s. A few 204s on consume are normal: nothing was claimable at that instant."
echo "Any 500 means something broke: check the backend logs."
