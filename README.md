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
| `POST` | `/api/v1/queues/{queue}/consume` | `{ "consumer_id": "email-service" }` | Claim the oldest pending message: 200 with it, or 204 if none |
| `POST` | `/api/v1/messages/{id}/ack` | `{ "consumer_id": "email-service" }` | Mark processed |
| `POST` | `/api/v1/messages/{id}/nack` | `{ "consumer_id": "email-service", "requeue": true }` | Reject: `requeue: true` (default) retries, `false` moves it to the DLQ |
| `GET` | `/api/v1/dashboard/overview` | | Broker-wide counts by status |
| `GET` | `/api/v1/dashboard/queues` | | Per-queue counts |
| `GET` | `/api/v1/dashboard/dlq` | | Dead-lettered messages |
| `POST` | `/api/v1/dashboard/dlq/{id}/replay` | | Move a dead message back to `PENDING` |

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
| `404` | Topic, queue or message does not exist | No |
| `409` | Conflicts with current state: duplicate name, message not `UNACKED`, held by another consumer | No |
| `413` | Payload over the size limit | No, send less |
| `500` | Unexpected server failure (details are logged, never returned) | Yes, with backoff |

---

## Running Locally

### Prerequisites

- Java 21
- PostgreSQL 16
- Maven
- Node.js 20+
- Docker + Docker Compose (optional — for full stack)

### Option A — Docker Compose (recommended)

```bash
git clone https://github.com/srivathsav01/MiniQueue.git
cd MiniQueue
```

Create a `.env` file in the project root:
```
POSTGRES_PASSWORD=your_password
SPRING_DATASOURCE_PASSWORD=your_password
CORS_ALLOWED_ORIGINS=http://localhost:80
```

Start everything:
```bash
docker compose up --build
```

| Service | URL |
|---|---|
| Dashboard | http://localhost |
| Swagger UI | http://localhost/swagger-ui/index.html |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 |

### Option B — Manual Setup

**Backend:**

```bash
cd backend
```

Create the database:
```sql
CREATE DATABASE miniqueue;
```

Configure `src/main/resources/application.yaml` with your database credentials, then:

```bash
./mvnw spring-boot:run
```

Backend starts on `http://localhost:8080`.

**Frontend:**

```bash
cd ui
npm install
npm run dev
```

Dashboard opens at `http://localhost:5173`.

### Running the tests

The integration tests start their own throwaway PostgreSQL container, so they don't touch your local database. Docker (or a Docker-compatible runtime such as Rancher Desktop in `dockerd` mode) must be running.

```bash
cd backend
./mvnw test
```

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