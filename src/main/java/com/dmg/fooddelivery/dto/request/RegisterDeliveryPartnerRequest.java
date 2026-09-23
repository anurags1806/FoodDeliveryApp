package com.dmg.fooddelivery.dto.request;

import jakarta.validation.constraints.NotNull;

public record RegisterDeliveryPartnerRequest(@NotNull Long cityId) {}
