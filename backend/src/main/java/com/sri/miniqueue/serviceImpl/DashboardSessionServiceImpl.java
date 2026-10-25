package com.sri.miniqueue.serviceImpl;

import com.sri.miniqueue.dto.SessionResponse;
import com.sri.miniqueue.entity.DashboardSession;
import com.sri.miniqueue.repository.DashboardSessionRepository;
import com.sri.miniqueue.security.ClientPrincipal;
import com.sri.miniqueue.service.DashboardSessionService;
import com.sri.miniqueue.utils.ApiKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardSessionServiceImpl implements DashboardSessionService {

    private final DashboardSessionRepository sessionRepository;

    @Value("${miniqueue.security.session-ttl:8h}")
    private Duration sessionTtl;

    @Override
    @Transactional
    public SessionResponse startSession(String username, Set<String> scopes) {
        String token = ApiKeys.randomToken(ApiKeys.SESSION_PREFIX);
        LocalDateTime now = LocalDateTime.now();

        DashboardSession session = new DashboardSession();
        session.setUsername(username);
        session.setScopes(String.join(" ", new TreeSet<>(scopes)));
        session.setTokenHash(ApiKeys.hash(token));
        session.setCreatedAt(now);
        session.setExpiresAt(now.plus(sessionTtl));
        sessionRepository.save(session);

        log.info("Dashboard sign-in: {} with scopes [{}]", username, session.getScopes());
        return SessionResponse.builder()
                .token(token)
                .username(username)
                .scopes(List.of(session.getScopes().split(" ")))
                .expiresAt(session.getExpiresAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ClientPrincipal> authenticate(String token) {
        if (!ApiKeys.looksLikeSessionToken(token)) {
            return Optional.empty();
        }
        return sessionRepository.findByTokenHashAndExpiresAtAfter(ApiKeys.hash(token), LocalDateTime.now())
                .map(s -> new ClientPrincipal(s.getId(), s.getUsername(), Set.of(s.getScopes().split(" "))));
    }

    @Override
    @Transactional
    public void logout(UUID sessionId) {
        sessionRepository.deleteById(sessionId); // a missing row (already expired) is not an error
    }

    @Scheduled(fixedDelay = 3_600_000) // hourly
    @Transactional
    public void deleteExpiredSessions() {
        int deleted = sessionRepository.deleteExpired(LocalDateTime.now());
        if (deleted > 0) {
            log.info("Deleted {} expired dashboard sessions", deleted);
        }
    }
}