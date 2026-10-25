package com.sri.miniqueue.repository;

import com.sri.miniqueue.entity.DashboardSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DashboardSessionRepository extends JpaRepository<DashboardSession, UUID> {

    // ExpiresAtAfter is part of the lookup, so an expired session is refused even before cleanup runs.
    Optional<DashboardSession> findByTokenHashAndExpiresAtAfter(String tokenHash, LocalDateTime now);

    @Modifying
    @Query("DELETE FROM DashboardSession s WHERE s.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}