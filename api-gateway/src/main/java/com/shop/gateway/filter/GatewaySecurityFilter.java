package com.shop.gateway.filter;

import com.shop.common.security.AccessTokenService;
import com.shop.common.security.AuthUser;
import com.shop.common.security.InternalTokenService;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The single trust boundary of the platform:
 *  1. rejects path-traversal tricks and anything addressed to /internal
 *  2. verifies the user's access token (or allows anonymous on public endpoints)
 *  3. applies coarse RBAC (/api/admin/** => ADMIN)
 *  4. strips the user JWT and any client-supplied X-Internal-Token, then adds a freshly signed
 *     60-second internal token that downstream services require.
 */
@Component
public class GatewaySecurityFilter implements GlobalFilter, Ordered {

    private static final String INTERNAL_HEADER = "X-Internal-Token";
    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private record Rule(HttpMethod method, String pattern) {}

    private static final List<Rule> PUBLIC = List.of(
            new Rule(HttpMethod.POST, "/api/auth/register"),
            new Rule(HttpMethod.POST, "/api/auth/login"),
            new Rule(HttpMethod.POST, "/api/auth/refresh"),
            new Rule(HttpMethod.POST, "/api/auth/logout"),
            new Rule(HttpMethod.GET, "/api/products"),
            new Rule(HttpMethod.GET, "/api/products/*"));

    private static final List<String> ADMIN_ONLY = List.of("/api/admin/**");

    private final AccessTokenService accessTokens;
    private final InternalTokenService internalTokens;

    public GatewaySecurityFilter(AccessTokenService accessTokens, InternalTokenService internalTokens) {
        this.accessTokens = accessTokens;
        this.internalTokens = internalTokens;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest req = exchange.getRequest();
        String rawPath = req.getURI().getRawPath();
        String path = req.getURI().getPath();

        // 1. path hygiene (stops /api/products/../../internal/... style bypasses)
        String lower = rawPath.toLowerCase(Locale.ROOT);
        if (lower.contains("..") || lower.contains("%2e") || lower.contains("%2f") || lower.contains("%5c")
                || lower.contains("//") || lower.contains(";") || lower.contains("\\")) {
            return GatewayErrors.write(exchange, HttpStatus.BAD_REQUEST, "Invalid path");
        }
        if (path.equals("/internal") || path.startsWith("/internal/")) {
            return GatewayErrors.write(exchange, HttpStatus.NOT_FOUND, "Not found");
        }

        // let CORS pre-flight through untouched (handled by the CORS config)
        if (HttpMethod.OPTIONS.equals(req.getMethod())) {
            return chain.filter(exchange);
        }

        // 2. authentication
        Optional<AuthUser> user = Optional.empty();
        String authz = req.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authz != null && authz.regionMatches(true, 0, "Bearer ", 0, 7)) {
            user = accessTokens.parse(authz.substring(7).trim());
        }
        boolean isPublic = PUBLIC.stream().anyMatch(r -> r.method().equals(req.getMethod()) && MATCHER.match(r.pattern(), path));
        if (user.isEmpty() && !isPublic) {
            return GatewayErrors.write(exchange, HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        // 3. coarse RBAC
        if (ADMIN_ONLY.stream().anyMatch(p -> MATCHER.match(p, path)) && !user.map(AuthUser::isAdmin).orElse(false)) {
            return GatewayErrors.write(exchange, HttpStatus.FORBIDDEN, "Access denied");
        }

        // 4. swap the user JWT for a short-lived internal token
        String internal = internalTokens.mint(user.orElse(AuthUser.anonymous()));
        ServerHttpRequest mutated = req.mutate().headers(h -> {
            h.remove(HttpHeaders.AUTHORIZATION);
            h.remove(INTERNAL_HEADER);              // never trust a client-supplied value
            h.set(INTERNAL_HEADER, internal);
        }).build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
