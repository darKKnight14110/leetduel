package com.leetduel.wsgateway.security;

import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.security.Principal;

// Browsers cannot set an Authorization header on a native WebSocket upgrade
// request, so auth here happens one level up the stack: the client puts
// the JWT in the STOMP CONNECT frame's own headers (application-level,
// after the WS upgrade already completed), and this interceptor validates
// it before letting Spring send CONNECTED back. Structurally different
// from the HTTP Gateway's JwtAuthWebFilter, which checks a real
// Authorization header once per HTTP request - this runs once per WS
// session, at CONNECT time only.
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtValidator jwtValidator;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            String token = authHeader != null && authHeader.startsWith("Bearer ")
                    ? authHeader.substring("Bearer ".length())
                    : null;

            JwtValidator.ValidatedToken validated = token == null ? null
                    : jwtValidator.validate(token).orElse(null);
            if (validated == null) {
                // Rejecting here closes the WS connection before CONNECTED
                // is ever sent - the client sees a failed handshake, same
                // outward behavior as a 401 from JwtAuthWebFilter.
                throw new StompAuthenticationException("Invalid or missing JWT on STOMP CONNECT");
            }

            Principal principal = new UserPrincipal(validated.userId());
            accessor.setUser(principal);
        }
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (destination != null && destination.startsWith("/user/")
                    && !"/user/queue/practice".equals(destination)) {
                throw new StompAuthenticationException("Unsupported user destination");
            }
        }
        return message;
    }

    private record UserPrincipal(String userId) implements Principal {
        @Override
        public String getName() {
            return userId;
        }
    }
}
