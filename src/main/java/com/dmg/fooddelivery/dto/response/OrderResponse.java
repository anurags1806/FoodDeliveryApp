package com.dmg.fooddelivery.dto.response;

import com.dmg.fooddelivery.model.OrderStatus;
import com.dmg.fooddelivery.model.PaymentStatus;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        Long customerId,
        Long restaurantId,
        String restaurantName,
        List<OrderItemResponse> items,
        OrderStatus status,
        PaymentStatus paymentStatus,
        BigDecimal totalAmount,
        @Nullable Long deliveryPartnerId,
        Instant createdAt,
        @Nullable Instant updatedAt
) {}
