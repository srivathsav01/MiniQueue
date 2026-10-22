package com.sri.miniqueue.config;

import com.sri.miniqueue.utils.APIConstants;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Puts every REST controller in our controller package under /api/v1, in one place,
 * instead of repeating the prefix on each @RequestMapping.
 *
 * Scoped to our package on purpose: a blanket "every @RestController" rule would also
 * move springdoc's /v3/api-docs, which lives in a library.
 * Actuator and the WebSocket endpoints are registered separately and are not affected.
 */
@Configuration
public class ApiVersionConfig implements WebMvcConfigurer {

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(APIConstants.API_V1,
                HandlerTypePredicate.forBasePackage("com.sri.miniqueue.controller"));
    }
}