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

    /**
     * API key this application authenticates with (required). Ask the MiniQueue admin for
     * one (POST /api/v1/clients); its scopes decide what this application may publish and
     * consume. Pass it in through the environment, e.g. ${MINIQUEUE_API_KEY}, never commit it.
     */
    private String apiKey;

    /**
     * Tells instances of this application apart when several run with the same key, so the
     * broker can reject a late ack from one instance after a message was redelivered to
     * another. Leave unset to generate a random id per instance; set it to a stable,
     * unique value such as the pod name if you want readable ids on the dashboard.
     */
    private String instanceId;

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

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
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