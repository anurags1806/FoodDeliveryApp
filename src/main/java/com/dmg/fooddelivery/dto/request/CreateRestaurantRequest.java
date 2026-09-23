package com.dmg.fooddelivery.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateRestaurantRequest(
        @NotBlank String name,
        @NotNull Long cityId,
        @NotBlank String address,
        /** Admins may create a restaurant on behalf of an owner; owners
         *  creating their own restaurant may omit this and it defaults to
         *  the authenticated user. */
        Long ownerId
) {}
