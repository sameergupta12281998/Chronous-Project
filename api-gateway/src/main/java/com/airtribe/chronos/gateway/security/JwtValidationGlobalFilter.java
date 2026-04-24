package com.airtribe.chronos.gateway.security;

import com.airtribe.chronos.commons.security.GatewayHeaders;
import com.airtribe.chronos.commons.security.JwtTokenService;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Validates Bearer JWT for protected routes and forwards user identity to downstream
 * services as X-Chronos-User-Id / X-Chronos-Username headers. Public routes
 * (registration, login, health, metrics) bypass the check.
 */
public class JwtValidationGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtValidationGlobalFilter.class);
    private final JwtTokenService jwt;

    public JwtValidationGlobalFilter(JwtTokenService jwt) { this.jwt = jwt; }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (isPublic(path)) return chain.filter(exchange);

        String header = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return reject(exchange);
        try {
            Claims c = jwt.parse(header.substring(7));
            ServerHttpRequest mutated = exchange.getRequest().mutate()
                    .header(GatewayHeaders.USER_ID, c.getSubject())
                    .header(GatewayHeaders.USERNAME, c.get("username", String.class))
                    .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (Exception ex) {
            log.debug("JWT rejected: {}", ex.getMessage());
            return reject(exchange);
        }
    }

    private static boolean isPublic(String path) {
        return path.startsWith("/api/v1/auth/")
                || path.startsWith("/actuator")
                || path.equals("/");
    }

    private static Mono<Void> reject(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    @Override public int getOrder() { return -100; }
}
