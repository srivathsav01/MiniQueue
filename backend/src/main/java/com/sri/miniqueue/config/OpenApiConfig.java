package com.sri.miniqueue.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/** Adds an "Authorize" button to Swagger UI: paste a key once and every "Try it out" sends it. */
@Configuration
@SecurityScheme(name = "apiKey", type = SecuritySchemeType.HTTP, scheme = "bearer",
        description = "A MiniQueue API key (mq_...). Admins create them at POST /api/v1/clients.")
@OpenAPIDefinition(security = @SecurityRequirement(name = "apiKey"))
public class OpenApiConfig {
}