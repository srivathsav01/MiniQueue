package com.sri.miniqueue.serviceImpl;

import com.sri.miniqueue.dto.DashboardOverviewResponse;
import com.sri.miniqueue.dto.DlqMessageResponse;
import com.sri.miniqueue.dto.MessageStatusCount;
import com.sri.miniqueue.dto.QueueStatsResponse;
import com.sri.miniqueue.entity.Message;
import com.sri.miniqueue.entity.Queue;
import com.sri.miniqueue.exception.CustomException;
import com.sri.miniqueue.repository.MessageRepository;
import com.sri.miniqueue.repository.QueueRepository;
import com.sri.miniqueue.repository.TopicRepository;
import com.sri.miniqueue.service.DashboardService;
import com.sri.miniqueue.to.MessageStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final MessageRepository messageRepository;

    private final TopicRepository topicRepository;

    private final QueueRepository queueRepository;

    @Override
    public DashboardOverviewResponse getOverview() {
        List<MessageStatusCount> messages = messageRepository.countByQueueAndStatus();
        Map<String, Long> counts = new HashMap<>();
        long total=0;
        for (MessageStatusCount message : messages){
            counts.merge(message.getStatus().toLowerCase(),message.getCount(),Long::sum);
            total+=message.getCount();
        }
        return DashboardOverviewResponse.builder()
                .totalTopics(topicRepository.count())
                .totalQueues(queueRepository.count())
                .totalMessages(total)
                .pending(counts.getOrDefault("pending", 0L))
                .unacked(counts.getOrDefault("unacked", 0L))
                .acked(counts.getOrDefault("acked", 0L))
                .dead(counts.getOrDefault("dead", 0L))
                .build();
    }

    @Override
    public List<QueueStatsResponse> getQueueStats() {
        List<MessageStatusCount> messages = messageRepository.countByQueueAndStatus();
        Map<UUID, Queue> queueMap = queueRepository.findAll()
                .stream()
                .collect(Collectors.toMap(Queue::getId, q -> q));
        Map<UUID,List<MessageStatusCount>> stats = messages.stream()
                .collect(Collectors.groupingBy(MessageStatusCount::getQueueId));
        List<QueueStatsResponse> result = new ArrayList<>();
        stats.forEach((UUID id,List<MessageStatusCount> counts)-> {
            Queue q = queueMap.get(id);
            if (q != null) {
                QueueStatsResponse queueStatsResponse = new QueueStatsResponse();
                queueStatsResponse.setQueueName(q.getName());
                queueStatsResponse.setTopicName(q.getTopic().getName());
                for (MessageStatusCount count : counts) {
                    switch (count.getStatus().toLowerCase()) {
                        case "pending" -> queueStatsResponse.setPending(count.getCount());
                        case "unacked" -> queueStatsResponse.setUnacked(count.getCount());
                        case "acked" -> queueStatsResponse.setAcked(count.getCount());
                        case "dead" -> queueStatsResponse.setDead(count.getCount());
                    }
                }
                result.add(queueStatsResponse);
            }
        });
        return result;
    }

    @Override
    public List<DlqMessageResponse> getDeadLetterMessages() {
        return messageRepository.findDeadMessages(MessageStatus.DEAD)
                .stream()
                .map(msg-> DlqMessageResponse.builder()
                        .messageId(msg.getId())
                        .queueName(msg.getQueue().getName())
                        .payload(msg.getPayload())
                        .consumerId(msg.getConsumerId())
                        .retryCount(msg.getRetryCount())
                        .unackedAt(msg.getUnackedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public String replayMessage(UUID messageId) {
        Optional<Message> msg = messageRepository.findById(messageId);
        if(msg.isEmpty()) {
            throw new CustomException("No message found with id " + messageId);
        }
        if(msg.get().getStatus() != MessageStatus.DEAD) {
            throw new CustomException("Only DEAD messages can be replayed");
        }
        msg.get().setStatus(MessageStatus.PENDING);
        msg.get().setConsumerId(null);
        msg.get().setUnackedAt(null);
        messageRepository.save(msg.get());
        return "Reset Message "+messageId+" to PENDING";
    }
}
