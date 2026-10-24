package com.sri.miniqueue.repository;

import com.sri.miniqueue.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientRepository extends JpaRepository<Client, UUID> {

    Optional<Client> findByKeyHashAndRevokedAtIsNull(String keyHash);

    Optional<Client> findByNameAndRevokedAtIsNull(String name);

    List<Client> findByRevokedAtIsNullOrderByCreatedAtAsc();

    // A direct UPDATE, not load-modify-save: two requests touching at once can't clash.
    @Modifying
    @Query("UPDATE Client c SET c.lastUsedAt = :now WHERE c.id = :id")
    void touch(@Param("id") UUID id, @Param("now") LocalDateTime now);
}