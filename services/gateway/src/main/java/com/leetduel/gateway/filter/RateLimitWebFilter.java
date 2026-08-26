package com.leetduel.gateway.filter;

import com.leetduel.gateway.config.RouteProperties;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

// Redis-backed, not in-memory - this Gateway is meant to run multiple
// instances behind a load balancer once on k8s (see goals.md's Deployment
// section and the HPA note under Phase 6), and a per-instance in-memory
// counter would let a single client get N requests through EACH instance
// instead of N total. Runs first (@Order(1), before JwtAuthWebFilter) so a
// client hammering the public /auth/login endpoint gets rejected before
// this service spends any effort on signature verification.
@Component
@Order(1)
public class RateLimitWebFilter implements WebFilter {

    private static final String KEY_PREFIX = "ratelimit:";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final int requestsPerWindow;
    private final long windowSeconds;

    public RateLimitWebFilter(ReactiveStringRedisTemplate redisTemplate, RouteProperties routeProperties) {
        this.redisTemplate = redisTemplate;
        this.requestsPerWindow = routeProperties.rateLimit().requestsPerWindow();
        this.windowSeconds = routeProperties.rateLimit().windowSeconds();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String key = KEY_PREFIX + clientIp(exchange) + ":" + currentWindow();

        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> {
                    // EXPIRE only set on the window's first request - every
                    // key naturally self-cleans windowSeconds after it was
                    // first touched, no separate cleanup job needed. The
                    // increment-then-expire pair isn't atomic (a Lua EVAL
                    // script would be), so a key could in theory survive
                    // slightly past its intended window under a crash
                    // between the two calls - acceptable for a rate limit
                    // (worst case: a few extra requests slip through once),
                    // not acceptable for anything money/inventory-related.
                    Mono<Boolean> expireStep = count == 1
                            ? redisTemplate.expire(key, Duration.ofSeconds(windowSeconds))
                            : Mono.just(true);

                    return expireStep.then(Mono.defer(() -> count > requestsPerWindow
                            ? tooManyRequests(exchange)
                            : chain.filter(exchange)));
                });
    }

    private long currentWindow() {
        return Instant.now().getEpochSecond() / windowSeconds;
    }

    // Falls back to "unknown" rather than throwing if the remote address is
    // somehow absent (e.g. a synthetic test exchange) - unauthenticated
    // callers hitting this branch would then all share one bucket, which
    // fails safe (over-limits) rather than failing open (no limit at all).
    private String clientIp(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress != null && remoteAddress.getAddress() != null
                ? remoteAddress.getAddress().getHostAddress()
                : "unknown";
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = "{\"error\":\"Rate limit exceeded, try again shortly\"}".getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
