package com.dmg.fooddelivery.integration;

import com.dmg.fooddelivery.dto.request.CreateMenuItemRequest;
import com.dmg.fooddelivery.dto.request.OrderItemRequest;
import com.dmg.fooddelivery.dto.request.PlaceOrderRequest;
import com.dmg.fooddelivery.dto.request.UpdateMenuItemRequest;
import com.dmg.fooddelivery.exception.ConflictException;
import com.dmg.fooddelivery.model.*;
import com.dmg.fooddelivery.repository.*;
import com.dmg.fooddelivery.security.UserPrincipal;
import com.dmg.fooddelivery.service.MenuItemService;
import com.dmg.fooddelivery.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RuntimeRegressionIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private CityRepository cities;
    @Autowired private RestaurantRepository restaurants;
    @Autowired private MenuItemRepository menus;
    @Autowired private OrderRepository orders;
    @Autowired private MenuItemService menuService;
    @Autowired private OrderService orderService;
    @Autowired private PasswordEncoder encoder;

    @AfterEach
    void clearAuthentication() {
        TestAuth.clear();
    }

    @NonNull
    private User account(Role role, boolean active) {
        return users.save(User.builder().name("Regression")
                .email("regression-" + System.nanoTime() + "@test.com")
                .passwordHash(encoder.encode("password")).role(role).active(active).build());
    }

    @NonNull
    private Restaurant restaurant(User owner, boolean active) {
        City city = cities.save(City.builder().name("Regression-" + System.nanoTime()).build());
        return restaurants.save(Restaurant.builder().name("Regression Restaurant")
                .owner(owner).city(city).address("1 Road").active(active).build());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.001", "12.345", "1000000000000000000000000000000000000"})
    void invalidPricesAreRejectedOnCreateAndUpdate(String price) throws Exception {
        User owner = account(Role.RESTAURANT_OWNER, true);
        Restaurant restaurant = restaurant(owner, true);
        long restaurantId = requireNonNull(restaurant.getId());
        MenuItem item = menus.save(MenuItem.builder().restaurant(restaurant).name("Original")
                .price(new BigDecimal("12.34")).stockQuantity(5).build());
        long itemId = requireNonNull(item.getId());
        long countBefore = menus.count();

        mvc.perform(post("/api/restaurants/{id}/menu-items", restaurantId)
                .with(requireNonNull(user(new UserPrincipal(owner)))).contentType("application/json")
                .content("{\"name\":\"Invalid\",\"price\":" + price + ",\"stockQuantity\":5}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("message").value("Validation failed"));
        mvc.perform(put("/api/menu-items/{id}", itemId)
                .with(requireNonNull(user(new UserPrincipal(owner)))).contentType("application/json")
                .content("{\"price\":" + price + "}"))
                .andExpect(status().isBadRequest());

        assertThat(menus.count()).isEqualTo(countBefore);
        assertThat(menus.findById(itemId).orElseThrow().getPrice()).isEqualByComparingTo("12.34");
    }

    @Test
    void validPricesPersistExactlyAndOmittedPriceRemainsUnchanged() {
        User owner = account(Role.RESTAURANT_OWNER, true);
        long restaurantId = requireNonNull(restaurant(owner, true).getId());
        TestAuth.loginAs(owner);
        var created = menuService.create(restaurantId,
                new CreateMenuItemRequest("Item", new BigDecimal("0.01"), 5));
        long itemId = requireNonNull(created.id());
        assertThat(menus.findById(itemId).orElseThrow().getPrice()).isEqualByComparingTo("0.01");
        menuService.update(itemId, new UpdateMenuItemRequest(null, new BigDecimal("12.34"), null, null));
        menuService.update(itemId, new UpdateMenuItemRequest(null, null, null, false));
        MenuItem saved = menus.findById(itemId).orElseThrow();
        assertThat(saved.getPrice()).isEqualByComparingTo("12.34");
        assertThat(saved.isAvailable()).isFalse();
    }

    @Test
    void inactiveRestaurantRejectsOrdersWithoutChangingStock() {
        Restaurant restaurant = restaurant(account(Role.RESTAURANT_OWNER, true), false);
        long restaurantId = requireNonNull(restaurant.getId());
        MenuItem item = menus.save(MenuItem.builder().restaurant(restaurant).name("Item")
                .price(new BigDecimal("5.00")).stockQuantity(2).build());
        long itemId = requireNonNull(item.getId());
        long countBefore = orders.count();
        TestAuth.loginAs(account(Role.CUSTOMER, true));

        assertThatThrownBy(() -> orderService.placeOrder(new PlaceOrderRequest(restaurantId,
                List.of(new OrderItemRequest(itemId, 1)))))
                .isInstanceOf(ConflictException.class).hasMessage("Restaurant is not accepting orders");
        assertThat(orders.count()).isEqualTo(countBefore);
        assertThat(menus.findById(itemId).orElseThrow().getStockQuantity()).isEqualTo(2);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"CUSTOMER", "DELIVERY_PARTNER", "ADMIN"})
    void adminCannotAssignAnOwnerWithTheWrongRole(Role role) throws Exception {
        User admin = account(Role.ADMIN, true);
        User invalidOwner = account(role, true);
        City city = cities.save(City.builder().name("Owner-role-" + System.nanoTime()).build());
        long countBefore = restaurants.count();
        mvc.perform(post("/api/restaurants").with(requireNonNull(user(new UserPrincipal(admin))))
                .contentType("application/json")
                .content("{\"name\":\"Invalid owner\",\"cityId\":" + city.getId()
                        + ",\"address\":\"1 Road\",\"ownerId\":" + invalidOwner.getId() + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("message").value("Restaurant owner must have the RESTAURANT_OWNER role"));
        assertThat(restaurants.count()).isEqualTo(countBefore);
    }

    @Test
    void adminCanAssignAnActualRestaurantOwner() throws Exception {
        User admin = account(Role.ADMIN, true);
        User owner = account(Role.RESTAURANT_OWNER, true);
        City city = cities.save(City.builder().name("Valid-owner-" + System.nanoTime()).build());
        mvc.perform(post("/api/restaurants").with(requireNonNull(user(new UserPrincipal(admin))))
                .contentType("application/json")
                .content("{\"name\":\"Valid owner\",\"cityId\":" + city.getId()
                        + ",\"address\":\"1 Road\",\"ownerId\":" + owner.getId() + "}"))
                .andExpect(status().isCreated());
    }

    @Test
    void disabledLoginIsForbiddenWhileActiveLoginStillSucceeds() throws Exception {
        User disabled = account(Role.CUSTOMER, false);
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"" + disabled.getEmail() + "\",\"password\":\"password\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("status").value(403))
                .andExpect(jsonPath("token").doesNotExist());
        User active = account(Role.CUSTOMER, true);
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"" + active.getEmail() + "\",\"password\":\"password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("token").isNotEmpty());
    }

    @Test
    void missingRoutesReturn404() throws Exception {
        mvc.perform(get("/api/restaurants/missing/path"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("status").value(404));
    }

    @Test
    void unsupportedMethodsReturn405WithAllowHeader() throws Exception {
        mvc.perform(get("/api/auth/login"))
                .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("status").value(405))
                .andExpect(header().string("Allow", requireNonNull(containsString("POST"))));
    }
}
