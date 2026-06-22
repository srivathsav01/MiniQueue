package com.sri.miniqueue.repository;

import com.sri.miniqueue.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TopicRepository extends JpaRepository<Topic, UUID> {

    Optional<Topic> findTopicByName(String name);
}
