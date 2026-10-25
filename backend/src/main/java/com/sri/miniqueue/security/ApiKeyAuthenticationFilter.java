package com.sri.miniqueue.security;

import com.sri.miniqueue.service.ClientService;
import com.sri.miniqueue.service.DashboardSessionService;
import com.sri.miniqueue.utils.ApiKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Works out who is calling, from one of three credentials:
 *   Authorization: Bearer mq_...    an API key (programs)
 *   Authorization: Bearer mqs_...   a dashboard session (people signed in with GitHub)
 *   /ws/...?ticket=wst_...          a one-time WebSocket ticket (browsers opening a socket)
 * All three end as the same ClientPrincipal with scopes, so the rules in SecurityConfig never
 * care how someone proved who they are, only what they may do.
 *
 * Deliberately NOT a @Component: Spring Boot registers every Filter bean as a plain servlet
 * filter too, so it would run twice, once outside Spring Security.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final ClientService clientService;
    private final DashboardSessionService sessionService;
    private final OneTimeCodes<ClientPrincipal> webSocketTickets;
    private final AuthenticationEntryPoint entryPoint;

    public ApiKeyAuthenticationFilter(ClientService clientService, DashboardSessionService sessionService,
                                      OneTimeCodes<ClientPrincipal> webSocketTickets, AuthenticationEntryPoint entryPoint) {
        this.clientService = clientService;
        this.sessionService = sessionService;
        this.webSocketTickets = webSocketTickets;
        this.entryPoint = entryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Optional<Optional<ClientPrincipal>> attempt = credentialFrom(request);
        if (attempt.isEmpty()) {
            // No credential at all: carry on as anonymous. Open endpoints still work; protected ones answer 401.
            chain.doFilter(request, response);
            return;
        }

        Optional<ClientPrincipal> principal = attempt.get();
        if (principal.isEmpty()) {
            // A credential was sent but is wrong, expired or revoked: fail loudly, even on open endpoints.
            entryPoint.commence(request, response, new BadCredentialsException("Invalid credentials"));
            return;
        }

        ClientPrincipal caller = principal.get();
        var authorities = caller.scopes().stream().map(SimpleGrantedAuthority::new).toList();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new PreAuthenticatedAuthenticationToken(caller, null, authorities));
        SecurityContextHolder.setContext(context);

        chain.doFilter(request, response);
    }

    /** Outer empty: no credential was sent. Inner empty: one was sent, but it is not valid. */
    private Optional<Optional<ClientPrincipal>> credentialFrom(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            String credential = header.substring(BEARER.length()).trim();
            return Optional.of(credential.startsWith(ApiKeys.SESSION_PREFIX)
                    ? sessionService.authenticate(credential)
                    : clientService.authenticate(credential));
        }
        String ticket = request.getParameter("ticket");
        if (ticket != null && request.getRequestURI().startsWith("/ws/")) {
            return Optional.of(webSocketTickets.redeem(ticket));
        }
        return Optional.empty();
    }
}