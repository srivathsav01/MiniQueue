package com.sri.miniqueue.serviceImpl;

import com.sri.miniqueue.dto.ConsumeResponse;
import com.sri.miniqueue.entity.Message;
import com.sri.miniqueue.entity.Queue;
import com.sri.miniqueue.entity.Topic;
import com.sri.miniqueue.exception.CustomException;
import com.sri.miniqueue.repository.MessageRepository;
import com.sri.miniqueue.repository.QueueRepository;
import com.sri.miniqueue.repository.TopicRepository;
import com.sri.miniqueue.service.MessageService;
import com.sri.miniqueue.to.MessageStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class MessageServiceImpl implements MessageService {

    private final MessageRepository messageRepository;

    private final QueueRepository queueRepository;

    private final TopicRepository topicRepository;

    @Override
    public Topic createTopic(String name) throws CustomException {
        if(this.topicRepository.findTopicByName(name.toLowerCase()).isPresent()){
            throw new CustomException("Topic already exists with the name "+name);
        }
        Topic topic = new Topic();
        topic.setName(name.toLowerCase());
        return this.topicRepository.save(topic);
    }

    @Override
    public Queue createQueue(String name, String topicName) throws CustomException {
        Optional<Topic> topic = this.topicRepository.findTopicByName(topicName.toLowerCase());
        if(topic.isEmpty()){
            throw new CustomException("No Topic found with the name "+ topicName);
        }
        if(this.queueRepository.findByName(name.toLowerCase()).isPresent()){
            throw new CustomException("Queue already exists with the name "+ name);
        }
        Queue queue = Queue.builder()
                .name(name.toLowerCase())
                .topic(topic.get())
                .build();
        return this.queueRepository.save(queue);
    }

    @Override
    public void publishMessage(String topicName, String payload) throws CustomException {
        Optional<Topic> topic = this.topicRepository.findTopicByName(topicName.toLowerCase());
        if(topic.isEmpty()){
            throw new CustomException("No Topic found with the name "+ topicName);
        }
        List<Queue> queueList = this.queueRepository.findByTopic(topic.get());
        if(queueList.isEmpty()){
            throw new CustomException("No Queues are bound to the topic "+ topicName);
        }
        List<Message> messages = new ArrayList<>();
        queueList.forEach(queue->{
            Message message = new Message();
            message.setPayload(payload);
            message.setQueue(queue);
            message.setStatus(MessageStatus.PENDING);
            messages.add(message);
        });
        this.messageRepository.saveAll(messages);
    }

    @Override
    public ConsumeResponse consumeMessage(String queueName, String consumerId) throws CustomException {
        Optional<Queue> queue = this.queueRepository.findByName(queueName.toLowerCase());
        if(queue.isEmpty()){
            throw new CustomException("No Queue found with the name "+ queueName);
        }
        Optional<Message> message = this.messageRepository.findOldestByQueueAndStatus(queue.get(),MessageStatus.PENDING);
        if(message.isEmpty()){
            throw new CustomException("No Messages found in the Queue "+queueName);
        }
        Message msg = message.get();
        msg.setStatus(MessageStatus.UNACKED);
        msg.setConsumerId(consumerId);
        msg.setUnackedAt(LocalDateTime.now());
        this.messageRepository.save(msg);
        ConsumeResponse consumeResponse = new ConsumeResponse();
        consumeResponse.setMessageId(msg.getId());
        consumeResponse.setPayload(msg.getPayload());
        consumeResponse.setPublishedAt(msg.getPublishedAt());
        consumeResponse.setQueueName(queueName);
        return consumeResponse;
    }

    @Override
    public String ackMessage(UUID messageId, String consumerId) throws CustomException{
        Optional<Message> message = this.messageRepository.findById(messageId);
        if(message.isEmpty()){
            throw new CustomException("No message found with the id "+messageId);
        }
        if(message.get().getStatus()!=MessageStatus.UNACKED){
            throw new CustomException("The message "+messageId+" is not in unacked state");
        }
        if(!message.get().getConsumerId().equals(consumerId)){
            throw new CustomException("Invalid Consumer ID "+consumerId);
        }
        Message msg = message.get();
        msg.setStatus(MessageStatus.ACKED);
        this.messageRepository.save(msg);
        return "Message acknowledged";
    }


}
