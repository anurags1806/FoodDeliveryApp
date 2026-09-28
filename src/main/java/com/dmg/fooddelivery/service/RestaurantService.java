package com.dmg.fooddelivery.service;

import static java.util.Objects.requireNonNull;

import com.dmg.fooddelivery.dto.request.CreateRestaurantRequest;
import com.dmg.fooddelivery.dto.response.RestaurantResponse;
import com.dmg.fooddelivery.exception.ForbiddenException;
import com.dmg.fooddelivery.exception.BadRequestException;
import com.dmg.fooddelivery.exception.NotFoundException;
import com.dmg.fooddelivery.model.City;
import com.dmg.fooddelivery.model.Restaurant;
import com.dmg.fooddelivery.model.Role;
import com.dmg.fooddelivery.model.User;
import com.dmg.fooddelivery.repository.RestaurantRepository;
import com.dmg.fooddelivery.repository.UserRepository;
import com.dmg.fooddelivery.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Validated
@RequiredArgsConstructor
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final CityService cityService;

    @Transactional
    public RestaurantResponse create(@NotNull @Valid CreateRestaurantRequest request) {
        User caller = SecurityUtils.currentUser();
        City city = cityService.getOrThrow(requireNonNull(request.cityId()));

        User owner;
        if (caller.getRole() == Role.ADMIN) {
            Long ownerId = request.ownerId();
            if (ownerId == null) {
                throw new ForbiddenException("Admin must specify ownerId when creating a restaurant");
            }
            owner = requireNonNull(userRepository.findById(ownerId)
                    .orElseThrow(() -> new NotFoundException("Owner not found: " + ownerId)));
        } else if (caller.getRole() == Role.RESTAURANT_OWNER) {
            // RESTAURANT_OWNER creating their own restaurant
            owner = caller;
        } else {
            throw new ForbiddenException("Only admins and restaurant owners can create restaurants");
        }
        if (owner.getRole() != Role.RESTAURANT_OWNER) {
            throw new BadRequestException("Restaurant owner must have the RESTAURANT_OWNER role");
        }

        Restaurant restaurant = Restaurant.builder()
                .name(request.name())
                .city(city)
                .owner(owner)
                .address(request.address())
                .build();
        restaurant = restaurantRepository.save(restaurant);
        return toResponse(restaurant);
    }

    public List<RestaurantResponse> listByCity(@NotNull Long cityId) {
        return requireNonNull(restaurantRepository.findByCityIdAndActiveTrue(cityId).stream().map(this::toResponse).toList());
    }

    public List<RestaurantResponse> listAll() {
        return requireNonNull(restaurantRepository.findAll().stream().map(this::toResponse).toList());
    }

    public RestaurantResponse getById(@NotNull Long id) {
        return toResponse(getEntityOrThrow(id));
    }

    public Restaurant getEntityOrThrow(@NotNull Long id) {
        return requireNonNull(restaurantRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Restaurant not found: " + id)));
    }

    /** Restaurant owners may only manage their own restaurant; admins may manage any. */
    public void assertOwnershipOrAdmin(@NotNull Restaurant restaurant, @NotNull User caller) {
        if (caller.getRole() == Role.ADMIN) return;
        if (!restaurant.getOwner().getId().equals(caller.getId())) {
            throw new ForbiddenException("You do not own this restaurant");
        }
    }

    private RestaurantResponse toResponse(Restaurant r) {
        return new RestaurantResponse(r.getId(), r.getName(), r.getCity().getId(), r.getCity().getName(),
                r.getOwner().getId(), r.getAddress(), r.isActive());
    }
}
