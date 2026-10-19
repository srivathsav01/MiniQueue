package io.github.srivathsav01.miniqueue.spring;

import io.github.srivathsav01.miniqueue.client.MiniQueueClient;
import io.github.srivathsav01.miniqueue.client.RetryPolicy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

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
        MiniQueueProperties.Retry retry = properties.getRetry();
        return MiniQueueClient.builder(properties.getBaseUrl())
                .connectTimeout(properties.getConnectTimeout())
                .requestTimeout(properties.getRequestTimeout())
                .retryPolicy(RetryPolicy.of(retry.getMaxAttempts(), retry.getInitialBackoff(), retry.getMaxBackoff()))
                .build();
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