package com.dmg.fooddelivery.dto.response;

import java.time.Instant;

public record RatingResponse(
        Long id, Long orderId, Long restaurantId, Integer score, String comment, Instant createdAt
) {}
