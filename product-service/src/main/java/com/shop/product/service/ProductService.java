package com.shop.product.service;

import com.shop.common.security.AuthUser;
import com.shop.common.web.ApiException;
import com.shop.product.domain.Product;
import com.shop.product.dto.ProductRequest;
import com.shop.product.dto.ProductResponse;
import com.shop.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository products;

    @Transactional(readOnly = true)
    public Page<ProductResponse> list(String q, Pageable pageable) {
        return products.findByActiveTrueAndNameContainingIgnoreCase(q == null ? "" : q.trim(), pageable)
                .map(ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        return ProductResponse.from(findActive(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest req, AuthUser user) {
        Product p = new Product();
        apply(p, req);
        p.setSellerId(user.id());
        return ProductResponse.from(products.save(p));
    }

    @Transactional
    public ProductResponse update(UUID id, ProductRequest req, AuthUser user) {
        Product p = findActive(id);
        assertCanModify(p, user);
        apply(p, req);
        return ProductResponse.from(p);
    }

    @Transactional
    public void delete(UUID id, AuthUser user) {
        Product p = findActive(id);
        assertCanModify(p, user);
        p.setActive(false);       // soft delete keeps order history intact
    }

    // ---- internal (service-to-service) ----

    @Transactional
    public void reserve(UUID id, int qty) {
        if (products.reserve(id, qty) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Insufficient stock or product unavailable");
        }
    }

    @Transactional
    public void release(UUID id, int qty) {
        products.release(id, qty);
    }

    private Product findActive(UUID id) {
        return products.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    /** RBAC says SELLER/ADMIN may write; ownership says a SELLER may only touch their own products. */
    private void assertCanModify(Product p, AuthUser user) {
        if (!user.isAdmin() && !p.getSellerId().equals(user.id())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You do not own this product");
        }
    }

    private void apply(Product p, ProductRequest r) {
        p.setName(r.name().trim());
        p.setDescription(r.description());
        p.setPrice(r.price());
        p.setStock(r.stock());
    }
}
