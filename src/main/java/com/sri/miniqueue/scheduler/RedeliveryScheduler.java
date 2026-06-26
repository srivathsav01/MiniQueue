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

@Slf4j
@RequiredArgsConstructor
@Component
public class RedeliveryScheduler {

    private final MessageRepository messageRepository;

    @Value("${miniqueue.redelivery.timeout-minutes}")
    private int timeoutMinutes;

    @Scheduled(fixedDelayString = "${miniqueue.redelivery.fixed-delay-ms}")
    public void resetUnackedMessages(){
        List<Message> messages = this.messageRepository.findStuckMessages(MessageStatus.UNACKED, LocalDateTime.now().minusMinutes(timeoutMinutes));
        if(messages.isEmpty()){
            log.info("No stuck messages found");
            return;
        }
        messages.forEach(msg->{
            msg.setStatus(MessageStatus.PENDING);
            msg.setUnackedAt(null);
            msg.setConsumerId(null);
        });
        log.info("Redelivering {} stuck messages", messages.size());
        this.messageRepository.saveAll(messages);
    }
}
