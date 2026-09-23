package com.dmg.fooddelivery.controller;

import com.dmg.fooddelivery.dto.request.CreateRestaurantRequest;
import com.dmg.fooddelivery.dto.response.OrderResponse;
import com.dmg.fooddelivery.dto.response.RatingResponse;
import com.dmg.fooddelivery.dto.response.RestaurantResponse;
import com.dmg.fooddelivery.service.OrderService;
import com.dmg.fooddelivery.service.RatingService;
import com.dmg.fooddelivery.service.RestaurantService;
import com.dmg.fooddelivery.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/restaurants")
@RequiredArgsConstructor
public class RestaurantController {

    private final RestaurantService restaurantService;
    private final OrderService orderService;
    private final RatingService ratingService;

    @PostMapping
    public ResponseEntity<RestaurantResponse> create(@Valid @RequestBody CreateRestaurantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(restaurantService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<RestaurantResponse>> list(@RequestParam(required = false) Long cityId) {
        if (cityId != null) {
            return ResponseEntity.ok(restaurantService.listByCity(cityId));
        }
        return ResponseEntity.ok(restaurantService.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RestaurantResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(restaurantService.getById(id));
    }

    /** Restaurant owner (or admin) views all orders placed at their restaurant. */
    @GetMapping("/{id}/orders")
    public ResponseEntity<List<OrderResponse>> ordersForRestaurant(@PathVariable Long id) {
        restaurantService.assertOwnershipOrAdmin(restaurantService.getEntityOrThrow(id),
                SecurityUtils.currentUser());
        return ResponseEntity.ok(orderService.listForRestaurant(id));
    }

    @GetMapping("/{id}/ratings")
    public ResponseEntity<List<RatingResponse>> ratingsForRestaurant(@PathVariable Long id) {
        return ResponseEntity.ok(ratingService.listForRestaurant(id));
    }
}
