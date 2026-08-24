package com.sri.miniqueue.metrics;

import com.sri.miniqueue.repository.MessageRepository;
import com.sri.miniqueue.to.MessageStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
public class BrokerMetrics {

    private final Counter publishedCounter;
    private final Counter consumedCounter;
    private final Counter ackedCounter;
    private final Counter nackedCounter;
    private final Counter deadCounter;
    private final Counter redeliveredCounter;
    private final MeterRegistry registry;

    public BrokerMetrics(MeterRegistry registry){
        this.registry = registry;
        this.publishedCounter = Counter.builder("miniqueue.messages.published")
                .description("Total messages published")
                .register(registry);

        this.consumedCounter = Counter.builder("miniqueue.messages.consumed")
                .description("Total messages consumed")
                .register(registry);

        this.ackedCounter = Counter.builder("miniqueue.messages.acked")
                .description("Total messages acknowledged")
                .register(registry);

        this.nackedCounter = Counter.builder("miniqueue.messages.nacked")
                .description("Total messages negatively acknowledged")
                .register(registry);

        this.deadCounter = Counter.builder("miniqueue.messages.dead")
                .description("Total messages moved to dead letter queue")
                .register(registry);

        this.redeliveredCounter = Counter.builder("miniqueue.messages.redelivered")
                .description("Total messages redelivered")
                .register(registry);
    }

    public void incrementPublished() { publishedCounter.increment(); }
    public void incrementConsumed() { consumedCounter.increment(); }
    public void incrementAcked() { ackedCounter.increment(); }
    public void incrementNacked() { nackedCounter.increment(); }
    public void incrementDead() { deadCounter.increment(); }
    public void incrementRedelivered() { redeliveredCounter.increment(); }

    public void registerQueueDepthGauge(String queueName, MessageRepository messageRepository) {
        Gauge.builder("miniqueue.queue.depth", messageRepository,
                        repo -> repo.countByQueueNameAndStatus(queueName, MessageStatus.PENDING))
                .description("Current pending message count")
                .tag("queue", queueName)
                .register(registry);
    }
}
