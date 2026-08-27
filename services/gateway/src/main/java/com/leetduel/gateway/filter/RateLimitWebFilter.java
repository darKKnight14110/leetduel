package com.leetduel.gateway.filter;

import com.leetduel.gateway.config.RouteProperties;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

// Redis-backed, not in-memory - this Gateway is meant to run multiple
// instances behind a load balancer once on k8s (see goals.md's Deployment
// section and the HPA note under Phase 6), and a per-instance in-memory
// counter would let a single client get N requests through EACH instance
// instead of N total. Runs first (@Order(1), before JwtAuthWebFilter) so a
// client hammering the public /auth/login endpoint gets rejected before
// this service spends any effort on signature verification.
//
// Token bucket, not fixed-window counter: a fixed window (INCR+EXPIRE) lets
// a client burst up to 2x the nominal rate right at a window boundary (N
// requests in the last instant of one window, N more in the first instant
// of the next) and can't express "allow occasional bursts, but hold to a
// steady average" - it's one knob (count per window), not two. Token
// bucket decouples burst size (capacity) from steady-state rate
// (refillTokensPerSecond), which is what production rate limiters (Stripe,
// AWS API Gateway, GCP) actually implement. The single EVAL below is what
// makes the refill/consume read-modify-write atomic - see
// token_bucket.lua's header comment for why that matters and why the whole
// thing is Redis-Cluster-safe by construction (one key per client, no
// CROSSSLOT risk).
@Component
@Order(1)
public class RateLimitWebFilter implements WebFilter {

    private static final String KEY_PREFIX = "ratelimit:token-bucket:";
    private static final int TOKENS_PER_REQUEST = 1;

    private final ReactiveStringRedisTemplate redisTemplate;
    private final RedisScript<List> tokenBucketScript;
    private final int capacity;
    private final double refillTokensPerSecond;
    private final long ttlSeconds;

    public RateLimitWebFilter(ReactiveStringRedisTemplate redisTemplate,
                               RedisScript<List> tokenBucketScript,
                               RouteProperties routeProperties) {
        this.redisTemplate = redisTemplate;
        this.tokenBucketScript = tokenBucketScript;
        RouteProperties.RateLimit rateLimit = routeProperties.rateLimit();
        this.capacity = rateLimit.capacity();
        this.refillTokensPerSecond = rateLimit.refillTokensPerSecond();
        // Time to refill from empty to full, +1s margin. A bucket idle past
        // this point has nothing left to remember - the script already
        // treats a missing key as "fresh, full bucket" (see its header), so
        // letting Redis expire it here is correct, not a leak: no memory
        // held for clients who aren't actively being rate-limited.
        this.ttlSeconds = (long) Math.ceil(capacity / refillTokensPerSecond) + 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String key = KEY_PREFIX + clientIp(exchange);
        List<String> keys = List.of(key);
        List<String> args = List.of(
                String.valueOf(capacity),
                String.valueOf(refillTokensPerSecond),
                String.valueOf(TOKENS_PER_REQUEST),
                String.valueOf(ttlSeconds));

        return redisTemplate.execute(tokenBucketScript, keys, args)
                .next()
                .flatMap(result -> {
                    boolean allowed = "1".equals(String.valueOf(result.get(0)));
                    String remaining = String.valueOf((long) Double.parseDouble(String.valueOf(result.get(1))));

                    // Standard rate-limit response headers (RFC-adjacent
                    // convention, not an actual RFC) - lets a well-behaved
                    // client back off before it starts getting 429s instead
                    // of discovering the limit by tripping it.
                    exchange.getResponse().getHeaders().set("X-RateLimit-Limit", String.valueOf(capacity));
                    exchange.getResponse().getHeaders().set("X-RateLimit-Remaining", remaining);

                    return allowed ? chain.filter(exchange) : tooManyRequests(exchange);
                });
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
