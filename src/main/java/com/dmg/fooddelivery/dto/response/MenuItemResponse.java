package com.dmg.fooddelivery.dto.response;

import java.math.BigDecimal;

public record MenuItemResponse(
        Long id, Long restaurantId, String name, BigDecimal price,
        Integer stockQuantity, boolean available
) {}
