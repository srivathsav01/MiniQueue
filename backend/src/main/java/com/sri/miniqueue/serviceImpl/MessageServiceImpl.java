package com.sri.miniqueue.serviceImpl;

import com.sri.miniqueue.dto.ConsumeResponse;
import com.sri.miniqueue.dto.MessageResponse;
import com.sri.miniqueue.dto.QueueResponse;
import com.sri.miniqueue.dto.TopicResponse;
import com.sri.miniqueue.entity.Message;
import com.sri.miniqueue.entity.Queue;
import com.sri.miniqueue.entity.Topic;
import com.sri.miniqueue.event.BrokerActivityEvent;
import com.sri.miniqueue.event.BrokerEventType;
import com.sri.miniqueue.event.NewMessageEvent;
import com.sri.miniqueue.exception.*;
import com.sri.miniqueue.mapper.MessageMapper;
import com.sri.miniqueue.mapper.QueueMapper;
import com.sri.miniqueue.mapper.TopicMapper;
import com.sri.miniqueue.metrics.BrokerMetrics;
import com.sri.miniqueue.repository.MessageRepository;
import com.sri.miniqueue.repository.QueueRepository;
import com.sri.miniqueue.repository.TopicRepository;
import com.sri.miniqueue.service.MessageService;
import com.sri.miniqueue.to.MessageStatus;
import com.sri.miniqueue.utils.Names;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class MessageServiceImpl implements MessageService {

    private final MessageRepository messageRepository;

    private final QueueRepository queueRepository;

    private final TopicRepository topicRepository;

    private final ApplicationEventPublisher eventPublisher;

    private final BrokerMetrics brokerMetrics;

    private final TopicMapper topicMapper;

    private final QueueMapper queueMapper;

    private final MessageMapper messageMapper;

    @Value("${miniqueue.redelivery.max-retries}")
    private int maxRetries;

    @Value("${miniqueue.limits.max-payload-bytes:262144}")
    private int maxPayloadBytes;

    @Override
    @Transactional
    public Topic createTopic(String rawName) {
        String name = Names.normalize(rawName, "Topic");
        Topic topic = new Topic();
        topic.setName(name);
        try {
            // saveAndFlush, not save: the INSERT runs now, so a duplicate fails inside this
            // try block instead of later at commit, where we could no longer catch it.
            topic = topicRepository.saveAndFlush(topic);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Topic already exists with the name " + name);
        }
        eventPublisher.publishEvent(new BrokerActivityEvent(BrokerEventType.CREATED, "", "New Topic " + name + " created", LocalDateTime.now()));
        return topic;
    }

    @Override
    public List<TopicResponse> getAllTopics() throws CustomException {
        List<Topic> topics = this.topicRepository.findAll();
       return topics.stream().map(topicMapper::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public Queue createQueue(String rawName, String rawTopicName) {
        String name = Names.normalize(rawName, "Queue");
        String topicName = Names.normalize(rawTopicName, "Topic");
        Topic topic = topicRepository.findTopicByName(topicName)
                .orElseThrow(() -> new ResourceNotFoundException("No Topic found with the name " + topicName));
        Queue queue = Queue.builder().name(name).topic(topic).build();
        try {
            queue = queueRepository.saveAndFlush(queue);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Queue already exists with the name " + name);
        }
        // Only after the insert succeeded: before, a failed create still registered a gauge.
        brokerMetrics.registerQueueDepthGauge(name, messageRepository);
        eventPublisher.publishEvent(new BrokerActivityEvent(BrokerEventType.CREATED, name, "New Queue " + name + " created for topic " + topicName, LocalDateTime.now()));
        return queue;
    }

    @Override
    public List<QueueResponse> getAllQueues() throws CustomException {
        List<Queue> queues = this.queueRepository.findAll();
        return queues.stream().map(queueMapper::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void publishMessage(String rawTopicName, String payload) throws CustomException {
        String topicName = Names.normalize(rawTopicName, "Topic");
        if (payload == null) {
            throw new CustomException("payload is required");
        }
        // Count bytes, not characters: "é" is 1 char but 2 bytes, and storage and network pay for bytes.
        int size = payload.getBytes(StandardCharsets.UTF_8).length;
        if (size > maxPayloadBytes) {
            throw new PayloadTooLargeException("Payload is " + size + " bytes; the limit is " + maxPayloadBytes);
        }
        Optional<Topic> topic = this.topicRepository.findTopicByName(rawTopicName.toLowerCase());
        if(topic.isEmpty()){
            throw new ResourceNotFoundException("No Topic found with the name "+ rawTopicName);
        }
        List<Queue> queueList = this.queueRepository.findByTopic(topic.get());
        if(queueList.isEmpty()){
            throw new CustomException("No Queues are bound to the topic "+ rawTopicName);
        }
        queueList.forEach(queue->{
            Message message = new Message();
            message.setPayload(payload);
            message.setQueue(queue);
            message.setStatus(MessageStatus.PENDING);
            Message saved = this.messageRepository.save(message);
            ConsumeResponse response = ConsumeResponse.builder()
                    .messageId(saved.getId())
                    .payload(saved.getPayload())
                    .queueName(queue.getName())
                    .publishedAt(saved.getPublishedAt())
                    .build();
            eventPublisher.publishEvent(new NewMessageEvent(response,queue.getName()));
            eventPublisher.publishEvent(new BrokerActivityEvent(BrokerEventType.PUBLISHED,queue.getName(),"Message "+saved.getId()+" published to "+queue.getName(),LocalDateTime.now()));
            brokerMetrics.incrementPublished();
        });
    }

    @Override
    @Transactional(rollbackFor = CustomException.class)
    public ConsumeResponse consumeMessage(String queueName, String consumerId) throws CustomException {
        Optional<Queue> queue = this.queueRepository.findByName(queueName.toLowerCase());
        if(queue.isEmpty()){
            throw new ResourceNotFoundException("No Queue found with the name "+ queueName);
        }
        Optional<Message> message = this.messageRepository.claimNextPending(queue.get().getId(),MessageStatus.PENDING.name());
        if(message.isEmpty()){
            throw new NoMessageAvailableException("No Messages found in the Queue "+queueName);
        }
        Message msg = message.get();
        msg.setStatus(MessageStatus.UNACKED);
        msg.setConsumerId(consumerId);
        msg.setUnackedAt(LocalDateTime.now());
        this.messageRepository.save(msg);
        eventPublisher.publishEvent(new BrokerActivityEvent(BrokerEventType.CONSUMED,queueName,"Message "+msg.getId()+" consumed by "+ consumerId,LocalDateTime.now()));
        brokerMetrics.incrementConsumed();
        ConsumeResponse consumeResponse = new ConsumeResponse();
        consumeResponse.setMessageId(msg.getId());
        consumeResponse.setPayload(msg.getPayload());
        consumeResponse.setPublishedAt(msg.getPublishedAt());
        consumeResponse.setQueueName(queueName);
        return consumeResponse;
    }

    @Override
    @Transactional(rollbackFor = CustomException.class)
    public String ackMessage(UUID messageId, String consumerId) throws CustomException{
        Optional<Message> message = this.messageRepository.findByIdForUpdate(messageId);
        if(message.isEmpty()){
            throw new ResourceNotFoundException("No message found with the id "+messageId);
        }
        if(message.get().getStatus()!=MessageStatus.UNACKED){
            throw new ConflictException("The message "+messageId+" is not in unacked state");
        }
        if(!consumerId.equals(message.get().getConsumerId())){
            throw new ConflictException("Invalid Consumer ID "+consumerId);
        }
        Message msg = message.get();
        msg.setStatus(MessageStatus.ACKED);
        this.messageRepository.save(msg);
        eventPublisher.publishEvent(new BrokerActivityEvent(BrokerEventType.ACKED,msg.getQueue().getName(),"Message "+msg.getId()+" acked by "+ consumerId,LocalDateTime.now()));
        brokerMetrics.incrementAcked();
        return "Message acknowledged";
    }

    @Override
    @Transactional(rollbackFor = CustomException.class)
    public String nackMessage(UUID messageId, String consumerId, boolean requeue) throws CustomException {
        Optional<Message> message = this.messageRepository.findByIdForUpdate(messageId);
        if(message.isEmpty()){
            throw new ResourceNotFoundException("No message found with the id "+messageId);
        }
        if(message.get().getStatus()!=MessageStatus.UNACKED){
            throw new ConflictException("The message "+messageId+" is not in unacked state");
        }
        if(!consumerId.equals(message.get().getConsumerId())){
            throw new ConflictException("Invalid Consumer ID "+consumerId);
        }
        Message msg = message.get();
        if (requeue) {
            // Count this failed attempt, exactly like the redelivery scheduler does, so a message
            // that keeps failing ends up in the DLQ instead of cycling through PENDING forever.
            int retries = (msg.getRetryCount() != null ? msg.getRetryCount() : 0) + 1;
            msg.setRetryCount(retries);
            msg.setConsumerId(null);
            msg.setUnackedAt(null);

            if (retries >= maxRetries) {
                msg.setStatus(MessageStatus.DEAD);
                this.messageRepository.save(msg);
                eventPublisher.publishEvent(new BrokerActivityEvent(BrokerEventType.DEAD, msg.getQueue().getName(),
                        "Message " + msg.getId() + " dead after " + retries + " failed attempts", LocalDateTime.now()));
                brokerMetrics.incrementDead();
                return "Message moved to Dead after " + retries + " failed attempts";
            }

            msg.setStatus(MessageStatus.PENDING);
            this.messageRepository.save(msg);
            eventPublisher.publishEvent(new BrokerActivityEvent(BrokerEventType.NACKED, msg.getQueue().getName(),
                    "Message " + msg.getId() + " nacked by " + consumerId + " (attempt " + retries + ")", LocalDateTime.now()));
            brokerMetrics.incrementNacked();
            return "Message reset to Pending";
        }
        else {
            msg.setStatus(MessageStatus.DEAD);
            int current = msg.getRetryCount() != null ? msg.getRetryCount() : 0;
            msg.setRetryCount(current + 1);
            this.messageRepository.save(msg);
            eventPublisher.publishEvent(new BrokerActivityEvent(BrokerEventType.DEAD,msg.getQueue().getName(),"Message " +msg.getId()+ "set to dead ",LocalDateTime.now()));
            brokerMetrics.incrementDead();
            return "Message set to Dead";
        }
    }

    @Override
    public List<MessageResponse> getMessagesByQueue(String queueName) throws CustomException {
        List<Message> messages = messageRepository.findByQueueName(queueName);
        return messages.stream().map(messageMapper::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = CustomException.class)
    public String replayMessage(UUID messageId) {
        Optional<Message> msg = messageRepository.findByIdForUpdate(messageId);
        if(msg.isEmpty()) {
            throw new ResourceNotFoundException("No message found with id " + messageId);
        }
        if(msg.get().getStatus() != MessageStatus.DEAD) {
            throw new ConflictException("Only DEAD messages can be replayed");
        }
        msg.get().setStatus(MessageStatus.PENDING);
        msg.get().setConsumerId(null);
        msg.get().setUnackedAt(null);
        messageRepository.save(msg.get());
        eventPublisher.publishEvent(new BrokerActivityEvent(BrokerEventType.REPLAYED,msg.get().getQueue().getName(),"Message "+msg.get().getId()+" replayed from DLQ", LocalDateTime.now()));
        brokerMetrics.incrementRedelivered();
        return "Reset Message "+messageId+" to PENDING";
    }
}
