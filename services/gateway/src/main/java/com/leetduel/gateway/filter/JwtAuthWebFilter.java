package com.leetduel.gateway.filter;

import com.leetduel.gateway.config.RouteProperties;
import com.leetduel.gateway.security.JwtValidator;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

// Runs after RateLimitWebFilter (cheap rejection before spending effort on
// signature verification) and before ProxyWebFilter (nothing gets forwarded
// downstream without either being on the public allowlist or carrying a
// verified token). This is the entire reason this service exists as a
// gateway rather than each service validating its own JWTs - see
// goals.md's Auth/Gateway section.
@Component
@Order(2)
public class JwtAuthWebFilter implements WebFilter {

    static final String USER_ID_HEADER = "X-User-Id";
    static final String EMAIL_VERIFIED_HEADER = "X-Email-Verified";

    private final JwtValidator jwtValidator;
    private final RouteProperties routeProperties;

    public JwtAuthWebFilter(JwtValidator jwtValidator, RouteProperties routeProperties) {
        this.jwtValidator = jwtValidator;
        this.routeProperties = routeProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // Strip any inbound copies of these headers FIRST, unconditionally -
        // downstream services are meant to trust X-User-Id as something
        // only this Gateway can set. Without stripping here, a caller could
        // hand a downstream service a self-asserted identity directly and
        // it would be indistinguishable from one this Gateway actually
        // verified via signature.
        ServerHttpRequest sanitizedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(EMAIL_VERIFIED_HEADER);
                })
                .build();
        ServerWebExchange sanitizedExchange = exchange.mutate().request(sanitizedRequest).build();

        String path = sanitizedExchange.getRequest().getPath().value();
        if (routeProperties.publicPaths().contains(path)) {
            return chain.filter(sanitizedExchange);
        }

        String authHeader = sanitizedExchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(sanitizedExchange);
        }

        String token = authHeader.substring("Bearer ".length());
        return jwtValidator.validate(token)
                .map(validated -> {
                    ServerHttpRequest authedRequest = sanitizedExchange.getRequest().mutate()
                            .header(USER_ID_HEADER, validated.userId())
                            .header(EMAIL_VERIFIED_HEADER, String.valueOf(validated.emailVerified()))
                            .build();
                    return chain.filter(sanitizedExchange.mutate().request(authedRequest).build());
                })
                .orElseGet(() -> unauthorized(sanitizedExchange));
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = "{\"error\":\"Missing or invalid access token\"}".getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
