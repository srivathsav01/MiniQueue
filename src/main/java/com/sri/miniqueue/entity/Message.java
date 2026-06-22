package com.sri.miniqueue.entity;

import com.sri.miniqueue.to.MessageStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
public class Message {

    @Id
    @Column(nullable = false,updatable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String payload;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private MessageStatus status;

    @Column
    private Integer retryCount;

    @Column
    private String consumerId;

    @Column(insertable = false, updatable = false)
    private LocalDateTime publishedAt;

    @Column
    private LocalDateTime unackedAt;

    @ManyToOne()
    @JoinColumn(name="queue_id", referencedColumnName ="id", nullable = false)
    private Queue queue;
}
