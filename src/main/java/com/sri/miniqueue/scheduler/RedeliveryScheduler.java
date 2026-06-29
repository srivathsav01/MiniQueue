package com.sri.miniqueue.scheduler;

import com.sri.miniqueue.entity.Message;
import com.sri.miniqueue.repository.MessageRepository;
import com.sri.miniqueue.to.MessageStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@RequiredArgsConstructor
@Component
public class RedeliveryScheduler {

    private final MessageRepository messageRepository;

    @Value("${miniqueue.redelivery.timeout-minutes}")
    private int timeoutMinutes;

    @Value("${miniqueue.redelivery.max-tries}")
    private int retryCount;

    @Scheduled(fixedDelayString = "${miniqueue.redelivery.fixed-delay-ms}")
    public void resetUnackedMessages(){
        List<Message> messages = this.messageRepository.findStuckMessages(MessageStatus.UNACKED, LocalDateTime.now().minusMinutes(timeoutMinutes));
        if(messages.isEmpty()){
            log.info("No stuck messages found");
            return;
        }
        AtomicInteger deadCount= new AtomicInteger(0);
        messages.forEach(msg->{
            Integer retries = msg.getRetryCount()+1;
            if(retries>=retryCount){
                msg.setStatus(MessageStatus.DEAD);
                deadCount.incrementAndGet();
            }
            else{
                msg.setStatus(MessageStatus.PENDING);
                msg.setUnackedAt(null);
                msg.setConsumerId(null);
            }
            msg.setRetryCount(retries);
        });
        log.info("Redelivering {} stuck messages", messages.size()- deadCount.get());
        log.warn("Messages moved to dead-letter queue: {}", deadCount.get());
        this.messageRepository.saveAll(messages);
    }
}
