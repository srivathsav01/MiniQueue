package com.sri.miniqueue.security;

import com.sri.miniqueue.dto.SessionResponse;
import com.sri.miniqueue.service.DashboardSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GitHubLoginService {

    private final GitHubApi gitHub;

    private final DashboardAccess access;

    private final DashboardSessionService sessions;

    public SessionResponse signIn(String code) {
        GitHubApi.GitHubUser user = gitHub.fetchUser(code);
        Set<String> scopes = access.scopesFor(user.login())
                .orElseThrow(() -> new GitHubLoginException("not_allowed", user.login() + " is not an admin"));
        return sessions.startSession(user.login().toLowerCase(Locale.ROOT), scopes);
    }
}