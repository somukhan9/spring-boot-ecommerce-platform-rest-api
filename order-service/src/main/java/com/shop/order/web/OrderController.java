package com.shop.order.web;

import com.shop.common.security.AuthUser;
import com.shop.order.dto.OrderResponse;
import com.shop.order.dto.PlaceOrderRequest;
import com.shop.order.dto.UpdateStatusRequest;
import com.shop.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    public OrderResponse place(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody PlaceOrderRequest req) {
        return service.place(user, req);
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('CUSTOMER')")
    public Page<OrderResponse> mine(@AuthenticationPrincipal AuthUser user, Pageable pageable) {
        return service.myOrders(user.id(), pageable);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<OrderResponse> all(Pageable pageable) {
        return service.all(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public OrderResponse get(@PathVariable UUID id, @AuthenticationPrincipal AuthUser user) {
        return service.get(id, user);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public OrderResponse cancel(@PathVariable UUID id, @AuthenticationPrincipal AuthUser user) {
        return service.cancel(id, user);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public OrderResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateStatusRequest req) {
        return service.updateStatus(id, req.status());
    }
}
