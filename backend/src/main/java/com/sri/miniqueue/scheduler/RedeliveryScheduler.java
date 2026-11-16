package com.sri.miniqueue.scheduler;

import com.sri.miniqueue.dto.ConsumeResponse;
import com.sri.miniqueue.entity.Message;
import com.sri.miniqueue.event.BrokerActivityEvent;
import com.sri.miniqueue.event.BrokerEventType;
import com.sri.miniqueue.event.NewMessageEvent;
import com.sri.miniqueue.repository.MessageRepository;
import com.sri.miniqueue.to.MessageStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@RequiredArgsConstructor
@Component
public class RedeliveryScheduler {

    private final MessageRepository messageRepository;

    private final ApplicationEventPublisher eventPublisher;

    @Value("${miniqueue.redelivery.max-retries}")
    private int retryCount;

    @Value("${miniqueue.redelivery.batch-size}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${miniqueue.redelivery.fixed-delay-ms}")
    @Transactional
    public void redeliverExpiredLeases() {
        List<Message> messages = this.messageRepository.claimExpiredLeases(
                MessageStatus.UNACKED.name(), LocalDateTime.now(), batchSize);
        if (messages.isEmpty()) {
            log.debug("No expired leases");
            return;
        }
        AtomicInteger deadCount = new AtomicInteger(0);
        messages.forEach(msg -> {
            int retries = (msg.getRetryCount() != null ? msg.getRetryCount() : 0) + 1;
            String queueName = msg.getQueue().getName();
            if (retries >= retryCount) {
                msg.setStatus(MessageStatus.DEAD);
                deadCount.incrementAndGet();
                eventPublisher.publishEvent(new BrokerActivityEvent(BrokerEventType.DEAD, queueName,
                        "Message " + msg.getId() + " dead. Max retries exceeded", LocalDateTime.now()));
            } else {
                msg.setStatus(MessageStatus.PENDING);
                msg.setUnackedAt(null);
                msg.setLeaseExpiresAt(null);
                msg.setConsumerId(null);
                eventPublisher.publishEvent(new BrokerActivityEvent(BrokerEventType.REDELIVERED, queueName,
                        "Message " + msg.getId() + " redelivered - retry " + retries, LocalDateTime.now()));
                // Wake a consumer instead of waiting for its next poll
                eventPublisher.publishEvent(new NewMessageEvent(
                        ConsumeResponse.builder().messageId(msg.getId()).queueName(queueName).build(), queueName));
            }
            msg.setRetryCount(retries);
        });
        log.info("Redelivering {} messages with expired leases", messages.size() - deadCount.get());
        if (deadCount.get() > 0) {
            log.warn("Messages moved to dead-letter queue: {}", deadCount.get());
        }
        this.messageRepository.saveAll(messages);
    }
}
