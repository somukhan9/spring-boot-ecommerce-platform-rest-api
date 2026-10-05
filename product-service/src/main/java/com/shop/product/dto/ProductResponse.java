package com.shop.product.dto;

import com.shop.product.domain.Product;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponse(UUID id, String name, String description, BigDecimal price, int stock, UUID sellerId, boolean active) {
    public static ProductResponse from(Product p) {
        return new ProductResponse(p.getId(), p.getName(), p.getDescription(), p.getPrice(), p.getStock(), p.getSellerId(), p.isActive());
    }
}
