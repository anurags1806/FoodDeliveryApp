package com.dmg.fooddelivery.service;

import com.dmg.fooddelivery.dto.request.CreateMenuItemRequest;
import com.dmg.fooddelivery.dto.request.UpdateMenuItemRequest;
import com.dmg.fooddelivery.dto.response.MenuItemResponse;
import com.dmg.fooddelivery.exception.NotFoundException;
import com.dmg.fooddelivery.model.MenuItem;
import com.dmg.fooddelivery.model.Restaurant;
import com.dmg.fooddelivery.model.User;
import com.dmg.fooddelivery.repository.MenuItemRepository;
import com.dmg.fooddelivery.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MenuItemService {

    private final MenuItemRepository menuItemRepository;
    private final RestaurantService restaurantService;

    @Transactional
    public MenuItemResponse create(Long restaurantId, CreateMenuItemRequest request) {
        Restaurant restaurant = restaurantService.getEntityOrThrow(restaurantId);
        User caller = SecurityUtils.currentUser();
        restaurantService.assertOwnershipOrAdmin(restaurant, caller);

        MenuItem item = MenuItem.builder()
                .restaurant(restaurant)
                .name(request.name())
                .price(request.price())
                .stockQuantity(request.stockQuantity())
                .available(true)
                .build();
        item = menuItemRepository.save(item);
        return toResponse(item);
    }

    @Transactional
    public MenuItemResponse update(Long menuItemId, UpdateMenuItemRequest request) {
        MenuItem item = getEntityOrThrow(menuItemId);
        User caller = SecurityUtils.currentUser();
        restaurantService.assertOwnershipOrAdmin(item.getRestaurant(), caller);

        if (request.name() != null) item.setName(request.name());
        if (request.price() != null) item.setPrice(request.price());
        if (request.stockQuantity() != null) item.setStockQuantity(request.stockQuantity());
        if (request.available() != null) item.setAvailable(request.available());
        // @Version on MenuItem guards this against lost updates if a
        // concurrent order-placement decrement is racing the same row.
        return toResponse(item);
    }

    @Transactional
    public void delete(Long menuItemId) {
        MenuItem item = getEntityOrThrow(menuItemId);
        User caller = SecurityUtils.currentUser();
        restaurantService.assertOwnershipOrAdmin(item.getRestaurant(), caller);
        menuItemRepository.delete(item);
    }

    public List<MenuItemResponse> listByRestaurant(Long restaurantId) {
        return menuItemRepository.findByRestaurantId(restaurantId).stream().map(this::toResponse).toList();
    }

    public MenuItem getEntityOrThrow(Long id) {
        return menuItemRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Menu item not found: " + id));
    }

    private MenuItemResponse toResponse(MenuItem m) {
        return new MenuItemResponse(m.getId(), m.getRestaurant().getId(), m.getName(), m.getPrice(),
                m.getStockQuantity(), m.isAvailable());
    }
}
