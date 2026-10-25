package com.sri.miniqueue.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "dashboard_session")
public class DashboardSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    /** The GitHub login, lowercased. */
    @Column(nullable = false)
    private String username;

    /** "admin" or "view". */
    @Column(nullable = false)
    private String scopes;

    @Column(nullable = false)
    private String tokenHash;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;
}