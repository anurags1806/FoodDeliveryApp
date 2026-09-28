package com.dmg.fooddelivery.controller;

import com.dmg.fooddelivery.dto.request.CreateMenuItemRequest;
import com.dmg.fooddelivery.dto.request.UpdateMenuItemRequest;
import com.dmg.fooddelivery.dto.response.MenuItemResponse;
import com.dmg.fooddelivery.service.MenuItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class MenuItemController {

    private final MenuItemService menuItemService;

    @PostMapping("/api/restaurants/{restaurantId}/menu-items")
    public ResponseEntity<MenuItemResponse> create(@NonNull @PathVariable Long restaurantId,
                                                     @NonNull @Valid @RequestBody CreateMenuItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(menuItemService.create(restaurantId, request));
    }

    @GetMapping("/api/restaurants/{restaurantId}/menu-items")
    public ResponseEntity<List<MenuItemResponse>> listByRestaurant(@NonNull @PathVariable Long restaurantId) {
        return ResponseEntity.ok(menuItemService.listByRestaurant(restaurantId));
    }

    @PutMapping("/api/menu-items/{id}")
    public ResponseEntity<MenuItemResponse> update(@NonNull @PathVariable Long id,
                                                     @NonNull @Valid @RequestBody UpdateMenuItemRequest request) {
        return ResponseEntity.ok(menuItemService.update(id, request));
    }

    @DeleteMapping("/api/menu-items/{id}")
    public ResponseEntity<Void> delete(@NonNull @PathVariable Long id) {
        menuItemService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
