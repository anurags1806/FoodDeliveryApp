package com.dmg.fooddelivery.dto.response;

public record RestaurantResponse(
        Long id, String name, Long cityId, String cityName,
        Long ownerId, String address, boolean active
) {}
