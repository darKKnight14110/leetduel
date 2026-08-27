package com.leetduel.wsgateway.config;

import com.leetduel.wsgateway.security.StompAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

// Native WebSocket transport only - no SockJS fallback (skipped per the
// Phase 3 plan: modern browsers don't need it, and it's one less
// dependency on both server and frontend). Client destinations are
// subscribe-only (/topic/duel/{matchId}) - server never receives an
// application-level SEND, so no /app prefix is configured.
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Spring's in-process SimpleBroker - only delivers to sessions held
        // by THIS instance. Cross-instance fanout is handled entirely
        // outside this broker, via the Redis Pub/Sub relay (see the
        // fanout package) - see docs/goals.md's Phase 3 plan for why.
        registry.enableSimpleBroker("/topic");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
