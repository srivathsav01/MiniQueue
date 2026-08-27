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
   │  POST /messages/publishMessage
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

   Option A — Pull (REST polling)        Option B — Push (WebSocket)
   GET /consumer/consumeMessage          ws://<host>/ws/consume?queue=queue-01
   POST /consumer/ackMessage             → receives notification → calls GET /consume
                                         → calls POST /ack
```

### Message Lifecycle (State Machine)

```
PENDING ──── consumed ────▶ UNACKED ──── acked ────▶ ACKED
                                │
                                └─── retries exceeded ───▶ DEAD
```

A message can only move forward through states. An `ACKED` message can never go back to `PENDING`. State transitions are enforced in the service layer.

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
Consumer receives notification → calls GET /consume via REST
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
| Database | PostgreSQL 16 | Durable message storage with JSONB payload support |
| ORM | Spring Data JPA / Hibernate | Type-safe database access |
| Language | Java 21 | LTS release, modern language features |
| Frontend | React + TypeScript | Operations and management dashboard |
| Styling | Tailwind CSS + Shadcn/ui | Component library and utility styling |
| Observability | Prometheus + Grafana | Metrics collection and visualization |
| Containerization | Docker + Docker Compose | Single-command stack deployment |

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

If the broker marks a message `UNACKED` at push time, and the consumer crashes between receiving the notification and calling `GET /consume`, the message is stuck in `UNACKED` with no consumer actively holding it. The redelivery scheduler eventually rescues it, but the window of inconsistency is real.

More importantly, this design duplicates the status transition logic. The `consumeMessage` service method already handles `PENDING → UNACKED` correctly — it sets the status, records the `consumerId`, and stamps `unackedAt` atomically. Replicating that logic in the WebSocket listener creates two code paths that must stay in sync.

The chosen design keeps WebSocket as a **notification layer only**. The broker pushes a lightweight signal — "there is a message ready for you." The consumer responds by calling `GET /consume` via REST, which triggers the single, tested status transition path. The REST endpoint remains the sole source of truth for message state.

This separation of concerns also handles the competing consumer case cleanly — if two consumers are connected to the same queue and both receive the notification, whoever calls `GET /consume` first claims the message. The second call naturally gets "no messages available." No coordination logic needed.

### Why round-robin for WebSocket consumer selection

When multiple consumers are connected to the same queue via WebSocket, the broker must decide who receives each notification. Sending to all of them would cause duplicate processing — each consumer would call `GET /consume` and compete, but the notification itself is redundant for all but one.

Round-robin distributes notifications evenly across connected consumers using a per-queue `AtomicInteger` counter. This is thread-safe, stateless, and requires no coordination between consumers.

### Why a dedicated `/ws/monitor` endpoint instead of reusing `/ws/consume`

Consumer WebSocket and monitor WebSocket serve different audiences with different data needs. `/ws/consume` is for delivery — it carries message notifications to a specific queue's consumers. `/ws/monitor` is for observability — it broadcasts all broker activity to dashboards.

Mixing them would mean the dashboard has to connect to every queue individually, and consumer clients would receive irrelevant monitoring events. Separation keeps both clean and independently evolvable.

### Why dashboard aggregations run in the database, not in Java

Fetching all message rows into Java memory and counting them there would be catastrophically inefficient at scale. A queue with 100,000 messages would require 100,000 rows transferred over the network just to produce a count.

PostgreSQL's `GROUP BY` with `COUNT()` computes aggregations in a single scan without moving data anywhere. The database returns one row per status per queue — a handful of integers regardless of message volume. Computation lives where the data lives.

---

## Known Limitations

- **No consumer authentication** — any client can consume from any queue by providing a consumer ID string. A production broker would require consumer registration and credential verification.
- **Single node only** — no horizontal scaling or clustering support. The WebSocket session registry is in-memory, so multiple broker instances would not share session state.
- **No message TTL** — messages do not expire. A queue can grow unboundedly.
- **No backpressure** — the broker does not limit how many messages a producer can publish.

---

## API Reference

### Broker Endpoints

#### Create a Topic
```
POST /topics/createTopic
Content-Type: application/json

{ "name": "order.placed" }
```

#### List All Topics
```
GET /topics/all
```

#### Create a Queue
```
POST /queues/createQueue
Content-Type: application/json

{ "name": "email-queue", "topic_name": "order.placed" }
```

#### List All Queues
```
GET /queues/all
```

#### Get Messages by Queue
```
GET /queues/{queueName}/messages
```

#### Publish a Message
```
POST /messages/publishMessage
Content-Type: application/json

{ "topic_name": "order.placed", "payload": "{\"orderId\": \"123\"}" }
```

#### Consume a Message (Pull)
```
GET /consumer/consumeMessage?queue_name=email-queue&consumer_id=email-service
```

#### Acknowledge a Message
```
POST /consumer/ackMessage
Content-Type: application/json

{ "message_id": "<uuid>", "consumer_id": "email-service" }
```

#### Nack a Message
```
POST /consumer/nackMessage
Content-Type: application/json

{ "message_id": "<uuid>", "consumer_id": "email-service", "requeue": true }
```

Set `requeue: true` to reset to `PENDING`. Set `requeue: false` to move directly to `DEAD`.

### Dashboard Endpoints

#### Broker Overview
```
GET /dashboard/overview
```

#### Queue Breakdown
```
GET /dashboard/queues
```

#### Dead Letter Queue
```
GET /dashboard/dlq
```

#### Replay a Dead Message
```
POST /dashboard/dlq/{messageId}/replay
```

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
  "status": 400,
  "message": "No Topic found with the name order.placed",
  "errors": null,
  "response_body": null
}
```

| Status | Meaning |
|---|---|
| `400` | Client error — invalid input, resource not found, wrong state |
| `500` | Server error — unexpected failure |

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
cd miniqueue-backend
```

Create the database:
```sql
CREATE DATABASE miniqueue;
```

Configure `src/main/resources/application.yml` with your database credentials, then:

```bash
./mvnw spring-boot:run
```

Backend starts on `http://localhost:8080`.

**Frontend:**

```bash
cd miniqueue-dashboard
npm install
npm run dev
```

Dashboard opens at `http://localhost:5173`.

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
