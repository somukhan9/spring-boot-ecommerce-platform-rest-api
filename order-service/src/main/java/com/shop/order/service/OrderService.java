package com.shop.order.service;

import com.shop.common.security.AuthUser;
import com.shop.common.web.ApiException;
import com.shop.order.client.ProductClient;
import com.shop.order.client.ProductClient.ProductView;
import com.shop.order.domain.Order;
import com.shop.order.domain.OrderItem;
import com.shop.order.domain.OrderStatus;
import com.shop.order.dto.OrderResponse;
import com.shop.order.dto.PlaceOrderRequest;
import com.shop.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orders;
    private final ProductClient products;

    /**
     * Not @Transactional on purpose: remote calls must not hold a DB connection.
     * Stock is reserved first; on any failure the reservations are compensated (simple saga).
     */
    public OrderResponse place(AuthUser user, PlaceOrderRequest req) {
        Map<UUID, Integer> wanted = new LinkedHashMap<>();
        req.items().forEach(i -> wanted.merge(i.productId(), i.quantity(), Integer::sum));

        Map<UUID, Integer> reserved = new LinkedHashMap<>();
        try {
            Order order = new Order();
            order.setCustomerId(user.id());
            BigDecimal total = BigDecimal.ZERO;

            for (var e : wanted.entrySet()) {
                ProductView p = products.get(e.getKey());      // price comes from product-service, never from the client
                products.reserve(e.getKey(), e.getValue());
                reserved.put(e.getKey(), e.getValue());

                OrderItem item = new OrderItem();
                item.setProductId(p.id());
                item.setProductName(p.name());
                item.setUnitPrice(p.price());
                item.setQuantity(e.getValue());
                order.addItem(item);
                total = total.add(p.price().multiply(BigDecimal.valueOf(e.getValue())));
            }
            order.setTotal(total);
            return OrderResponse.from(orders.save(order));
        } catch (RuntimeException ex) {
            reserved.forEach((id, qty) -> releaseQuietly(id, qty));
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> myOrders(UUID customerId, Pageable pageable) {
        return orders.findByCustomerId(customerId, pageable).map(OrderResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> all(Pageable pageable) {
        return orders.findAll(pageable).map(OrderResponse::from);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID id, AuthUser user) {
        return OrderResponse.from(loadVisible(id, user));
    }

    @Transactional
    public OrderResponse cancel(UUID id, AuthUser user) {
        Order o = loadVisible(id, user);
        if (!user.isAdmin() && o.getStatus() != OrderStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "Only pending orders can be cancelled");
        }
        transition(o, OrderStatus.CANCELLED);
        return OrderResponse.from(o);
    }

    @Transactional
    public OrderResponse updateStatus(UUID id, OrderStatus next) {
        Order o = orders.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found"));
        transition(o, next);
        return OrderResponse.from(o);
    }

    /** Owner or ADMIN. Others get 404 (not 403) so order ids cannot be enumerated. */
    private Order loadVisible(UUID id, AuthUser user) {
        Order o = orders.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found"));
        if (!user.isAdmin() && !o.getCustomerId().equals(user.id())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Order not found");
        }
        return o;
    }

    private void transition(Order o, OrderStatus next) {
        if (!o.getStatus().canMoveTo(next)) {
            throw new ApiException(HttpStatus.CONFLICT, "Cannot move order from " + o.getStatus() + " to " + next);
        }
        if (next == OrderStatus.CANCELLED) {
            o.getItems().forEach(i -> releaseQuietly(i.getProductId(), i.getQuantity()));
        }
        o.setStatus(next);
    }

    private void releaseQuietly(UUID productId, int qty) {
        try {
            products.release(productId, qty);
        } catch (RuntimeException e) {
            log.error("Failed to release {} units of product {} - needs manual reconciliation", qty, productId, e);
        }
    }
}
