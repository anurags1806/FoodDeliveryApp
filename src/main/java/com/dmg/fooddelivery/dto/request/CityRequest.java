package com.dmg.fooddelivery.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CityRequest(@NotBlank String name) {}
