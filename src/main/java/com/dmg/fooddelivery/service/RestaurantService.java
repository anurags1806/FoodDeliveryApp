package com.dmg.fooddelivery.service;

import com.dmg.fooddelivery.dto.request.CreateRestaurantRequest;
import com.dmg.fooddelivery.dto.response.RestaurantResponse;
import com.dmg.fooddelivery.exception.ForbiddenException;
import com.dmg.fooddelivery.exception.NotFoundException;
import com.dmg.fooddelivery.model.City;
import com.dmg.fooddelivery.model.Restaurant;
import com.dmg.fooddelivery.model.Role;
import com.dmg.fooddelivery.model.User;
import com.dmg.fooddelivery.repository.RestaurantRepository;
import com.dmg.fooddelivery.repository.UserRepository;
import com.dmg.fooddelivery.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final CityService cityService;

    @Transactional
    public RestaurantResponse create(CreateRestaurantRequest request) {
        User caller = SecurityUtils.currentUser();
        City city = cityService.getOrThrow(request.cityId());

        User owner;
        if (caller.getRole() == Role.ADMIN) {
            Long ownerId = request.ownerId();
            if (ownerId == null) {
                throw new ForbiddenException("Admin must specify ownerId when creating a restaurant");
            }
            owner = userRepository.findById(ownerId)
                    .orElseThrow(() -> new NotFoundException("Owner not found: " + ownerId));
        } else {
            // RESTAURANT_OWNER creating their own restaurant
            owner = caller;
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

    public List<RestaurantResponse> listByCity(Long cityId) {
        return restaurantRepository.findByCityIdAndActiveTrue(cityId).stream().map(this::toResponse).toList();
    }

    public List<RestaurantResponse> listAll() {
        return restaurantRepository.findAll().stream().map(this::toResponse).toList();
    }

    public RestaurantResponse getById(Long id) {
        return toResponse(getEntityOrThrow(id));
    }

    public Restaurant getEntityOrThrow(Long id) {
        return restaurantRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Restaurant not found: " + id));
    }

    /** Restaurant owners may only manage their own restaurant; admins may manage any. */
    public void assertOwnershipOrAdmin(Restaurant restaurant, User caller) {
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
