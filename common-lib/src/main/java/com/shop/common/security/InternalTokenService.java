package com.shop.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Very short-lived token minted by the API gateway after it has verified the user's access token.
 * Every downstream service accepts ONLY this token (header X-Internal-Token), so anybody who
 * reaches a service directly, with or without a valid user JWT, is rejected.
 */
public class InternalTokenService {

    public static final String ISSUER = "api-gateway";
    private static final String ANONYMOUS = "anonymous";

    private final SecretKey key;
    private final long ttlSeconds;

    public InternalTokenService(String secret, long ttlSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlSeconds = ttlSeconds;
    }

    public String mint(AuthUser user) {
        Instant now = Instant.now();
        var b = Jwts.builder()
                .issuer(ISSUER)
                .subject(user.isAnonymous() ? ANONYMOUS : user.id().toString())
                .id(UUID.randomUUID().toString())
                .claim("typ", "internal")
                .claim("roles", List.copyOf(user.roles()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)));
        if (user.email() != null) b.claim("email", user.email());
        return b.signWith(key).compact();
    }

    public Optional<AuthUser> verify(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).requireIssuer(ISSUER).build()
                    .parseSignedClaims(token).getPayload();
            if (!"internal".equals(c.get("typ", String.class))) return Optional.empty();
            if (ANONYMOUS.equals(c.getSubject())) return Optional.of(AuthUser.anonymous());
            Object raw = c.get("roles");
            Set<String> roles = raw instanceof Collection<?> col
                    ? col.stream().map(String::valueOf).collect(Collectors.toSet())
                    : Set.of();
            return Optional.of(new AuthUser(UUID.fromString(c.getSubject()), c.get("email", String.class), roles));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
