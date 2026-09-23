package com.dmg.fooddelivery.dto.response;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long menuItemId, String menuItemName, int quantity, BigDecimal priceAtOrder
) {}
