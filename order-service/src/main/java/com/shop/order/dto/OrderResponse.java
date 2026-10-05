package com.shop.order.dto;

import com.shop.order.domain.Order;
import com.shop.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(UUID id, UUID customerId, OrderStatus status, BigDecimal total, Instant createdAt, List<Item> items) {

    public record Item(UUID productId, String productName, BigDecimal unitPrice, int quantity) {}

    public static OrderResponse from(Order o) {
        return new OrderResponse(o.getId(), o.getCustomerId(), o.getStatus(), o.getTotal(), o.getCreatedAt(),
                o.getItems().stream()
                        .map(i -> new Item(i.getProductId(), i.getProductName(), i.getUnitPrice(), i.getQuantity()))
                        .toList());
    }
}
