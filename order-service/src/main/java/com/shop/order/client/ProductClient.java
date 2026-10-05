package com.shop.order.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.shop.common.security.InternalAuthFilter;
import com.shop.common.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/** Calls product-service's /internal API, forwarding the caller's gateway-signed token. */
@Component
public class ProductClient {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProductView(UUID id, String name, BigDecimal price, int stock) {}

    private final RestClient rest;

    public ProductClient(@Value("${services.product.url}") String baseUrl) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(2000);
        f.setReadTimeout(5000);
        this.rest = RestClient.builder().baseUrl(baseUrl).requestFactory(f).build();
    }

    public ProductView get(UUID id) {
        return rest.get().uri("/internal/products/{id}", id)
                .header(InternalAuthFilter.HEADER, token())
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> { throw map(res.getStatusCode()); })
                .body(ProductView.class);
    }

    public void reserve(UUID id, int qty) {
        post("/internal/products/{id}/reserve", id, qty);
    }

    public void release(UUID id, int qty) {
        post("/internal/products/{id}/release", id, qty);
    }

    private void post(String path, UUID id, int qty) {
        rest.post().uri(path, id)
                .header(InternalAuthFilter.HEADER, token())
                .body(Map.of("quantity", qty))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> { throw map(res.getStatusCode()); })
                .toBodilessEntity();
    }

    private static ApiException map(HttpStatusCode s) {
        return switch (s.value()) {
            case 404 -> new ApiException(HttpStatus.NOT_FOUND, "Product not found");
            case 409 -> new ApiException(HttpStatus.CONFLICT, "Insufficient stock or product unavailable");
            default -> new ApiException(HttpStatus.BAD_GATEWAY, "Product service unavailable");
        };
    }

    private static String token() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getCredentials() instanceof String t)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return t;
    }
}
