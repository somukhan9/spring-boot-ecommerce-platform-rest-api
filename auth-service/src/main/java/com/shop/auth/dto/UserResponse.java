package com.shop.auth.dto;

import com.shop.auth.domain.User;
import com.shop.common.security.Role;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserResponse(UUID id, String email, String fullName, boolean enabled, Set<Role> roles, Instant createdAt) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getFullName(), u.isEnabled(), Set.copyOf(u.getRoles()), u.getCreatedAt());
    }
}
