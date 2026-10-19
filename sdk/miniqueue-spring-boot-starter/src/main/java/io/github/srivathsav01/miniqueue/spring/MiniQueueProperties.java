package io.github.srivathsav01.miniqueue.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings under {@code miniqueue.client.*} in application.yaml.
 *
 * The prefix is miniqueue.client, not just miniqueue, so it can never collide with the
 * broker's own miniqueue.redelivery.* settings if both ever live in one place.
 */
@ConfigurationProperties(prefix = "miniqueue.client")
public class MiniQueueProperties {

    /** Root URL of the MiniQueue broker. */
    private String baseUrl = "http://localhost:8080";

    /** How long to wait to open a connection to the broker. */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /** How long to wait for a full response from the broker. */
    private Duration requestTimeout = Duration.ofSeconds(10);

    private final Retry retry = new Retry();

    private final Consumer consumer = new Consumer();

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public Retry getRetry() {
        return retry;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    /** Retries for failed calls (5xx responses and connection failures only). */
    public static class Retry {

        /** Total attempts per call, including the first. 1 disables retries. */
        private int maxAttempts = 3;

        /** Wait before the first retry; doubled for each further retry (plus random jitter). */
        private Duration initialBackoff = Duration.ofMillis(200);

        /** Upper limit for any single wait between retries. */
        private Duration maxBackoff = Duration.ofSeconds(5);

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getInitialBackoff() {
            return initialBackoff;
        }

        public void setInitialBackoff(Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
        }

        public Duration getMaxBackoff() {
            return maxBackoff;
        }

        public void setMaxBackoff(Duration maxBackoff) {
            this.maxBackoff = maxBackoff;
        }
    }

    /** Defaults for every @MiniQueueListener. */
    public static class Consumer {

        /**
         * Consumer id sent to the broker. Leave unset to generate one that is unique to this
         * application instance (spring.application.name plus a random suffix). If you set it,
         * give each running instance a different value: the broker's fencing check, which
         * rejects a late ack after a message was redelivered, compares consumer ids.
         */
        private String consumerId;

        /** Worker threads per listener, unless the annotation overrides it. */
        private int concurrency = 1;

        /** Poll interval right after a message was received. */
        private Duration minPollInterval = Duration.ofMillis(100);

        /** Longest poll interval while the queue stays empty. */
        private Duration maxPollInterval = Duration.ofSeconds(5);

        /** How long shutdown waits for in-flight messages before interrupting them. */
        private Duration shutdownTimeout = Duration.ofSeconds(30);

        /** Start listeners automatically when the application starts. */
        private boolean autoStartup = true;

        public String getConsumerId() {
            return consumerId;
        }

        public void setConsumerId(String consumerId) {
            this.consumerId = consumerId;
        }

        public int getConcurrency() {
            return concurrency;
        }

        public void setConcurrency(int concurrency) {
            this.concurrency = concurrency;
        }

        public Duration getMinPollInterval() {
            return minPollInterval;
        }

        public void setMinPollInterval(Duration minPollInterval) {
            this.minPollInterval = minPollInterval;
        }

        public Duration getMaxPollInterval() {
            return maxPollInterval;
        }

        public void setMaxPollInterval(Duration maxPollInterval) {
            this.maxPollInterval = maxPollInterval;
        }

        public Duration getShutdownTimeout() {
            return shutdownTimeout;
        }

        public void setShutdownTimeout(Duration shutdownTimeout) {
            this.shutdownTimeout = shutdownTimeout;
        }

        public boolean isAutoStartup() {
            return autoStartup;
        }

        public void setAutoStartup(boolean autoStartup) {
            this.autoStartup = autoStartup;
        }
    }
}