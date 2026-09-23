package com.dmg.fooddelivery.dto.request;

import com.dmg.fooddelivery.model.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(@NotNull OrderStatus status) {}
