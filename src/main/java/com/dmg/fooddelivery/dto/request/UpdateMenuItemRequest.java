package com.dmg.fooddelivery.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;

public record UpdateMenuItemRequest(
        @Nullable String name,
        @Nullable @DecimalMin(value = "0.0", inclusive = false)
        @Digits(integer = 36, fraction = 2, message = "price must fit 36 integer digits and at most 2 decimal places") BigDecimal price,
        @Nullable @Min(0) Integer stockQuantity,
        @Nullable Boolean available
) {}
