package com.shop.auth.web;

import com.shop.auth.dto.UpdateRolesRequest;
import com.shop.auth.dto.UpdateStatusRequest;
import com.shop.auth.dto.UserResponse;
import com.shop.auth.service.UserAdminService;
import com.shop.common.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserAdminService admin;

    @GetMapping
    public Page<UserResponse> list(Pageable pageable) {
        return admin.list(pageable);
    }

    @PutMapping("/{id}/roles")
    public UserResponse updateRoles(@PathVariable UUID id, @Valid @RequestBody UpdateRolesRequest req,
                                    @AuthenticationPrincipal AuthUser actor) {
        return admin.updateRoles(id, req.roles(), actor);
    }

    @PutMapping("/{id}/status")
    public UserResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateStatusRequest req,
                                     @AuthenticationPrincipal AuthUser actor) {
        return admin.setEnabled(id, req.enabled(), actor);
    }
}
