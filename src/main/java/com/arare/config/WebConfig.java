package com.arare.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Cross-origin access for frontends that are not same-origin (Vite dev
 * server proxies /api, but a built app served from another origin calls the
 * API directly). Without this, Spring MVC rejects preflight OPTIONS requests
 * with 403 ("Invalid CORS request") and browsers block responses because no
 * Access-Control-Allow-Origin header is sent.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${arare.cors.allowed-origins:*}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}