package com.shop.auth.service;

import com.shop.auth.domain.User;
import com.shop.auth.dto.UserResponse;
import com.shop.auth.repository.RefreshTokenRepository;
import com.shop.auth.repository.UserRepository;
import com.shop.common.security.AuthUser;
import com.shop.common.security.Role;
import com.shop.common.web.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;

    @Transactional(readOnly = true)
    public Page<UserResponse> list(Pageable pageable) {
        return users.findAll(pageable).map(UserResponse::from);
    }

    @Transactional
    public UserResponse updateRoles(UUID id, Set<Role> roles, AuthUser actor) {
        User u = load(id, actor);
        u.setRoles(new HashSet<>(roles));
        // Force re-login so new roles reach the next access token instead of waiting for a stale refresh.
        refreshTokens.revokeAllForUser(id);
        return UserResponse.from(u);
    }

    @Transactional
    public UserResponse setEnabled(UUID id, boolean enabled, AuthUser actor) {
        User u = load(id, actor);
        u.setEnabled(enabled);
        if (!enabled) refreshTokens.revokeAllForUser(id);
        return UserResponse.from(u);
    }

    private User load(UUID id, AuthUser actor) {
        if (id.equals(actor.id())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot modify your own account here");
        }
        return users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
