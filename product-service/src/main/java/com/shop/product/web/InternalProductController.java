package com.shop.product.web;

import com.shop.product.dto.ProductResponse;
import com.shop.product.dto.StockRequest;
import com.shop.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Service-to-service endpoints. The API gateway has NO route for /internal/**, and rejects it
 * explicitly, so end users can never reach these - only other services holding a forwarded
 * gateway-signed token.
 */
@RestController
@RequestMapping("/internal/products")
@RequiredArgsConstructor
public class InternalProductController {

    private final ProductService service;

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/reserve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reserve(@PathVariable UUID id, @Valid @RequestBody StockRequest req) {
        service.reserve(id, req.quantity());
    }

    @PostMapping("/{id}/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@PathVariable UUID id, @Valid @RequestBody StockRequest req) {
        service.release(id, req.quantity());
    }
}
