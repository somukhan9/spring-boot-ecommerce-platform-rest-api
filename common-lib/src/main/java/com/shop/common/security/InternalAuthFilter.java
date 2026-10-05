package com.shop.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Zero-trust gate for every microservice. No valid gateway-signed X-Internal-Token => 403,
 * before any controller or security rule is evaluated.
 * The raw token is stored as the Authentication "credentials" so services can forward it on
 * service-to-service calls.
 */
public class InternalAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";

    private final InternalTokenService tokens;

    public InternalAuthFilter(InternalTokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String uri = req.getRequestURI();
        if (uri.equals("/actuator/health") || uri.startsWith("/actuator/health/")) {
            chain.doFilter(req, res);
            return;
        }

        String token = req.getHeader(HEADER);
        Optional<AuthUser> verified = (token == null || token.isBlank()) ? Optional.empty() : tokens.verify(token);
        if (verified.isEmpty()) {
            JsonErrors.write(res, 403, "Direct access to this service is not allowed");
            return;
        }

        AuthUser user = verified.get();
        if (!user.isAnonymous()) {
            List<SimpleGrantedAuthority> authorities = user.roles().stream()
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
            SecurityContext ctx = SecurityContextHolder.createEmptyContext();
            ctx.setAuthentication(new UsernamePasswordAuthenticationToken(user, token, authorities));
            SecurityContextHolder.setContext(ctx);
        }
        chain.doFilter(req, res);
    }
}
