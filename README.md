# Miniqueue

A lightweight message broker built from scratch with **at-least-once delivery semantics**, topic-based fan-out, and explicit consumer acknowledgement. Inspired by RabbitMQ's core concepts — built to understand them from the inside out.

**Live Demo:** https://miniqueue.onrender.com  
**API Explorer:** https://miniqueue-backend.onrender.com/swagger-ui/index.html

---

## What it is

Miniqueue decouples producers from consumers using a topic → queue → consumer model. A producer publishes a message to a topic. The broker fans it out to all queues bound to that topic. Each queue delivers messages independently to its consumers. A message is only considered complete after an explicit acknowledgement — if a consumer crashes before acking, the message is redelivered.

This is the core guarantee that makes distributed systems reliable: **at-least-once delivery**.

---

## Architecture

```
Producer
   │
   │  POST /api/v1/topics/{topic}/messages
   ▼
┌─────────────────────────────────────────┐
│               Miniqueue Broker          │
│                                         │
│  Topic                                  │
│       │                                 │
│       ├──── binding ────▶ queue-01      │
│       │                                 │
│       └──── binding ────▶ queue-02      │
└─────────────────────────────────────────┘
         │                        │
         ▼                        ▼
   consumer-service-01     consumer-service-02

   Option A — Pull (REST polling)                 Option B — Push (WebSocket)
   POST /api/v1/queues/{queue}/consume            ws://<host>/ws/consume?queue=queue-01
   POST /api/v1/messages/{id}/ack                 → receives notification → calls POST .../consume
                                                  → calls POST .../ack
```

### Message Lifecycle (State Machine)

```
PENDING ──── consumed ────▶ UNACKED ──── acked ────▶ ACKED
   ▲                            │
   │                            ├── nack (requeue) or ack timeout ──▶ back to PENDING
   │                            │
   │                            └── nack (no requeue) or retries exceeded ──▶ DEAD
   │                                                                          │
   └──────────────────────────── replayed from DLQ ───────────────────────────┘
```

An `ACKED` message is final and can never go back to `PENDING`. State transitions are enforced in the service layer, and every transition locks the message row first (see [Concurrency Guarantees](#concurrency-guarantees)).

### WebSocket Notification Flow

```
Producer publishes message
        ↓
Broker saves message → status: PENDING
        ↓
NewMessageEvent fires internally
        ↓
WebSocketEventListener picks next session (round-robin)
        ↓
Pushes notification: { "queue": "queue-01", "event": "<payload preview>" }
        ↓
Consumer receives notification → calls GET /consumer/consumeMessage via REST
        ↓
Status transitions to UNACKED — claimed by consumer
        ↓
Consumer processes → calls POST /ack
        ↓
Status transitions to ACKED
```

### Monitor WebSocket Flow

```
Dashboard connects to ws://<host>/ws/monitor
        ↓
Every broker state transition fires a BrokerActivityEvent
        ↓
MonitorEventListener broadcasts event to all connected dashboards
        ↓
Dashboard renders event in real-time activity feed
```

---

## Tech Stack

| Layer | Technology | Why |
|---|---|---|
| Backend | Spring Boot 3.5 | Production-grade Java framework |
| Database | PostgreSQL 16 | Durable message storage and row-level locking (`FOR UPDATE SKIP LOCKED`) for safe concurrent delivery |
| ORM | Spring Data JPA / Hibernate | Type-safe database access |
| Language | Java 21 | LTS release, modern language features |
| Frontend | React + TypeScript | Operations and management dashboard |
| Styling | Tailwind CSS + Shadcn/ui | Component library and utility styling |
| Observability | Prometheus + Grafana | Metrics collection and visualization |
| Containerization | Docker + Docker Compose | Single-command stack deployment |
| Testing | JUnit 5 + Testcontainers | Integration tests against a real PostgreSQL in Docker |

---

## Dashboard

The operations and management dashboard provides real-time visibility and full broker control.

### Operations

**Overview** — broker-wide message counts broken down by status (pending, unacked, acked, dead). Auto-refreshes on every broker event via WebSocket.

**Queue Breakdown** — per-queue message counts with color-coded status columns. Click any queue row to inspect its messages inline as compact horizontal cards. Dead counts are highlighted in bold.

**Dead Letter Queue Inspector** — all dead messages with their last consumer, retry count, and timestamp. Replay any dead message back to `PENDING` with a single click and confirmation dialog.

**Live Activity Feed** — a real-time scrolling feed of all broker events received via `/ws/monitor`. Shows event type, queue name, detail, and timestamp. Capped at 50 events with auto-scroll. Connection status indicator with automatic reconnect.

### Management

**Topics** — create topics and browse all existing ones.

**Queues** — create queues bound to topics. Click any queue to inspect its messages inline.

**Publish** — publish messages to any topic with JSON validation, payload templates, and real-time fan-out confirmation.

---

## Design Decisions

### Why PostgreSQL instead of an in-memory store

Messages must survive a broker restart. An in-memory store like Redis would lose all unacked messages on crash. PostgreSQL gives us durable storage with ACID guarantees — a message written to the database is guaranteed to be there after a restart. This is the foundation of at-least-once delivery.

### Why UUID primary keys instead of auto-increment

Auto-incrementing integer IDs are generated sequentially by the database. This creates a bottleneck if multiple broker instances run in parallel — they'd conflict on ID generation. UUIDs are generated by the application before the database is involved, are globally unique, and are unguessable by external callers.

### Why store status as a string (VARCHAR) instead of an integer

Storing `PENDING`, `UNACKED`, `ACKED`, `DEAD` as readable strings makes the database self-documenting. A query like `SELECT * FROM message WHERE status = 'DEAD'` is immediately understandable. Integer enum storage (`0, 1, 2, 3`) is an optimisation that sacrifices readability — not worth it at this scale.

### Why fan-out creates separate message rows per queue

Each queue gets its own independent copy of the message. This means `email-queue` and `warehouse-queue` can consume and ack independently — one queue's progress doesn't affect the other. The tradeoff is storage: one publish to a topic with 10 queues creates 10 rows. For a message broker, this is the correct tradeoff.

### Why `publishedAt` is database-generated

If the application generates the timestamp, clock skew between multiple app instances could cause ordering issues. The database has one clock — so `published_at DEFAULT now()` guarantees consistent, trustworthy ordering within a queue.

### Why WebSocket is used only for notification, not for status transition

The alternative design — transitioning a message to `UNACKED` inside the WebSocket event listener on push — seems simpler at first. But it introduces a subtle reliability problem.

If the broker marks a message `UNACKED` at push time, and the consumer crashes between receiving the notification and calling `GET /consumer/consumeMessage`, the message is stuck in `UNACKED` with no consumer actively holding it. The redelivery scheduler eventually rescues it, but the window of inconsistency is real.

More importantly, this design duplicates the status transition logic. The `consumeMessage` service method already handles `PENDING → UNACKED` correctly — it sets the status, records the `consumerId`, and stamps `unackedAt` atomically. Replicating that logic in the WebSocket listener creates two code paths that must stay in sync.

The chosen design keeps WebSocket as a **notification layer only**. The broker pushes a lightweight signal — "there is a message ready for you." The consumer responds by calling `GET /consumer/consumeMessage` via REST, which triggers the single, tested status transition path. The REST endpoint remains the sole source of truth for message state.

This separation of concerns also handles the competing consumer case cleanly — if two consumers are connected to the same queue and both receive the notification, whoever calls `GET /consumer/consumeMessage` first claims the message. The second call gets "no messages available." No coordination logic is needed in the WebSocket layer, because the database row lock on the consume path guarantees only one caller can claim a message (see [Concurrency Guarantees](#concurrency-guarantees)).

### Why round-robin for WebSocket consumer selection

When multiple consumers are connected to the same queue via WebSocket, the broker must decide who receives each notification. Sending to all of them would cause duplicate processing — each consumer would call `GET /consumer/consumeMessage` and compete, but the notification itself is redundant for all but one.

Round-robin distributes notifications evenly across connected consumers using a per-queue `AtomicInteger` counter. This is thread-safe, stateless, and requires no coordination between consumers.

### Why a dedicated `/ws/monitor` endpoint instead of reusing `/ws/consume`

Consumer WebSocket and monitor WebSocket serve different audiences with different data needs. `/ws/consume` is for delivery — it carries message notifications to a specific queue's consumers. `/ws/monitor` is for observability — it broadcasts all broker activity to dashboards.

Mixing them would mean the dashboard has to connect to every queue individually, and consumer clients would receive irrelevant monitoring events. Separation keeps both clean and independently evolvable.

### Why dashboard aggregations run in the database, not in Java

Fetching all message rows into Java memory and counting them there would be catastrophically inefficient at scale. A queue with 100,000 messages would require 100,000 rows transferred over the network just to produce a count.

PostgreSQL's `GROUP BY` with `COUNT()` computes aggregations in a single scan without moving data anywhere. The database returns one row per status per queue — a handful of integers regardless of message volume. Computation lives where the data lives.

---

## Concurrency Guarantees

Miniqueue is safe to run with many competing consumers on the same queue, and with more than one broker instance pointed at the same database: **a message is never handed to two consumers at once, and an ack is never silently overwritten.** This is verified by an integration test, not just claimed.

### The race

The original consume path was *look, then act*: read the oldest `PENDING` message, then update it to `UNACKED` in a separate step. Two consumers polling at the same moment could both read the same row, and both would receive the message. The redelivery scheduler had the same shape, so it could overwrite a message that a consumer had just acked and put it back in the queue.

Adding `@Transactional` does not fix this. A transaction makes changes all-or-nothing, but a plain `SELECT` takes no lock in PostgreSQL, so two transactions can still read the same row and both act on it.

### The fix

Every path that changes a message's state now locks the row before deciding anything:

| Path | Lock | Why this lock |
|---|---|---|
| Consume | `SELECT ... FOR UPDATE SKIP LOCKED LIMIT 1` | Each consumer locks a different row and skips rows other consumers hold, so N consumers work in parallel instead of queueing behind the same row |
| Redelivery scheduler | `FOR UPDATE SKIP LOCKED`, in batches | Two broker instances never redeliver the same stuck message, and a message that is mid-ack is skipped rather than overwritten |
| Ack / Nack / Replay | `FOR UPDATE` (pessimistic write) | These target one specific message, so they *wait* for the lock and then check the real, current status |

The row lock is held only for the short claim transaction (milliseconds), never while a consumer processes the message. After the claim, the message is protected by its state: `UNACKED` with a `consumerId` and an `unackedAt` timestamp acts as a lease. If it isn't acked before the timeout, the scheduler returns it to `PENDING`. A late ack from the original consumer is then rejected by the `consumerId` check, a simple fencing check, because the message may already belong to another consumer.

### The proof

`ConcurrencyIntegrationTest` starts a real PostgreSQL 16 in Docker with Testcontainers, publishes 100 messages, releases 10 consumer threads at the same instant, and asserts that:

- no message was delivered more than once,
- every published message was delivered, and
- the database shows 0 `PENDING` and 100 `UNACKED`.

Removing `SKIP LOCKED` from the consume query makes this test fail with duplicate deliveries, which confirms the test actually detects the race rather than passing by luck.

### The trade-off

`SKIP LOCKED` gives approximately FIFO delivery, not strict FIFO. With several consumers, a later message can finish before an earlier one, which is the same behaviour as an SQS standard queue. Strict per-key ordering would need partitioning, which Miniqueue does not implement.

---

## Known Limitations

- **No consumer authentication** — any client can consume from any queue by providing a consumer ID string. A production broker would require consumer registration and credential verification.
- **WebSocket notifications are per instance** — message delivery state lives in PostgreSQL and is safe across multiple broker instances, but the WebSocket session registry is in-memory, so a push notification only reaches consumers connected to the instance that received the publish. Polling consumers are unaffected.
- **No strict ordering or partitioning** — competing consumers on the same queue are safe (no duplicate delivery), but there is no per-key ordering like Kafka partitions, and delivery order across consumers is approximately FIFO.
- **No message TTL** — messages do not expire. A queue can grow unboundedly.
- **No backpressure** — the broker does not limit how many messages a producer can publish.
- **Unbenchmarked at scale** — PostgreSQL was chosen for correctness (transactions, durability) over a purpose-built queue like Redis Streams, but the design hasn't been stress-tested to identify where write throughput or lock contention start to become a bottleneck.

---

## API Reference

### Authentication

Producers and consumers authenticate with an API key: `Authorization: Bearer mq_...`. An admin registers each application as a **client** and chooses its scopes:

| Scope | Allows |
|---|---|
| `publish:<topic>` / `publish:*` | Publishing to that topic / any topic |
| `consume:<queue>` / `consume:*` | Consuming, acking and nacking on that queue / any queue |
| `admin` | Everything, including `/api/v1/clients` |

Keys are 256-bit random values. The broker stores only their SHA-256 hash, so a database leak does not leak working keys. The consumer identity recorded on a message is taken from the key (plus an optional `MiniQueue-Instance` header that tells replicas apart), never from the request, so one client cannot ack another client's message.

The first admin key comes from `MINIQUEUE_BOOTSTRAP_ADMIN_KEY` at startup. Missing or invalid keys get `401`, valid keys without the needed scope get `403`.

### Broker Endpoints

All REST endpoints are versioned under `/api/v1`. URLs name resources and the HTTP method says what happens to them; actions that change a message's state (consume, ack, nack, replay) are `POST`, because a `GET` must be safe for browsers and proxies to repeat.

| Method | Path | Body | Purpose |
|---|---|---|---|
| `POST` | `/api/v1/topics` | `{ "name": "order.placed" }` | Create a topic |
| `GET` | `/api/v1/topics` | | List topics |
| `POST` | `/api/v1/queues` | `{ "name": "email-queue", "topic_name": "order.placed" }` | Create a queue bound to a topic |
| `GET` | `/api/v1/queues` | | List queues |
| `GET` | `/api/v1/queues/{queue}/messages` | | Messages in a queue |
| `POST` | `/api/v1/topics/{topic}/messages` | `{ "payload": "{\"orderId\": \"123\"}" }` | Publish to every queue bound to the topic |
| `POST` | `/api/v1/queues/{queue}/consume` | | Claim the oldest pending message: 200 with it, or 204 if none |
| `POST` | `/api/v1/messages/{id}/ack` | | Mark processed |
| `POST` | `/api/v1/messages/{id}/nack` | `{ "requeue": true }` (optional) | Reject: `requeue: true` (default) retries, `false` moves it to the DLQ |
| `GET` | `/api/v1/dashboard/overview` | | Broker-wide counts by status |
| `GET` | `/api/v1/dashboard/queues` | | Per-queue counts |
| `GET` | `/api/v1/dashboard/dlq` | | Dead-lettered messages |
| `POST` | `/api/v1/dashboard/dlq/{id}/replay` | | Move a dead message back to `PENDING` |
| `POST` | `/api/v1/clients` | `{ "name": "mailer", "scopes": ["consume:email-queue"] }` | Register a client; the response holds its `api_key`, shown once |
| `GET` | `/api/v1/clients` | | Active clients (key prefixes and last use, never keys) |
| `POST` | `/api/v1/clients/{name}/rotate` | | New key; the old one stops working immediately |
| `DELETE` | `/api/v1/clients/{name}` | | Revoke |

Topic and queue names are lowercased and must be 1 to 100 characters from `a-z`, `0-9`, `.`, `_` and `-`. Payloads are limited to `miniqueue.limits.max-payload-bytes` (256 KB by default).

Interactive docs: `http://localhost:8080/swagger-ui.html`.

### WebSocket

#### Consumer Notifications (Push-based consumption)
```
ws://<host>/ws/consume?queue=email-queue
```

Notification format:
```json
{ "queue": "email-queue", "event": "<payload preview...>" }
```

On receiving a notification, call `GET /consumer/consumeMessage` via REST to claim and process the message.

#### Monitor Feed (Dashboard)
```
ws://<host>/ws/monitor
```

Event format:
```json
{
  "eventType": "PUBLISHED",
  "queueName": "email-queue",
  "detail": "Message published to email-queue",
  "timestamp": "2026-06-24T10:00:00"
}
```

Event types: `PUBLISHED`, `CONSUMED`, `ACKED`, `NACKED`, `REDELIVERED`, `DEAD`, `REPLAYED`.

### Error Responses

All errors return a consistent shape:
```json
{
  "status": 404,
  "message": "No Topic found with the name order.placed",
  "errors": null,
  "response_body": null
}
```

| Status | Meaning | Retry? |
|---|---|---|
| `204` | Consume found nothing claimable right now | Poll again later |
| `400` | Invalid input: bad name, missing field, malformed JSON | No, fix the request |
| `401` | Missing, invalid or revoked API key | No, fix the key |
| `403` | The key's scopes don't allow this | No, an admin must grant the scope |
| `404` | Topic, queue or message does not exist | No |
| `409` | Conflicts with current state: duplicate name, message not `UNACKED`, held by another consumer | No |
| `413` | Payload over the size limit | No, send less |
| `500` | Unexpected server failure (details are logged, never returned) | Yes, with backoff |

---

## Running Locally

### Prerequisites

- Java 21
- PostgreSQL 16
- Maven (or the bundled `./mvnw`)
- Node.js 20+
- Docker + Docker Compose (optional: for the full stack, and required for the integration tests)
- `openssl`, to generate keys (Git Bash on Windows ships with it)

### Step 1: generate an admin key

Every producer and consumer authenticates with an API key, and only an admin can create those keys. The broker reads the first admin key from the `MINIQUEUE_BOOTSTRAP_ADMIN_KEY` environment variable at startup. On first start it creates an admin client called `bootstrap-admin` with that key; set a different value later and restart, and the key is rotated.

Generate one:
```bash
echo "mq_$(openssl rand -base64 32 | tr '+/' '-_' | tr -d '=')"
```

This prints something like `mq_q3ZxV0h...` (46 characters). Keep it somewhere safe and never commit it. The broker refuses to start if the variable is set to a value that doesn't start with `mq_` and have at least 32 characters after it. If the variable is not set at all, the broker still starts, but logs a warning, and nobody can register clients.

### Option A: Docker Compose (recommended)

```bash
git clone https://github.com/srivathsav01/MiniQueue.git
cd MiniQueue
```

Create a `.env` file in the project root (it is git-ignored):
```
POSTGRES_PASSWORD=your_password
SPRING_DATASOURCE_PASSWORD=your_password
CORS_ALLOWED_ORIGINS=http://localhost:80
MINIQUEUE_BOOTSTRAP_ADMIN_KEY=mq_paste_the_key_from_step_1
```

Start everything:
```bash
docker compose up --build
```

The backend log should show `Bootstrap admin key installed (mq_xxxxxxxx...)`.

| Service | URL |
|---|---|
| Dashboard | http://localhost |
| REST API | http://localhost/api/v1/... (proxied to the backend by the dashboard's nginx) |
| Swagger UI | http://localhost/swagger-ui/index.html |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 |

### Option B: Manual setup

**Database:**
```sql
CREATE DATABASE miniqueue;
```
Flyway creates all the tables on the first start.

**Backend:** the backend is configured through environment variables, so no credentials go into `application.yaml`.

Git Bash / macOS / Linux:
```bash
cd backend
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/miniqueue
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=your_password
export MINIQUEUE_BOOTSTRAP_ADMIN_KEY=mq_paste_the_key_from_step_1
./mvnw spring-boot:run
```

PowerShell:
```powershell
cd backend
$env:SPRING_DATASOURCE_PASSWORD = "your_password"
$env:MINIQUEUE_BOOTSTRAP_ADMIN_KEY = "mq_paste_the_key_from_step_1"
./mvnw spring-boot:run
```

IntelliJ: open **Run → Edit Configurations → MiniqueueApplication → Environment variables** and add them as `NAME=value` pairs separated by `;`.

The backend starts on `http://localhost:8080`, and its log should show `Bootstrap admin key installed`.

**Frontend:**
```bash
cd ui
npm install
npm run dev
```
The dashboard opens at `http://localhost:5173`. The Vite dev server proxies `/api` and `/ws` to `localhost:8080`.

### Step 2: register your first clients

The examples use `B=http://localhost:8080`. With Docker Compose, use `B=http://localhost` instead.

```bash
B=http://localhost:8080
ADMIN=mq_paste_the_key_from_step_1

# A topic, and a queue bound to it
curl -s -X POST $B/api/v1/topics -H "Content-Type: application/json" -d '{"name":"order.placed"}'
curl -s -X POST $B/api/v1/queues -H "Content-Type: application/json" -d '{"name":"email-queue","topic_name":"order.placed"}'

# A producer that may publish to order.placed, and a consumer that may consume email-queue.
# Each response contains "api_key". It is shown only once: copy it now.
curl -s -X POST $B/api/v1/clients -H "Authorization: Bearer $ADMIN" -H "Content-Type: application/json" \
  -d '{"name":"shop","scopes":["publish:order.placed"]}'
curl -s -X POST $B/api/v1/clients -H "Authorization: Bearer $ADMIN" -H "Content-Type: application/json" \
  -d '{"name":"mailer","scopes":["consume:email-queue"]}'
```

Then use the two keys:
```bash
SHOP=mq_key_from_the_shop_response
MAILER=mq_key_from_the_mailer_response

# Publish as the shop
curl -s -X POST $B/api/v1/topics/order.placed/messages -H "Authorization: Bearer $SHOP" \
  -H "Content-Type: application/json" -d '{"payload":"{\"orderId\":42}"}'

# Consume as the mailer, then ack using the message_id from the response
curl -s -X POST $B/api/v1/queues/email-queue/consume -H "Authorization: Bearer $MAILER"
curl -s -X POST $B/api/v1/messages/<message_id>/ack -H "Authorization: Bearer $MAILER"
```

Lost a key? Run `POST /api/v1/clients/{name}/rotate` with the admin key. It returns a new key, and the old one stops working immediately.

In Swagger UI, click **Authorize** and paste a key; every "Try it out" call then sends it.

### Running the tests

The backend integration tests start their own throwaway PostgreSQL container, so they never touch your local database, and they set their own admin key. Docker (or a Docker-compatible runtime such as Rancher Desktop in `dockerd` mode) must be running.

```bash
cd backend
./mvnw test
```

The SDK unit tests use in-process stub servers and need neither Docker nor a broker:
```bash
cd sdk
./mvnw test
```

### Load test and live SDK checks

These run against a broker you have already started. They register their own scoped clients, so they need the admin key:
```bash
ADMIN_KEY=mq_paste_the_key_from_step_1 ./load-test.sh
```

For `LiveBrokerCheck`, `LiveConsumerCheck` and `LiveStarterCheck` (in `sdk/`), set `MINIQUEUE_ADMIN_KEY` to the same key in each run configuration's environment variables, then run `main()`.

---

## Observability

When running via Docker Compose, Prometheus scrapes the broker's custom metrics every 15 seconds:

| Metric | Type | Description |
|---|---|---|
| `miniqueue_messages_published_total` | Counter | Total messages published |
| `miniqueue_messages_consumed_total` | Counter | Total messages consumed |
| `miniqueue_messages_acked_total` | Counter | Total messages acknowledged |
| `miniqueue_messages_dead_total` | Counter | Total messages dead-lettered |
| `miniqueue_messages_redelivered_total` | Counter | Total messages redelivered |
| `miniqueue_queue_depth{queue="..."}` | Gauge | Current pending messages per queue |

Grafana at `http://localhost:3000` (admin/admin) connects to Prometheus automatically.