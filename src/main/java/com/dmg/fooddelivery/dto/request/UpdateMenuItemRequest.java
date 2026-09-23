package com.dmg.fooddelivery.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

public record UpdateMenuItemRequest(
        String name,
        @DecimalMin(value = "0.0", inclusive = false) BigDecimal price,
        @Min(0) Integer stockQuantity,
        Boolean available
) {}
