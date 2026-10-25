package com.sri.miniqueue.service;

import com.sri.miniqueue.dto.SessionResponse;
import com.sri.miniqueue.security.ClientPrincipal;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface DashboardSessionService {

    SessionResponse startSession(String username, Set<String> scopes);

    Optional<ClientPrincipal> authenticate(String token);

    void logout(UUID sessionId);
}