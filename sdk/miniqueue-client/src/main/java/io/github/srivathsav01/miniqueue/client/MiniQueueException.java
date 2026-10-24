package io.github.srivathsav01.miniqueue.client;

/**
 * Base type for every failure the client reports. It is unchecked, like most modern
 * Java SDKs, so callers handle only the cases they care about:
 *
 * <pre>{@code
 * try {
 *     client.ack(message.id(), "worker-1");
 * } catch (MiniQueueException.Conflict e) {
 *     // already acked, or redelivered to another consumer: safe to ignore
 * }
 * }</pre>
 *
 * The subclasses mirror the broker's HTTP contract: 4xx means the request itself is
 * wrong, 5xx and connection failures mean trying again later may work.
 */
public class MiniQueueException extends RuntimeException {

    /** The HTTP status, or 0 when no response was received at all. */
    private final int statusCode;

    MiniQueueException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }

    /**
     * True when sending the same request again might succeed.
     *
     * Note this means "might succeed", not "safe to repeat": if a publish timed out
     * after the broker had already stored it, retrying it stores a second copy. That is
     * normal at-least-once behaviour, and consumers should be written to tolerate it.
     */
    public boolean isRetryable() {
        return false;
    }

    /** HTTP 400: malformed or invalid request. Fix the request; retrying will not help. */
    public static final class BadRequest extends MiniQueueException {
        BadRequest(String message) {
            super(message, 400, null);
        }
    }

    /**
     * HTTP 401: the API key is missing, wrong or revoked. Not retryable: the same key will
     * fail the same way. Check the configured key, or ask an admin to rotate it.
     */
    public static final class Unauthorized extends MiniQueueException {
        Unauthorized(String message) {
            super(message, 401, null);
        }
    }

    /**
     * HTTP 403: the key is valid but its scopes do not allow this, e.g. publishing to a topic
     * it has no publish scope for. Not retryable: an admin has to grant the scope.
     */
    public static final class Forbidden extends MiniQueueException {
        Forbidden(String message) {
            super(message, 403, null);
        }
    }

    /** HTTP 404: the topic, queue or message does not exist. */
    public static final class NotFound extends MiniQueueException {
        NotFound(String message) {
            super(message, 404, null);
        }
    }

    /**
     * HTTP 409: the request conflicts with the message's current state, for example
     * acking a message that is no longer UNACKED or that now belongs to another consumer.
     */
    public static final class Conflict extends MiniQueueException {
        Conflict(String message) {
            super(message, 409, null);
        }
    }

    /** HTTP 413: the payload is over the broker's size limit. Send a smaller message; retrying will not help. */
    public static final class PayloadTooLarge extends MiniQueueException {
        PayloadTooLarge(String message) {
            super(message, 413, null);
        }
    }

    /** HTTP 5xx: the broker failed. Retrying later, with backoff, may succeed. */
    public static final class ServerError extends MiniQueueException {
        ServerError(String message, int statusCode) {
            super(message, statusCode, null);
        }

        @Override
        public boolean isRetryable() {
            return true;
        }
    }

    /** No response at all: connection refused, DNS failure, or timeout. Retrying may succeed. */
    public static final class ConnectionFailed extends MiniQueueException {
        ConnectionFailed(String message, Throwable cause) {
            super(message, 0, cause);
        }

        @Override
        public boolean isRetryable() {
            return true;
        }
    }
}