package com.leetduel.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

// CORS is the browser's own same-origin policy stopping the frontend's
// fetch() from ever reaching a response - entirely separate from this
// Gateway's own JWT/rate-limit checks (see JwtAuthWebFilter/
// RateLimitWebFilter's @Order comments). Without this bean, a browser at
// localhost:3000 calling localhost:8084 never gets a response body back
// for a cross-origin request at all; the browser blocks it client-side
// before JS ever sees anything, which shows up as a bare "Failed to
// fetch"/CORS console error, not anything this service logs (confirmed
// directly - this is exactly the failure this fixes, not a hypothetical).
//
// Explicit @Order(HIGHEST_PRECEDENCE) is required, not decoration:
// reactive CorsWebFilter, unlike Spring Security's CORS integration,
// does NOT implement Ordered itself. Left unordered it would sort to
// Spring's default LOWEST_PRECEDENCE and run AFTER RateLimitWebFilter
// (@Order(1)) and JwtAuthWebFilter (@Order(2)) - both of which would then
// reject a browser's CORS preflight (a credential-less OPTIONS request)
// before this filter ever got a chance to attach the Access-Control-*
// headers the browser is waiting for.
@Configuration
public class CorsConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public CorsWebFilter corsWebFilter(@Value("${leetduel.cors.allowed-origin}") String allowedOrigin) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
