# MiniQueue

[![CI](https://github.com/srivathsav01/MiniQueue/actions/workflows/ci.yml/badge.svg)](https://github.com/srivathsav01/MiniQueue/actions/workflows/ci.yml)
[![Smoke test](https://github.com/srivathsav01/MiniQueue/actions/workflows/smoke.yml/badge.svg)](https://github.com/srivathsav01/MiniQueue/actions/workflows/smoke.yml)

A message broker built from scratch on PostgreSQL, with **at-least-once delivery**, topic-based fan-out, explicit acknowledgements, a dead-letter queue, API-key security and a Java SDK. Inspired by RabbitMQ's core concepts, and built to understand them from the inside out.

**Live demo:** https://miniqueue.onrender.com (sign in with GitHub: any account gets read-only access)  
**API explorer:** https://miniqueue-backend.onrender.com/swagger-ui/index.html  
**Java SDK:** [`sdk/`](sdk/README.md), a plain Java client and a Spring Boot starter with `@MiniQueueListener`

---

## What it is

MiniQueue decouples producers from consumers with a topic → queue → consumer model. A producer publishes a message to a topic. The broker fans it out to every queue bound to that topic. Each queue delivers its copy independently. A message only counts as done after an explicit ack: if a consumer crashes before acking, the message is redelivered.

That is the guarantee that makes distributed systems reliable: **at-least-once delivery**. Consumers should therefore be idempotent, because a message can occasionally arrive twice.

---

## Architecture

```
Producer (API key with publish:order.placed)
   │
   │  POST /api/v1/topics/order.placed/messages
   ▼
┌─────────────────────────────────────────┐
│               MiniQueue broker          │
│                                         │
│  Topic order.placed                     │
│       │                                 │
│       ├──── binding ────▶ email-queue   │
│       │                                 │
│       └──── binding ────▶ stock-queue   │
└─────────────────────────────────────────┘
         │                        │
         ▼                        ▼
   email-service            stock-service
 (consume:email-queue)    (consume:stock-queue)

   Pull (REST polling, used by the SDK)       Push notification (WebSocket)
   POST /api/v1/queues/{queue}/consume        /ws/consume?queue=email-queue&ticket=...
   POST /api/v1/messages/{id}/ack             → "message X is ready" → POST .../consume
```

### Message lifecycle

```
PENDING ──── consumed ────▶ UNACKED ──── acked ────▶ ACKED
   ▲                            │
   │                            ├── nack (requeue) or ack timeout ──▶ back to PENDING
   │                            │
   │                            └── nack (no requeue) or retries exceeded ──▶ DEAD
   │                                                                          │
   └──────────────────────────── replayed from DLQ ───────────────────────────┘
```

An `ACKED` message is final. Every transition locks the message row first (see [Concurrency guarantees](#concurrency-guarantees)).

### WebSocket notification flow

```
Producer publishes → broker saves the message as PENDING and commits
        ↓
NewMessageEvent fires after the commit
        ↓
WebSocketEventListener picks the next connected consumer (round-robin)
        ↓
Pushes { "queue": "email-queue", "message_id": "..." }   (the id only, never the payload)
        ↓
Consumer calls POST /api/v1/queues/email-queue/consume with its API key → UNACKED
        ↓
Consumer processes, then POST /api/v1/messages/{id}/ack → ACKED
```

### Monitor flow (dashboard)

```
Dashboard asks POST /api/v1/auth/ws-ticket → opens /ws/monitor?ticket=wst_...
        ↓
Every state transition fires a BrokerActivityEvent (after commit)
        ↓
MonitorEventListener broadcasts it to every connected dashboard
        ↓
The live activity feed renders it
```

---

## Security

Two kinds of callers, one permission model.

| Caller | Proves who it is with | Gets it from | Lasts |
|---|---|---|---|
| **Programs** (producers, consumers, scripts, Prometheus) | API key `mq_...` | An admin registers the program on the dashboard's Clients page; the key is shown once | Until rotated or revoked |
| **People** (the dashboard) | Session token `mqs_...` | Sign in with GitHub | 8 hours, or until sign-out |
| **Browsers opening a WebSocket** | Ticket `wst_...` in the URL | `POST /api/v1/auth/ws-ticket` with a session or key | 30 seconds, one use |

Every credential becomes the same thing on the server: a name plus a set of **scopes**.

| Scope | Allows |
|---|---|
| `publish:<topic>` / `publish:*` | Publishing to that topic / any topic |
| `consume:<queue>` / `consume:*` | Consuming, acking and nacking on that queue / any queue |
| `view` | Reading the dashboard: overview, queues, messages, DLQ, live feed |
| `metrics` | Scraping `/actuator/prometheus` |
| `admin` | Everything: topics, queues, replay, clients |

GitHub usernames listed in `MINIQUEUE_ADMINS` sign in with `admin`. Anyone else who signs in with GitHub gets `view`, which is what makes the public demo safe to share. Anything not explicitly allowed is denied.

### Sign-in flow

```
1. Dashboard → GET /api/v1/auth/github/login
   Broker    → random state in a 10-minute HttpOnly cookie → redirect to GitHub
2. GitHub    → user authorizes → redirect to /api/v1/auth/github/callback?code=...&state=...
3. Broker    → state must match the cookie (blocks login CSRF)
             → exchanges the code with GitHub server to server, reads { id, login }, discards GitHub's token
             → admin or view, from MINIQUEUE_ADMINS → creates an mqs_ session (stored as a SHA-256 hash)
             → redirects to the dashboard with a one-time login code (60 seconds), never the token itself
4. Dashboard → POST /api/v1/auth/exchange { code } → session token → sent as Bearer on every call
```

### Why these choices

- **Keys and tokens are stored as SHA-256 hashes.** A database leak does not leak working credentials. Keys are 256 random bits, so a fast hash is enough; BCrypt's slowness only matters for human-chosen passwords, and there are none.
- **Opaque session tokens, not JWTs.** A JWT cannot be revoked before it expires. A stored token can: sign-out deletes the row and the token is dead everywhere. The cost is one indexed lookup per request, which this single-service broker already pays for API keys.
- **Tokens in a header, not cookies.** The dashboard and the API are different sites on `onrender.com` (a public suffix), so browsers treat backend cookies as third-party and block them. A header also rules out CSRF: a malicious page cannot make the browser attach it.
- **Single-use codes wherever a credential must travel in a URL** (the sign-in redirect, the WebSocket handshake). URLs end up in logs and browser history; by then the code has been used or has expired.
- **Consumer identity comes from the key**, plus an optional `MiniQueue-Instance` header to tell replicas apart, never from the request body. One client cannot ack another client's message.

---

## Tech stack

| Layer | Technology | Why |
|---|---|---|
| Backend | Spring Boot 3.5, Java 21 | Production-grade framework, LTS release |
| Database | PostgreSQL 16 + Flyway | Durable storage, row-level locking (`FOR UPDATE SKIP LOCKED`), versioned schema |
| Security | Spring Security, GitHub OAuth | API keys and scopes for programs, GitHub sign-in for people |
| ORM | Spring Data JPA / Hibernate | Type-safe database access |
| SDK | Java 17 `HttpClient` + Jackson, Spring Boot starter | Retries with backoff, a polling consumer, `@MiniQueueListener` |
| Frontend | React + TypeScript, Tailwind CSS + shadcn/ui | Operations and management dashboard |
| Observability | Micrometer, Prometheus + Grafana | Metrics collection and visualization |
| Containers | Docker + Docker Compose | Single-command local stack |
| Testing | JUnit 5, Testcontainers, MockMvc | Integration tests against a real PostgreSQL, through the real security chain |
| CI/CD | GitHub Actions, Render deploy hooks | Test, deploy the tested commit, smoke-test production |

---

## Dashboard

Sign in with GitHub. Admins see everything; everyone else sees the same data without the buttons that change it.

### Operations

**Overview:** broker-wide message counts by status (pending, unacked, acked, dead). Refreshes on every broker event.

**Queue breakdown:** per-queue counts with color-coded status columns. Click a queue to inspect its messages inline.

**Dead letter queue:** dead messages with their last consumer, retry count and timestamp. Admins can replay a message back to `PENDING`.

**Live activity feed:** every broker event as it happens, through `/ws/monitor`. Capped at 50 events, with a connection indicator and automatic reconnect (a fresh ticket each time).

### Management (admin)

**Topics and queues:** create topics, bind queues to them, and inspect messages.

**Publish:** publish to any topic, with JSON validation and payload templates.

**Clients:** register the programs that publish or consume, choose their scopes, and copy the new key (shown once). Rotate a lost key or revoke a client; both take effect immediately.

---

## Design decisions

### Why PostgreSQL instead of an in-memory store

Messages must survive a broker restart. An in-memory store like Redis would lose every unacked message on a crash. PostgreSQL gives durable storage with ACID guarantees: a committed message is still there after a restart. That is the foundation of at-least-once delivery.

### Why UUID primary keys instead of auto-increment

Sequential IDs are generated by the database, which becomes a coordination point when several broker instances run in parallel. UUIDs are generated by the application, are globally unique, and cannot be guessed by callers.

### Why store the status as a string

`PENDING`, `UNACKED`, `ACKED` and `DEAD` as readable strings make the database self-documenting: `SELECT * FROM message WHERE status = 'DEAD'` needs no explanation. Integer enums save a few bytes and cost readability, which is not worth it at this scale.

### Why fan-out creates a separate row per queue

Each queue gets its own copy, so `email-queue` and `stock-queue` consume and ack independently, and one queue's progress never affects another. The trade-off is storage: a publish to a topic with 10 queues writes 10 rows. For a broker, that is the right trade-off. The rows are written in one transaction, so either every queue gets the message or none does.

### Why `published_at` is set by the database

If the application set the timestamp, clock skew between broker instances could reorder messages. The database has one clock, so `published_at DEFAULT now()` gives consistent ordering within a queue.

### Why the API is versioned with resource-style URLs

Everything lives under `/api/v1`, so a future v2 can change shapes without breaking v1 clients. URLs name resources and the HTTP method says what happens. Consume is a `POST`, not a `GET`, because it changes state (`PENDING → UNACKED`), and HTTP allows browsers and proxies to repeat a `GET`.

### Why WebSocket is used only for notification, not for claiming

Marking a message `UNACKED` at push time looks simpler, but if the consumer crashes between receiving the push and starting work, the message sits in `UNACKED` with nobody holding it until the redelivery timeout. It would also duplicate the claim logic in a second code path.

So the WebSocket only says "message X is ready". The consumer then calls the authenticated consume endpoint, the single, tested path that claims a message. If two consumers race for the same notification, the row lock on that path guarantees exactly one wins; the other gets `204`. The notification carries the message id only, because payloads may be confidential and pass through logging proxies.

### Why round-robin for WebSocket consumers

Sending every notification to every connected consumer would make them all race for one message. Round-robin spreads notifications evenly with a per-queue atomic counter: thread-safe, and no coordination between consumers.

### Why a dedicated `/ws/monitor` endpoint

`/ws/consume` is for delivery to one queue's consumers; `/ws/monitor` is for observability across the whole broker. Mixing them would make the dashboard subscribe to every queue and send consumers events they don't need. They also need different permissions: `consume:<queue>` versus `view`.

### Why dashboard aggregations run in the database

Counting messages in Java would mean transferring every row first; a queue with 100,000 messages would move 100,000 rows to produce one number. `GROUP BY ... COUNT()` computes the counts in one scan and returns a handful of integers.

---

## Concurrency guarantees

MiniQueue is safe with many competing consumers on one queue, and with more than one broker instance on the same database: **a message is never handed to two consumers at once, and an ack is never silently overwritten.** An integration test proves it.

### The race

The original consume path was *look, then act*: read the oldest `PENDING` message, then update it to `UNACKED` in a separate step. Two consumers polling at the same moment could read the same row, and both received the message. The redelivery scheduler had the same shape, so it could overwrite a message a consumer had just acked.

`@Transactional` does not fix this. A transaction makes changes all-or-nothing, but a plain `SELECT` takes no lock in PostgreSQL, so two transactions can still read the same row and both act on it.

### The fix

Every path that changes a message's state locks the row before deciding anything:

| Path | Lock | Why this lock |
|---|---|---|
| Consume | `SELECT ... FOR UPDATE SKIP LOCKED LIMIT 1` | Each consumer locks a different row and skips rows others hold, so N consumers work in parallel instead of queueing behind one row |
| Redelivery scheduler | `FOR UPDATE SKIP LOCKED`, in batches | Two broker instances never redeliver the same message, and a message that is mid-ack is skipped, not overwritten |
| Ack / nack / replay | `FOR UPDATE` (pessimistic write) | These target one message, so they *wait* for the lock and then check its real, current status |

The lock is held only for the short claim transaction, never while the consumer works. After the claim, `UNACKED` with a `consumer_id` and an `unacked_at` timestamp acts as a lease: if it isn't acked before the timeout, the scheduler returns it to `PENDING`, and a late ack from the original consumer is rejected by the `consumer_id` check (fencing).

The same look-then-act race existed when creating topics and queues; there, a `UNIQUE` constraint is the fix, and a duplicate gets `409`.

### The proof

`ConcurrencyIntegrationTest` starts a real PostgreSQL 16 with Testcontainers, publishes 100 messages, releases 10 consumer threads at the same instant, and asserts that no message was delivered twice, every message was delivered, and the database shows 0 `PENDING` and 100 `UNACKED`. Removing `SKIP LOCKED` makes the test fail with duplicates, which shows it detects the race rather than passing by luck.

### The trade-off

`SKIP LOCKED` gives approximately FIFO delivery, not strict FIFO. With several consumers, a later message can finish before an earlier one, as with an SQS standard queue. Strict per-key ordering would need partitioning, which MiniQueue does not implement.

---

## Known limitations

- **One-time codes live in memory.** WebSocket tickets and sign-in codes work for a single broker instance; several instances would need them in PostgreSQL or Redis. (Message delivery itself is already safe across instances.)
- **WebSocket notifications are per instance.** The session registry is in memory, so a push only reaches consumers connected to the instance that took the publish. Polling consumers are unaffected.
- **Open WebSockets outlive sign-out and revocation.** Credentials are checked at the handshake only.
- **Dashboard roles are decided at sign-in.** Removing someone from `MINIQUEUE_ADMINS` takes effect when their session expires (at most 8 hours).
- **No strict ordering or partitioning.** Delivery across competing consumers is approximately FIFO.
- **No retention yet.** Acked and dead messages are never deleted, and the per-queue message listing is not paginated.
- **No rate limiting yet**, and the payload size limit is checked after the request body has been read.
- **Timestamps have no time zone** (`LocalDateTime`); they are correct while the server and database run in UTC, as they do on Render.
- **Unbenchmarked at scale.** PostgreSQL was chosen for correctness over a purpose-built log; where write throughput or lock contention becomes the bottleneck has not been measured.

---

## API reference

All REST endpoints are versioned under `/api/v1`. Programs send `Authorization: Bearer mq_<api key>`; the dashboard sends `Authorization: Bearer mqs_<session token>`.

### Broker

| Method | Path | Body | Needs | Purpose |
|---|---|---|---|---|
| `POST` | `/api/v1/topics/{topic}/messages` | `{ "payload": "{\"orderId\": 42}" }` | `publish:{topic}` | Publish to every queue bound to the topic |
| `POST` | `/api/v1/queues/{queue}/consume` | | `consume:{queue}` | Claim the oldest pending message: `200` with it, or `204` if none |
| `POST` | `/api/v1/messages/{id}/ack` | | the consumer holding it | Mark processed |
| `POST` | `/api/v1/messages/{id}/nack` | `{ "requeue": true }` (optional) | the consumer holding it | Reject: `true` (default) retries, `false` moves it to the DLQ |
| `POST` | `/api/v1/topics` | `{ "name": "order.placed" }` | `admin` | Create a topic |
| `POST` | `/api/v1/queues` | `{ "name": "email-queue", "topic_name": "order.placed" }` | `admin` | Create a queue bound to a topic |
| `GET` | `/api/v1/topics`, `/api/v1/queues` | | `view` | List topics / queues |
| `GET` | `/api/v1/queues/{queue}/messages` | | `view` | Messages in a queue |
| `GET` | `/api/v1/dashboard/overview`, `/queues`, `/dlq` | | `view` | Counts by status, per queue, and dead messages |
| `POST` | `/api/v1/dashboard/dlq/{id}/replay` | | `admin` | Move a dead message back to `PENDING` |

The optional `MiniQueue-Instance: <id>` header on consume, ack and nack tells replicas that share one key apart. Topic and queue names are lowercased and must be 1 to 100 characters from `a-z`, `0-9`, `.`, `_` and `-`. Payloads are limited to 256 KB by default (`miniqueue.limits.max-payload-bytes`).

### Clients (API keys)

| Method | Path | Body | Needs | Purpose |
|---|---|---|---|---|
| `POST` | `/api/v1/clients` | `{ "name": "email-service", "scopes": ["consume:email-queue"] }` | `admin` | Register; the response holds `api_key`, shown once |
| `GET` | `/api/v1/clients` | | `admin` | Active clients: key prefixes and last use, never keys |
| `POST` | `/api/v1/clients/{name}/rotate` | | `admin` | New key; the old one stops working immediately |
| `DELETE` | `/api/v1/clients/{name}` | | `admin` | Revoke |

### Dashboard sign-in

| Method | Path | Needs | Purpose |
|---|---|---|---|
| `GET` | `/api/v1/auth/github/login` | nobody | Starts GitHub sign-in (a browser navigation) |
| `GET` | `/api/v1/auth/github/callback` | nobody | GitHub returns here; redirects to the dashboard |
| `POST` | `/api/v1/auth/exchange` | a login code | `{ "code": "lc_..." }` → session token |
| `GET` | `/api/v1/auth/me` | any credential | Who am I, and which scopes |
| `POST` | `/api/v1/auth/logout` | a session | Ends the session everywhere |
| `POST` | `/api/v1/auth/ws-ticket` | any credential | A 30-second, single-use WebSocket ticket |

### Operations

| Path | Needs |
|---|---|
| `/actuator/health`, `/actuator/info` (deployed commit) | nobody |
| `/actuator/prometheus` | `metrics` or `admin` |
| `/swagger-ui/index.html` | nobody (click **Authorize** and paste a key to try calls) |

### WebSocket

```
/ws/consume?queue=email-queue&ticket=wst_...   needs consume:email-queue
/ws/monitor?ticket=wst_...                     needs view or admin
```

Programs that can set headers may send `Authorization: Bearer mq_...` on the handshake instead of a ticket. Connections are only accepted from the allowed origins (`CORS_ALLOWED_ORIGINS`).

Consumer notification:
```json
{ "queue": "email-queue", "message_id": "3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b90" }
```

Monitor event:
```json
{
  "eventType": "PUBLISHED",
  "queueName": "email-queue",
  "detail": "Message 3f2a9c1e-... published to email-queue",
  "timestamp": "2026-10-25T10:00:00"
}
```

Event types: `CREATED`, `PUBLISHED`, `CONSUMED`, `ACKED`, `NACKED`, `REDELIVERED`, `DEAD`, `REPLAYED`.

### Errors

Every error has the same shape:
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
| `401` | Missing, invalid, expired or revoked credential | No, fix the key or sign in again |
| `403` | Valid credential, but its scopes don't allow this | No, an admin must grant the scope |
| `404` | Topic, queue or message does not exist | No |
| `409` | Conflicts with current state: duplicate name, message not `UNACKED`, held by another consumer | No |
| `413` | Payload over the size limit | No, send less |
| `500` | Unexpected server failure (details are logged, never returned) | Yes, with backoff |

---

## Running locally

### Prerequisites

- Java 21, and Maven (or the bundled `./mvnw`)
- PostgreSQL 16
- Node.js 20.19+ or 22
- Docker, or a compatible runtime such as Rancher Desktop in `dockerd` mode: needed for the integration tests and for Docker Compose
- A GitHub account, for signing in to the dashboard

### Step 1: create a GitHub OAuth app

GitHub allows one callback URL per OAuth app, so use one app for local development and another for production.

1. GitHub → **Settings → Developer settings → OAuth Apps → New OAuth App**
2. Homepage URL: `http://localhost:5173`
3. Authorization callback URL: `http://localhost:8080/api/v1/auth/github/callback`
4. Register, copy the **Client ID**, then **Generate a new client secret** and copy it (it is shown once).

### Step 2: configure and start the backend

The backend reads its settings from environment variables; nothing secret goes into `application.yaml`.

| Variable | Example | Purpose |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/miniqueue` | Database |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | `postgres` / `...` | Database credentials |
| `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` | from step 1 | Dashboard sign-in |
| `MINIQUEUE_ADMINS` | `your-github-username` | Comma-separated GitHub usernames that get `admin` |
| `MINIQUEUE_PUBLIC_VIEWER` | `true` (default) | `false`: only admins may sign in |
| `MINIQUEUE_DASHBOARD_URL` | `http://localhost:5173` (default) | Where sign-in returns to |
| `GITHUB_CALLBACK_URL` | `http://localhost:8080/api/v1/auth/github/callback` (default) | Must match the OAuth app |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` (default) | Origins allowed to call the API and open WebSockets |

Create the database (`CREATE DATABASE miniqueue;`); Flyway creates the tables on first start. Then:

```bash
cd backend
export SPRING_DATASOURCE_PASSWORD=your_password
export GITHUB_CLIENT_ID=... GITHUB_CLIENT_SECRET=... MINIQUEUE_ADMINS=your-github-username
./mvnw spring-boot:run
```

In IntelliJ, put the same variables in **Run → Edit Configurations → MiniqueueApplication → Environment variables**.

### Step 3: start the dashboard and sign in

```bash
cd ui
npm install
npm run dev
```

Open `http://localhost:5173` and click **Sign in with GitHub**. The Vite dev server proxies `/api` and `/ws` to `localhost:8080`.

### Step 4: register your programs

On the **Clients** page, register one client per program and copy its key (shown once):

| Client | Scopes | Used by |
|---|---|---|
| `scripts` | `admin` | The load test, the smoke test and the SDK live checks (`ADMIN_KEY` / `MINIQUEUE_ADMIN_KEY`) |
| `prometheus` | `metrics` | Prometheus, from `monitoring/miniqueue-api-key` (git-ignored) |
| e.g. `shop` | `publish:order.placed` | A producer |
| e.g. `email-service` | `consume:email-queue` | A consumer |

Then, with a topic `order.placed` and a queue `email-queue` created on the Manage page:

```bash
B=http://localhost:8080
SHOP=mq_key_of_shop
MAILER=mq_key_of_email_service

curl -s -X POST $B/api/v1/topics/order.placed/messages -H "Authorization: Bearer $SHOP" \
  -H "Content-Type: application/json" -d '{"payload":"{\"orderId\":42}"}'

curl -s -X POST $B/api/v1/queues/email-queue/consume -H "Authorization: Bearer $MAILER"
curl -s -X POST $B/api/v1/messages/<message_id>/ack -H "Authorization: Bearer $MAILER"
```

For real programs, use the [Java SDK](sdk/README.md) instead of `curl`.

### Docker Compose

`docker compose up --build` starts PostgreSQL, the backend, the dashboard (nginx, on http://localhost), Prometheus (`:9090`) and Grafana (`:3000`). Put the variables above in a git-ignored `.env` file next to `docker-compose.yml`, plus `POSTGRES_PASSWORD`. Sign-in through `http://localhost` needs its own OAuth app with the callback `http://localhost/api/v1/auth/github/callback`, and `MINIQUEUE_DASHBOARD_URL=http://localhost`.

---

## Testing

### Automated tests

```bash
cd backend && ./mvnw test   # needs Docker: each class starts its own throwaway PostgreSQL
cd sdk && ./mvnw test       # in-process stub servers: no Docker, no broker
cd ui && npm run build      # type-checks and builds the dashboard
```

| Test | Proves |
|---|---|
| `ConcurrencyIntegrationTest` | 10 consumers racing over 100 messages never get the same message twice |
| `TopicQueueIntegrityTest` | Concurrent creates make exactly one topic; names are validated; the payload limit counts bytes |
| `SecurityIntegrationTest` | Through the real filter chain: 401 vs 403, scopes and wildcards, identity from the key, rotation and revocation |
| `DashboardAuthIntegrationTest` | GitHub sign-in with GitHub mocked: state check, admin vs viewer, single-use codes and tickets, sign-out, metrics scope |
| SDK `MiniQueueClientTest`, `RetryPolicyTest`, `MiniQueueConsumerTest` | HTTP contract, error mapping, retry backoff, the polling consumer |
| Starter `MiniQueueAutoConfigurationTest` | `@MiniQueueListener` discovery, ack/nack, startup failures, instance ids |

### Against a running broker

```bash
# 28 end-to-end checks: auth, scopes, publish → consume → ack, revocation, metrics, CORS, dashboard routes
BACKEND_URL=http://localhost:8080 ADMIN_KEY=mq_key_of_scripts ./scripts/smoke-test.sh

# Burst of parallel publishes and consumes (watch the dashboard's live feed while it runs)
ADMIN_KEY=mq_key_of_scripts ./load-test.sh
```

The SDK's `LiveBrokerCheck`, `LiveConsumerCheck` and `LiveStarterCheck` (in `sdk/`) run against a live broker with `MINIQUEUE_ADMIN_KEY` set to the same admin key.

### CI/CD

| Workflow | When | What |
|---|---|---|
| [`ci.yml`](.github/workflows/ci.yml) | Every pull request and push to `main` | Backend tests (Testcontainers), SDK tests, dashboard build |
| | Push to `main`, all green | Deploys that exact commit to Render through deploy hooks, waits until `/actuator/info` reports it, then runs the smoke test |
| [`smoke.yml`](.github/workflows/smoke.yml) | Daily at 06:00 IST, and on demand | The smoke test against production |

Render's auto-deploy is off: production only ever runs a commit that passed the tests.

---

## Observability

Prometheus scrapes `/actuator/prometheus` every 15 seconds, authenticating with a `metrics`-scoped key read from a credentials file:

| Metric | Type | Description |
|---|---|---|
| `miniqueue_messages_published_total` | Counter | Messages published |
| `miniqueue_messages_consumed_total` | Counter | Messages consumed |
| `miniqueue_messages_acked_total` | Counter | Messages acknowledged |
| `miniqueue_messages_dead_total` | Counter | Messages dead-lettered |
| `miniqueue_messages_redelivered_total` | Counter | Messages redelivered |
| `miniqueue_queue_depth{queue="..."}` | Gauge | Pending messages per queue |

Grafana at `http://localhost:3000` connects to Prometheus automatically when running with Docker Compose.