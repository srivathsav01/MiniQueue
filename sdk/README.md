# MiniQueue Java SDK

Java client and Spring Boot starter for [MiniQueue](../README.md), a PostgreSQL-backed message broker with at-least-once delivery.

| Module | Use it when |
|---|---|
| `miniqueue-spring-boot-starter` | You have a Spring Boot app. Annotate a method, set two properties, done. |
| `miniqueue-client` | Any other Java 17+ app. Plain Java, one dependency (Jackson). |

## Installation

Releases are built by [JitPack](https://jitpack.io/#srivathsav01/MiniQueue). Add the repository once:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

Then the module you need:

```xml
<!-- Spring Boot apps -->
<dependency>
    <groupId>com.github.srivathsav01.MiniQueue</groupId>
    <artifactId>miniqueue-spring-boot-starter</artifactId>
    <version>v0.1.0</version>
</dependency>

<!-- Plain Java -->
<dependency>
    <groupId>com.github.srivathsav01.MiniQueue</groupId>
    <artifactId>miniqueue-client</artifactId>
    <version>v0.1.0</version>
</dependency>
```

## Getting an API key

Every call is authenticated. Ask the MiniQueue admin to register your application as a client with the scopes it needs:

```bash
curl -X POST https://<broker>/api/v1/clients \
  -H "Authorization: Bearer <admin key>" \
  -H "Content-Type: application/json" \
  -d '{"name":"email-service","scopes":["consume:email-queue","publish:email.sent"]}'
```

The response contains `api_key`. It is shown only this once, so store it in your secret manager or environment, never in source code.

| Scope | Allows |
|---|---|
| `publish:<topic>` | Publishing to that topic (`publish:*` for any topic) |
| `consume:<queue>` | Consuming, acking and nacking on that queue (`consume:*` for any queue) |
| `admin` | Everything, including managing clients |

## Spring Boot

```yaml
# application.yaml
miniqueue:
  client:
    base-url: http://localhost:8080
    api-key: ${MINIQUEUE_API_KEY}
```

```java
@Component
class EmailListener {

    @MiniQueueListener(queue = "email-queue")
    public void onOrderPlaced(String payload) {
        // Return normally: the message is acked.
        // Throw: it is nacked and redelivered, then moved to the dead-letter queue after max retries.
    }
}
```

Publish by injecting the auto-configured client anywhere:

```java
@Service
class OrderService {

    private final MiniQueueClient miniQueue;

    OrderService(MiniQueueClient miniQueue) {
        this.miniQueue = miniQueue;
    }

    void placeOrder(Order order) {
        miniQueue.publish("order.placed", toJson(order));
    }
}
```

A listener method must be non-private and take one parameter: `String` for the payload, or `ReceivedMessage` for the payload plus id, queue name and publish time. The queue name supports placeholders: `@MiniQueueListener(queue = "${app.queues.email}")`.

If `miniqueue.client.api-key` is missing, the application fails at startup with a message naming the property, instead of failing later on the first call.

### Configuration

| Property | Default | Meaning |
|---|---|---|
| `miniqueue.client.base-url` | `http://localhost:8080` | Broker URL |
| `miniqueue.client.api-key` | none (required) | API key from `POST /api/v1/clients` |
| `miniqueue.client.instance-id` | random per instance | Tells replicas that share one key apart (see below) |
| `miniqueue.client.connect-timeout` | `5s` | Time to open a connection |
| `miniqueue.client.request-timeout` | `10s` | Time to wait for a response |
| `miniqueue.client.retry.max-attempts` | `3` | Attempts per call (5xx and connection failures only); `1` disables retries |
| `miniqueue.client.retry.initial-backoff` | `200ms` | First retry wait, doubled each time, with random jitter |
| `miniqueue.client.retry.max-backoff` | `5s` | Cap on any single retry wait |
| `miniqueue.client.consumer.concurrency` | `1` | Worker threads per listener (overridable per annotation) |
| `miniqueue.client.consumer.min-poll-interval` | `100ms` | Poll interval while messages are flowing |
| `miniqueue.client.consumer.max-poll-interval` | `5s` | Poll interval ceiling while the queue is empty |
| `miniqueue.client.consumer.shutdown-timeout` | `30s` | How long shutdown waits for in-flight messages |
| `miniqueue.client.consumer.auto-startup` | `true` | Start listeners with the application |

## Plain Java

```java
MiniQueueClient client = MiniQueueClient.builder("http://localhost:8080")
        .apiKey(System.getenv("MINIQUEUE_API_KEY"))
        .build();

client.publish("order.placed", "{\"orderId\": 42}");

// One message at a time:
client.consume("email-queue").ifPresent(message -> {
    sendEmail(message.payload());
    client.ack(message.id());
});

// Or a managed polling loop:
MiniQueueConsumer consumer = MiniQueueConsumer
        .builder(client, "email-queue", message -> sendEmail(message.payload()))
        .concurrency(4)
        .build()
        .start();

// On shutdown:
consumer.close();
```

Create one `MiniQueueClient` per broker and share it: it is thread-safe and holds a pooled HTTP connection.

## Identity and instances

The broker takes your identity from the API key: a message you consume is recorded as held by your client's registered name, and only that client can ack or nack it. Requests cannot claim to be someone else.

When several copies of one service run with the same key, give each a distinct instance id. The broker records `email-service@<instance>`, so if copy A stalls and a message is redelivered to copy B, A's late ack is rejected instead of interfering. The starter does this automatically with a random id per instance; with the plain client, set `.instanceId("pod-2")` yourself, for example from the pod or host name. Instance ids follow the broker's name rules: `a-z`, `0-9`, `.`, `_`, `-`.

## Errors

Every failure is an unchecked `MiniQueueException` subclass that mirrors the broker's HTTP contract:

| Exception | HTTP | `isRetryable()` | What to do |
|---|---|---|---|
| `MiniQueueException.BadRequest` | 400 | no | Fix the request (invalid name, missing field) |
| `MiniQueueException.Unauthorized` | 401 | no | The key is missing, wrong or revoked: check configuration, or ask for a new key |
| `MiniQueueException.Forbidden` | 403 | no | The key lacks the scope: ask the admin to grant it |
| `MiniQueueException.NotFound` | 404 | no | The topic, queue or message does not exist |
| `MiniQueueException.Conflict` | 409 | no | The message is no longer held by you (already acked, or redelivered after the ack timeout) |
| `MiniQueueException.PayloadTooLarge` | 413 | no | Send a smaller payload (broker default limit: 256 KB) |
| `MiniQueueException.ServerError` | 5xx | yes | Retried automatically with backoff |
| `MiniQueueException.ConnectionFailed` | none | yes | Retried automatically with backoff |

An empty queue is not an error: `consume` returns `Optional.empty()` (HTTP 204).

`MiniQueueConsumer` never stops on these errors. It logs them and keeps polling at the slowest interval, so an admin can fix a missing scope without a restart. Exception messages never contain the API key.

## Delivery semantics

Delivery is **at-least-once**. A message can occasionally be processed twice, for example if an ack is lost or a handler runs longer than the broker's ack timeout. Write handlers to be idempotent: processing the same message twice should have the same effect as processing it once.

## Building from source

```bash
cd sdk
./mvnw test      # client + starter, in dependency order
./mvnw install   # also installs both JARs into ~/.m2 for local use
```

The live checks (`LiveBrokerCheck`, `LiveConsumerCheck`, `LiveStarterCheck`) run against a real broker and need `MINIQUEUE_ADMIN_KEY` set to its admin key. See the main README.