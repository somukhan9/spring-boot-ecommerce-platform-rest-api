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
 * Short-lived user access token (JWT, HS256).
 * Signed by auth-service, verified by the API gateway ONLY. Business services never see this
 * secret, so a compromised business service cannot forge user tokens.
 */
public class AccessTokenService {

    public static final String ISSUER = "ecommerce-auth";

    private final SecretKey key;
    private final long ttlSeconds;

    public AccessTokenService(String secret, long ttlSeconds) {
        // throws WeakKeyException if the secret is shorter than 32 bytes -> fail fast on startup
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlSeconds = ttlSeconds;
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }

    public String generate(UUID userId, String email, Collection<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .claim("typ", "access")
                .claim("email", email)
                .claim("roles", List.copyOf(roles))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }

    public Optional<AuthUser> parse(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).requireIssuer(ISSUER).build()
                    .parseSignedClaims(token).getPayload();
            if (!"access".equals(c.get("typ", String.class))) return Optional.empty();
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
