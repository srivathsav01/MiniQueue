package com.sri.miniqueue.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sri.miniqueue.service.ClientService;
import com.sri.miniqueue.service.DashboardSessionService;
import com.sri.miniqueue.utils.Scopes;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

import java.util.Locale;
import java.util.function.Function;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ClientService clientService;

    private final DashboardSessionService sessionService;

    private final OneTimeCodes<ClientPrincipal> webSocketTickets;

    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        JsonSecurityErrors errors = new JsonSecurityErrors(objectMapper);

        http
                // CSRF works by making a browser send its cookies to another site. Every credential
                // here is sent explicitly by the caller (a header or a one-time code), never as a
                // cookie. The only cookie, the OAuth state, is itself the CSRF protection for sign-in.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(e -> e.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .addFilterBefore(new ApiKeyAuthenticationFilter(clientService, sessionService, webSocketTickets, errors),
                        AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // Open to everyone
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error").permitAll() // Spring's error forward; denying it turns real errors into 401
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/github/login", "/api/v1/auth/github/callback").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/exchange").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        // Public on purpose: the source is public, so hiding the docs hides nothing
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()

                        // Anyone signed in (or any API key): who am I, sign out, WebSocket ticket
                        .requestMatchers("/api/v1/auth/**").authenticated()

                        // Producers and consumers
                        .requestMatchers(HttpMethod.POST, "/api/v1/topics/{topic}/messages")
                        .access(scope(Scopes.PUBLISH, ctx -> ctx.getVariables().get("topic")))
                        .requestMatchers(HttpMethod.POST, "/api/v1/queues/{queue}/consume")
                        .access(scope(Scopes.CONSUME, ctx -> ctx.getVariables().get("queue")))
                        .requestMatchers(HttpMethod.POST, "/api/v1/messages/*/ack", "/api/v1/messages/*/nack").authenticated()
                        .requestMatchers("/ws/consume")
                        .access(scope(Scopes.CONSUME, ctx -> ctx.getRequest().getParameter("queue")))

                        // Reading the dashboard: view or admin
                        .requestMatchers(HttpMethod.GET, "/api/v1/topics", "/api/v1/queues",
                                "/api/v1/queues/*/messages", "/api/v1/dashboard/**").hasAnyAuthority(Scopes.VIEW, Scopes.ADMIN)
                        .requestMatchers("/ws/monitor").hasAnyAuthority(Scopes.VIEW, Scopes.ADMIN)

                        // Monitoring
                        .requestMatchers("/actuator/prometheus").hasAnyAuthority(Scopes.METRICS, Scopes.ADMIN)

                        // Everything else in the API changes state: admin only
                        .requestMatchers("/api/v1/**").hasAuthority(Scopes.ADMIN)

                        // Deny by default: a path nobody listed above is refused, not silently open
                        .anyRequest().denyAll());

        return http.build();
    }

    /** Allows the request if the caller's scopes cover this action on the topic or queue nameOf extracts. */
    private static AuthorizationManager<RequestAuthorizationContext> scope(
            String action, Function<RequestAuthorizationContext, String> nameOf) {
        return (authentication, context) -> {
            Authentication auth = authentication.get();
            if (auth == null || !(auth.getPrincipal() instanceof ClientPrincipal client)) {
                return new AuthorizationDecision(false); // anonymous: Spring turns this into 401
            }
            String name = nameOf.apply(context);
            if (name == null || name.isBlank()) {
                return new AuthorizationDecision(false);
            }
            return new AuthorizationDecision(Scopes.allows(client.scopes(), action, name.toLowerCase(Locale.ROOT)));
        };
    }
}