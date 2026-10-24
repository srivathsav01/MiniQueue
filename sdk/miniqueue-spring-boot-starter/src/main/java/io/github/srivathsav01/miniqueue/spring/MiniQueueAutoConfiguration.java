package io.github.srivathsav01.miniqueue.spring;

import io.github.srivathsav01.miniqueue.client.MiniQueueClient;
import io.github.srivathsav01.miniqueue.client.RetryPolicy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.UUID;

/**
 * Picked up automatically by Spring Boot because this class is listed in
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports.
 * Users never import or reference it.
 */
@AutoConfiguration
@EnableConfigurationProperties(MiniQueueProperties.class)
public class MiniQueueAutoConfiguration {

    /**
     * A ready-to-use client built from miniqueue.client.* settings. Inject it anywhere to
     * publish messages. @ConditionalOnMissingBean means that if the application defines its
     * own MiniQueueClient bean, that one is used instead: users can always override.
     */
    @Bean
    @ConditionalOnMissingBean
    public MiniQueueClient miniQueueClient(MiniQueueProperties properties) {
        // Fail at startup, naming the property, instead of on the first publish with a 401.
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("miniqueue.client.api-key is required. Ask your MiniQueue admin for a key"
                    + " (POST /api/v1/clients) and pass it in through the environment, e.g."
                    + " miniqueue.client.api-key: ${MINIQUEUE_API_KEY}");
        }
        MiniQueueProperties.Retry retry = properties.getRetry();
        return MiniQueueClient.builder(properties.getBaseUrl())
                .apiKey(properties.getApiKey())
                .instanceId(instanceId(properties))
                .connectTimeout(properties.getConnectTimeout())
                .requestTimeout(properties.getRequestTimeout())
                .retryPolicy(RetryPolicy.of(retry.getMaxAttempts(), retry.getInitialBackoff(), retry.getMaxBackoff()))
                .build();
    }

    /**
     * The configured instance id, or a random one. Random (not the host name) because two
     * copies of an app on one machine share a host name, and then their acks would no
     * longer be fenced from each other.
     */
    private static String instanceId(MiniQueueProperties properties) {
        String configured = properties.getInstanceId();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Finds @MiniQueueListener methods and runs a consumer for each.
     *
     * It is a BeanPostProcessor, and those are created very early, before most other beans
     * exist. Two rules follow: the method is static (so creating it does not first require
     * creating this configuration class), and it receives ObjectProviders rather than the
     * beans themselves (so the client and properties are only looked up later, when the
     * listeners start, instead of being forced into existence too early).
     */
    @Bean
    public static MiniQueueListenerRegistrar miniQueueListenerRegistrar(
            ObjectProvider<MiniQueueClient> client, ObjectProvider<MiniQueueProperties> properties) {
        return new MiniQueueListenerRegistrar(client, properties);
    }
}