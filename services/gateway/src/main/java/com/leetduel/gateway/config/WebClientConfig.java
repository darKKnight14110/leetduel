package com.leetduel.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    // A fresh WebClient per proxied call (built from this builder in
    // ProxyWebFilter) rather than one shared WebClient with a fixed
    // baseUrl - the target base URL varies per matched route, decided at
    // request time, not at bean-creation time.
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
