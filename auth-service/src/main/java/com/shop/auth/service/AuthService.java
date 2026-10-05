package com.shop.auth.service;

import com.shop.auth.domain.RefreshToken;
import com.shop.auth.domain.User;
import com.shop.auth.dto.*;
import com.shop.auth.repository.RefreshTokenRepository;
import com.shop.auth.repository.UserRepository;
import com.shop.common.security.AccessTokenService;
import com.shop.common.security.Role;
import com.shop.common.web.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final AccessTokenService accessTokens;

    @Value("${app.security.refresh-token-ttl-days}")
    private long refreshTtlDays;

    @Transactional
    public UserResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already registered");
        }
        User u = new User();
        u.setEmail(email);
        u.setFullName(req.fullName().trim());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setRoles(Set.of(Role.CUSTOMER));      // never self-assign privileged roles; ADMIN grants SELLER/ADMIN
        return UserResponse.from(users.save(u));
    }

    @Transactional
    public TokenResponse login(LoginRequest req) {
        User u = users.findByEmail(req.email().trim().toLowerCase(Locale.ROOT)).orElse(null);
        if (u == null || !u.isEnabled() || !encoder.matches(req.password(), u.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return issue(u, UUID.randomUUID());
    }

    /**
     * Refresh-token rotation with reuse detection.
     * noRollbackFor: when reuse is detected we must COMMIT the family revocation even though we throw.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse refresh(String rawToken) {
        RefreshToken t = refreshTokens.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        if (t.isRevoked()) {
            // An already-used token came back: assume theft, kill every token descended from this login.
            refreshTokens.revokeFamily(t.getFamilyId());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token reuse detected, please log in again");
        }
        if (t.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
        }
        User u = t.getUser();
        if (!u.isEnabled()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Account disabled");
        }
        t.setRevoked(true);                       // single use
        return issue(u, t.getFamilyId());         // new token in the same family, fresh roles from DB
    }

    @Transactional
    public void logout(String rawToken) {
        refreshTokens.findByTokenHash(hash(rawToken))
                .ifPresent(t -> refreshTokens.revokeFamily(t.getFamilyId()));
    }

    @Transactional
    public void logoutAll(UUID userId) {
        refreshTokens.revokeAllForUser(userId);
    }

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        return users.findById(userId).map(UserResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private TokenResponse issue(User u, UUID familyId) {
        String access = accessTokens.generate(u.getId(), u.getEmail(), u.getRoles().stream().map(Enum::name).toList());

        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken rt = new RefreshToken();
        rt.setTokenHash(hash(raw));
        rt.setFamilyId(familyId);
        rt.setUser(u);
        rt.setExpiresAt(Instant.now().plus(Duration.ofDays(refreshTtlDays)));
        refreshTokens.save(rt);

        return new TokenResponse(access, raw, "Bearer", accessTokens.ttlSeconds());
    }

    private static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
