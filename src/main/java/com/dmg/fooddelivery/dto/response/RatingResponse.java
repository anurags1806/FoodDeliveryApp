package com.dmg.fooddelivery.dto.response;

import java.time.Instant;
import org.springframework.lang.Nullable;

public record RatingResponse(
        Long id, Long orderId, Long restaurantId, Integer score, @Nullable String comment, Instant createdAt
) {}
