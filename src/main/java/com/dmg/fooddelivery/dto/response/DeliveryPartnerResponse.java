package com.dmg.fooddelivery.dto.response;

import com.dmg.fooddelivery.model.DeliveryPartnerStatus;

public record DeliveryPartnerResponse(
        Long id, Long userId, String name, Long cityId, DeliveryPartnerStatus status
) {}
