package com.leetduel.gateway.proxy;

import com.leetduel.gateway.config.RouteProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

// Terminal filter (lowest precedence - runs last). For a path matching a
// configured route, this forwards the request and writes the downstream
// response directly, WITHOUT calling chain.filter - there's no
// @RestController behind this to dispatch to, this filter IS the handler.
// For a path matching no route (chiefly /actuator/**, and anything genuinely
// unmapped), it calls chain.filter to let WebFlux's own dispatch - Boot's
// actuator endpoints, or the default 404 - handle it instead.
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class ProxyWebFilter implements WebFilter {

    private final WebClient.Builder webClientBuilder;
    private final List<RouteProperties.Route> routes;

    public ProxyWebFilter(WebClient.Builder webClientBuilder, RouteProperties routeProperties) {
        this.webClientBuilder = webClientBuilder;
        this.routes = routeProperties.routes();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        return findRoute(path)
                .map(route -> proxy(exchange, route))
                .orElseGet(() -> chain.filter(exchange));
    }

    // Longest-matching prefix wins, so a more specific route (if one is
    // ever added, e.g. /users/admin) takes precedence over a broader one
    // (/users) covering the same request.
    private Optional<RouteProperties.Route> findRoute(String path) {
        return routes.stream()
                .filter(route -> path.equals(route.path()) || path.startsWith(route.path() + "/"))
                .max(Comparator.comparingInt(route -> route.path().length()));
    }

    private Mono<Void> proxy(ServerWebExchange exchange, RouteProperties.Route route) {
        ServerHttpRequest request = exchange.getRequest();

        // No prefix stripping - see application.properties' comment on the
        // route table for why the downstream path is identical to the
        // inbound one.
        URI targetUri = UriComponentsBuilder.fromUriString(route.uri())
                .path(request.getURI().getRawPath())
                .query(request.getURI().getRawQuery())
                .build(true)
                .toUri();

        return webClientBuilder.build()
                .method(request.getMethod())
                .uri(targetUri)
                .headers(headers -> {
                    headers.addAll(request.getHeaders());
                    // Host/Content-Length describe THIS hop, not the one
                    // WebClient is about to make - left in place, they'd
                    // either point at the wrong host or mismatch the
                    // re-encoded body length.
                    headers.remove(HttpHeaders.HOST);
                    headers.remove(HttpHeaders.CONTENT_LENGTH);
                })
                .body(BodyInserters.fromDataBuffers(request.getBody()))
                .exchangeToMono(clientResponse -> {
                    ServerHttpResponse response = exchange.getResponse();
                    response.setStatusCode(clientResponse.statusCode());
                    response.getHeaders().addAll(clientResponse.headers().asHttpHeaders());
                    // Transfer-Encoding described the upstream hop's framing
                    // (e.g. chunked); this response gets its own framing
                    // decided by this server, not inherited from upstream.
                    response.getHeaders().remove(HttpHeaders.TRANSFER_ENCODING);
                    return response.writeWith(clientResponse.bodyToFlux(DataBuffer.class));
                });
    }
}
