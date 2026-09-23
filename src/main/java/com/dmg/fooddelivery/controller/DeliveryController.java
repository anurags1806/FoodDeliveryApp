package com.dmg.fooddelivery.controller;

import com.dmg.fooddelivery.dto.request.RegisterDeliveryPartnerRequest;
import com.dmg.fooddelivery.dto.response.DeliveryPartnerResponse;
import com.dmg.fooddelivery.dto.response.OrderResponse;
import com.dmg.fooddelivery.service.DeliveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/delivery")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;

    @PostMapping("/partners")
    public ResponseEntity<DeliveryPartnerResponse> register(@Valid @RequestBody RegisterDeliveryPartnerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(deliveryService.register(request));
    }

    /** The pool of unassigned, ready-for-pickup orders in the partner's city. */
    @GetMapping("/orders/assignable")
    public ResponseEntity<List<OrderResponse>> assignable() {
        return ResponseEntity.ok(deliveryService.listAssignableOrders());
    }

    /** The contended operation - see DeliveryService#acceptOrder for the locking strategy. */
    @PostMapping("/orders/{orderId}/accept")
    public ResponseEntity<OrderResponse> accept(@PathVariable Long orderId) {
        return ResponseEntity.ok(deliveryService.acceptOrder(orderId));
    }
}
