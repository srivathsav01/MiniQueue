package com.sri.miniqueue.metrics;

import com.sri.miniqueue.repository.MessageRepository;
import com.sri.miniqueue.repository.QueueRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MetricsInitializer {
    private final QueueRepository queueRepository;
    private final BrokerMetrics brokerMetrics;
    private final MessageRepository messageRepository;

    @PostConstruct
    public void initQueueGauges() {
        queueRepository.findAll().forEach(queue ->
                brokerMetrics.registerQueueDepthGauge(queue.getName(), messageRepository)
        );
    }
}
