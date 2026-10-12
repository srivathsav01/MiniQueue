package com.sri.miniqueue.repository;

import com.sri.miniqueue.dto.MessageStatusCount;
import com.sri.miniqueue.entity.Message;
import com.sri.miniqueue.entity.Queue;
import com.sri.miniqueue.to.MessageStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

    @Query(value = "SELECT m.* FROM message m WHERE m.queue_id = :queue_id AND m.status = :status ORDER BY m.published_at ASC LIMIT 1 FOR UPDATE SKIP LOCKED",nativeQuery = true )
    Optional<Message> claimNextPending(@Param("queue_id") UUID queue_id, @Param("status") String status);

    @Query(value = "SELECT m.* FROM message m WHERE m.status = :status AND m.unacked_at < :cutoff ORDER BY m.unacked_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED",nativeQuery = true)
    List<Message> claimStuckMessages(@Param("status") String status, @Param("cutoff")LocalDateTime cutoff, @Param("limit") int limit);

    @Query("SELECT m FROM Message m WHERE m.status = :status AND m.unackedAt < :cutoff")
    List<Message> findStuckMessages(@Param("status") MessageStatus status, @Param("cutoff")LocalDateTime cutoff);

    @Query("SELECT m.queue.id as queueId, m.status as status, COUNT(m) as count " +
            "FROM Message m GROUP BY m.queue.id, m.status")
    List<MessageStatusCount> countByQueueAndStatus();

    @Query("SELECT m FROM Message m WHERE m.status = :status")
    List<Message> findDeadMessages(@Param("status") MessageStatus status);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.queue.name = :queueName AND m.status = :status")
    Long countByQueueNameAndStatus(@Param("queueName") String queueName, @Param("status") MessageStatus status);

    @Query("SELECT m FROM Message m WHERE m.queue.name = :queueName ORDER BY m.publishedAt ASC")
    List<Message> findByQueueName(@Param("queueName") String queueName);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Message m WHERE m.id = :id")
    Optional<Message> findByIdForUpdate(@Param("id") UUID id);
}
