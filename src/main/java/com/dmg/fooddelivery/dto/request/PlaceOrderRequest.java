package com.dmg.fooddelivery.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record PlaceOrderRequest(
        @NotNull Long restaurantId,
        @NotEmpty @Valid List<OrderItemRequest> items
) {}
