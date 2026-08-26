package com.leetduel.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    // BCrypt: adaptive cost factor (can be tuned up as hardware gets
    // faster), per-hash random salt built in, purpose-built for password
    // storage - never a general-purpose hash (SHA-256 etc) for this.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Spring Security's CSRF exemption (below) has nothing to do with CORS -
    // CSRF is about a forged request riding an existing session; CORS is
    // the browser's own same-origin policy stopping the frontend's fetch()
    // from even reaching this response. Without this bean, a browser at
    // localhost:3000 calling localhost:8082 gets no Access-Control-*
    // headers back and blocks the response before JS ever sees it - a
    // failure that shows up as a vague "Failed to fetch" in the browser
    // console, not as any error this service logs.
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${leetduel.cors.allowed-origin}") String allowedOrigin
    ) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> {})
                // CSRF protects cookie-authenticated browser sessions from
                // cross-site form submission. This API is stateless/JWT
                // (Authorization header, not a cookie the browser attaches
                // automatically), so there's no session for a forged
                // cross-site request to ride on - CSRF doesn't apply here.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // All of auth-service's own endpoints are public by
                        // design - they're how a caller BECOMES
                        // authenticated (signup/login/refresh) or recovers
                        // access (verify/resend/forgot/reset). None of them
                        // need to trust an already-issued token; the JWT
                        // they hand back is what other services then
                        // require.
                        .requestMatchers(
                                "/auth/signup", "/auth/login", "/auth/refresh", "/auth/logout",
                                "/auth/verify-email", "/auth/resend-verification",
                                "/auth/forgot-password", "/auth/reset-password",
                                "/actuator/health"
                        ).permitAll()
                        .anyRequest().authenticated())
                .build();
    }
}
