package com.shop.common.security;

import java.util.Set;
import java.util.UUID;

/** Authenticated caller as seen by every service. id == null means "anonymous". */
public record AuthUser(UUID id, String email, Set<String> roles) {

    public static AuthUser anonymous() {
        return new AuthUser(null, null, Set.of());
    }

    public boolean isAnonymous() {
        return id == null;
    }

    public boolean hasRole(Role role) {
        return roles.contains(role.name());
    }

    public boolean isAdmin() {
        return hasRole(Role.ADMIN);
    }
}
