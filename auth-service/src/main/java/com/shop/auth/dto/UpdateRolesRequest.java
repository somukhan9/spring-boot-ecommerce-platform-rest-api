package com.shop.auth.dto;

import com.shop.common.security.Role;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record UpdateRolesRequest(@NotEmpty Set<Role> roles) {}
