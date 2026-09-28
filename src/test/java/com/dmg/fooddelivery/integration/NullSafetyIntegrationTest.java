package com.dmg.fooddelivery.integration;

import com.dmg.fooddelivery.dto.request.OrderItemRequest;
import com.dmg.fooddelivery.dto.request.PlaceOrderRequest;
import com.dmg.fooddelivery.dto.request.UpdateMenuItemRequest;
import com.dmg.fooddelivery.service.*;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class NullSafetyIntegrationTest {
    @Autowired private AuthService auth;
    @Autowired private CityService cities;
    @Autowired private DeliveryService delivery;
    @Autowired private MenuItemService menu;
    @Autowired private OrderService orders;
    @Autowired private RatingService ratings;
    @Autowired private RestaurantService restaurants;
    @Autowired private Validator validator;

    @Test
    void requiredServiceInputsAreValidatedBeforeBusinessLogic() {
        assertThatThrownBy(() -> auth.login(null)).isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> cities.getOrThrow(null)).isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> delivery.acceptOrder(null)).isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> menu.update(1L, null)).isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> orders.placeOrder(null)).isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> orders.updateStatus(1L, null)).isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> ratings.rate(1L, null)).isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> restaurants.create(null)).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void nestedOrderInputsAreValidatedForNonHttpCallers() {
        for (PlaceOrderRequest request : List.of(
                new PlaceOrderRequest(1L, null),
                new PlaceOrderRequest(1L, Collections.singletonList(null)),
                new PlaceOrderRequest(1L, List.of(new OrderItemRequest(null, 1))),
                new PlaceOrderRequest(null, List.of(new OrderItemRequest(1L, 1))))) {
            assertThatThrownBy(() -> orders.placeOrder(request)).isInstanceOf(ConstraintViolationException.class);
        }
    }

    @Test
    void optionalMenuUpdateFieldsRemainNullable() {
        assertThat(validator.validate(new UpdateMenuItemRequest(null, null, null, null))).isEmpty();
    }
}
