package com.sri.miniqueue.security;

import com.sri.miniqueue.service.ClientService;
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
 * Reads "Authorization: Bearer mq_..." and, if the key is valid, records the client as the
 * authenticated caller for this request. It does not decide what the caller may do; the
 * rules in SecurityConfig do that.
 *
 * Deliberately NOT a @Component: Spring Boot registers every Filter bean as a plain servlet
 * filter too, so it would run twice, once outside Spring Security.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final ClientService clientService;
    private final AuthenticationEntryPoint entryPoint;

    public ApiKeyAuthenticationFilter(ClientService clientService, AuthenticationEntryPoint entryPoint) {
        this.clientService = clientService;
        this.entryPoint = entryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            // No key: carry on as anonymous. Open endpoints still work; protected ones answer 401.
            chain.doFilter(request, response);
            return;
        }

        Optional<ClientPrincipal> client = clientService.authenticate(header.substring(BEARER.length()).trim());
        if (client.isEmpty()) {
            // A key was sent but is wrong. Fail loudly, even on open endpoints, so a typo or a
            // revoked key in a config file shows up at once instead of half-working.
            entryPoint.commence(request, response, new BadCredentialsException("Invalid API key"));
            return;
        }

        ClientPrincipal principal = client.get();
        var authorities = principal.scopes().stream().map(SimpleGrantedAuthority::new).toList();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new PreAuthenticatedAuthenticationToken(principal, null, authorities));
        SecurityContextHolder.setContext(context);

        chain.doFilter(request, response);
    }
}