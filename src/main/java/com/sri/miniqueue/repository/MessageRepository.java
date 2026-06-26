package com.sri.miniqueue.repository;

import com.sri.miniqueue.entity.Message;
import com.sri.miniqueue.entity.Queue;
import com.sri.miniqueue.to.MessageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

    @Query("SELECT m FROM Message m WHERE m.queue = :queue AND m.status = :status ORDER BY m.publishedAt ASC LIMIT 1")
    Optional<Message> findOldestByQueueAndStatus(@Param("queue") Queue queue, @Param("status") MessageStatus status);

    @Query("SELECT m FROM Message m WHERE m.status = :status AND m.unackedAt < :cutoff")
    List<Message> findStuckMessages(@Param("status") MessageStatus status, @Param("cutoff")LocalDateTime cutoff);
}
