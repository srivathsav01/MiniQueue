package com.sri.miniqueue.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A registered application that talks to the broker: a producer, a consumer, or both.
 * Its API key is never stored, only its SHA-256 hash.
 */
@Getter
@Setter
@Entity
@Table(name = "client")
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private String name;

    /** The first characters of the key, e.g. "mq_Ab12Cd34", so a human can tell keys apart. */
    @Column(nullable = false)
    private String keyPrefix;

    @Column(nullable = false)
    private String keyHash;

    /** Space-separated, e.g. "publish:order.placed consume:email-queue". */
    @Column(nullable = false)
    private String scopes;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime lastUsedAt;

    private LocalDateTime revokedAt;
}