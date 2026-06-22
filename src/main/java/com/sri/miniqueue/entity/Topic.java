package com.sri.miniqueue.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
public class Topic {

    @Id
    @Column(nullable = false,updatable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column
    private String name;

    @CreationTimestamp
    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;
}