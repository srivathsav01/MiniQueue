package com.sri.miniqueue.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * All OpenAPI settings in one place, as a bean rather than annotations: a bean is explicit
 * and can't be silently overridden by an annotation elsewhere.
 *
 * Adds an "Authorize" button to Swagger UI: paste a key once and every "Try it out" sends it.
 */
@Configuration
public class OpenApiConfig {

    private static final String API_KEY = "apiKey";

    @Bean
    public OpenAPI miniQueueOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Miniqueue API").version("v1"))
                .components(new Components().addSecuritySchemes(API_KEY, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .description("A MiniQueue API key (mq_...). Admins create them at POST /api/v1/clients.")))
                .addSecurityItem(new SecurityRequirement().addList(API_KEY));
    }
}