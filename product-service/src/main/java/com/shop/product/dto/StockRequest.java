package com.shop.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record StockRequest(@Min(1) @Max(1000) int quantity) {}
