package com.dmg.fooddelivery.controller;

import com.dmg.fooddelivery.dto.request.PlaceOrderRequest;
import com.dmg.fooddelivery.dto.request.RatingRequest;
import com.dmg.fooddelivery.dto.request.UpdateOrderStatusRequest;
import com.dmg.fooddelivery.dto.response.OrderResponse;
import com.dmg.fooddelivery.dto.response.RatingResponse;
import com.dmg.fooddelivery.exception.ForbiddenException;
import com.dmg.fooddelivery.model.Order;
import com.dmg.fooddelivery.model.Role;
import com.dmg.fooddelivery.model.User;
import com.dmg.fooddelivery.security.SecurityUtils;
import com.dmg.fooddelivery.service.OrderService;
import com.dmg.fooddelivery.service.RatingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final RatingService ratingService;

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.placeOrder(request));
    }

    @GetMapping("/mine")
    public ResponseEntity<List<OrderResponse>> myOrders() {
        return ResponseEntity.ok(orderService.listForCustomer(SecurityUtils.currentUserId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getById(@PathVariable Long id) {
        Order order = orderService.getEntityOrThrow(id);
        assertCanView(order);
        return ResponseEntity.ok(orderService.toResponse(order));
    }

    /**
     * Generic status transition endpoint used by restaurant owners
     * (ACCEPTED/REJECTED/PREPARING), delivery partners
     * (OUT_FOR_DELIVERY/DELIVERED), and customers (CANCELLED). Role- and
     * ownership-specific authorization happens in OrderService so the
     * exact same endpoint enforces "only the assigned partner", "only the
     * owning restaurant", etc.
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateStatus(@PathVariable Long id,
                                                        @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ResponseEntity.ok(orderService.updateStatus(id, request.status()));
    }

    @PostMapping("/{id}/rating")
    public ResponseEntity<RatingResponse> rate(@PathVariable Long id, @Valid @RequestBody RatingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ratingService.rate(id, request));
    }

    private void assertCanView(Order order) {
        User caller = SecurityUtils.currentUser();
        boolean allowed = switch (caller.getRole()) {
            case ADMIN -> true;
            case CUSTOMER -> order.getCustomer().getId().equals(caller.getId());
            case RESTAURANT_OWNER -> order.getRestaurant().getOwner().getId().equals(caller.getId());
            case DELIVERY_PARTNER -> order.getDeliveryPartner() != null
                    && order.getDeliveryPartner().getUser().getId().equals(caller.getId());
        };
        if (!allowed) {
            throw new ForbiddenException("You do not have access to this order");
        }
    }
}
