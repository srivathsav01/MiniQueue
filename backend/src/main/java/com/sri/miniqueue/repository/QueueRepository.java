package com.sri.miniqueue.repository;

import com.sri.miniqueue.entity.Queue;
import com.sri.miniqueue.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QueueRepository extends JpaRepository<Queue, UUID> {

    List<Queue> findByTopic(Topic topic);

    Optional<Queue> findByName(String name);
}
