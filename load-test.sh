#!/usr/bin/env bash
# Sends many publishes and consumes to a locally running MiniQueue AT THE SAME TIME,
# so every connected dashboard receives a burst of events from many threads at once.
# That burst is what used to trigger TEXT_PARTIAL_WRITING on a shared WebSocket session.
#
# Usage:   ./load-test.sh
# Options: BASE=http://localhost:8080 MESSAGES=200 PARALLEL=20 ./load-test.sh
set -euo pipefail

BASE="${BASE:-http://localhost:8080}"
MESSAGES="${MESSAGES:-200}"
PARALLEL="${PARALLEL:-20}"

# Unique names per run, so you can run this as many times as you like.
RUN="$(date +%s)"
TOPIC="load.topic.$RUN"
QUEUE="load-queue-$RUN"

echo "Creating topic '$TOPIC' and queue '$QUEUE' on $BASE"
curl -sf -o /dev/null -X POST "$BASE/topics/createTopic" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"$TOPIC\"}"
curl -sf -o /dev/null -X POST "$BASE/queues/createQueue" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"$QUEUE\",\"topic_name\":\"$TOPIC\"}"

echo
echo "Publishing $MESSAGES messages, $PARALLEL at a time. HTTP status counts:"
seq 1 "$MESSAGES" | xargs -P "$PARALLEL" -I{} \
  curl -s -o /dev/null -w "%{http_code}\n" -X POST "$BASE/messages/publishMessage" \
    -H 'Content-Type: application/json' \
    -d "{\"topic_name\":\"$TOPIC\",\"payload\":\"load message {}\"}" \
  | sort | uniq -c

echo
echo "Consuming $MESSAGES messages, $PARALLEL at a time. HTTP status counts:"
seq 1 "$MESSAGES" | xargs -P "$PARALLEL" -I{} \
  curl -s -o /dev/null -w "%{http_code}\n" \
    "$BASE/consumer/consumeMessage?queue_name=$QUEUE&consumer_id=load-consumer-{}" \
  | sort | uniq -c

echo
echo "Done. Expected: almost all 200s. A few 400s on consume are normal (see notes)."
echo "Any 500 means something broke: check the backend logs."
