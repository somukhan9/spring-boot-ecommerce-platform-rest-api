package com.shop.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window per-IP rate limiter (in-memory, per gateway instance).
 * For several gateway replicas swap this for Spring's RequestRateLimiter + Redis.
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final Set<String> AUTH_PATHS = Set.of("/api/auth/login", "/api/auth/register", "/api/auth/refresh");

    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();
    private final int generalLimit;
    private final int authLimit;

    public RateLimitFilter(@Value("${gateway.rate-limit.general-per-minute}") int generalLimit,
                           @Value("${gateway.rate-limit.auth-per-minute}") int authLimit) {
        this.generalLimit = generalLimit;
        this.authLimit = authLimit;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        boolean auth = AUTH_PATHS.contains(path);
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        String ip = remote == null ? "unknown" : remote.getAddress().getHostAddress();

        long minute = System.currentTimeMillis() / 60_000;
        String key = (auth ? "a|" : "g|") + ip + "|" + minute;
        int count = counters.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();

        if (count > (auth ? authLimit : generalLimit)) {
            exchange.getResponse().getHeaders().set("Retry-After", "60");
            return GatewayErrors.write(exchange, HttpStatus.TOO_MANY_REQUESTS, "Too many requests");
        }
        return chain.filter(exchange);
    }

    @Scheduled(fixedRate = 60_000)
    void evictOldWindows() {
        long current = System.currentTimeMillis() / 60_000;
        counters.keySet().removeIf(k -> Long.parseLong(k.substring(k.lastIndexOf('|') + 1)) < current);
    }

    @Override
    public int getOrder() {
        return -200;
    }
}
