package com.sri.miniqueue.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sri.miniqueue.mapper.ResponseBuilder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/**
 * Security failures happen in a filter, before any controller runs, so GlobalExceptionHandler
 * never sees them. This writes them in the same JSON shape as every other error.
 */
public class JsonSecurityErrors implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JsonSecurityErrors(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 401: we don't know who you are. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        // The HTTP standard requires a 401 to say which authentication scheme to use.
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(response, HttpStatus.UNAUTHORIZED,
                "Missing, invalid or revoked API key. Send it as 'Authorization: Bearer <key>'");
    }

    /** 403: we know who you are, and you may not do this. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        write(response, HttpStatus.FORBIDDEN, "This API key is not allowed to do this. Check its scopes");
    }

    private void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ResponseBuilder.createErrorResponse(status.value(), message));
    }
}