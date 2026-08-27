package com.leetduel.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

// Structured binding over @Value here (unlike most of auth-service's
// config) because the route table and public-path list are genuinely
// list-shaped - Spring's relaxed binding handles the indexed
// leetduel.gateway.routes[0].path=... properties directly, where @Value
// would mean hand-rolling comma-split parsing for no benefit.
@ConfigurationProperties(prefix = "leetduel.gateway")
public record RouteProperties(List<Route> routes, List<String> publicPaths, RateLimit rateLimit) {

    public record Route(String path, String uri) {
    }

    // capacity = max tokens a bucket holds, i.e. the size of the burst a
    // client can spend instantly before being throttled to the steady rate.
    // refillTokensPerSecond = steady-state rate once the burst is spent.
    // Two independent knobs (not just "N per M seconds") is what makes this
    // a token bucket rather than a fixed window - it decouples "how bursty"
    // from "how fast on average", which a single ratio can't express.
    public record RateLimit(int capacity, double refillTokensPerSecond) {
    }
}
