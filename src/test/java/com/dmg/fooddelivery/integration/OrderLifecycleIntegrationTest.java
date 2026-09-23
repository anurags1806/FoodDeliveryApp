package com.dmg.fooddelivery.integration;

import com.dmg.fooddelivery.dto.request.OrderItemRequest;
import com.dmg.fooddelivery.dto.request.PlaceOrderRequest;
import com.dmg.fooddelivery.dto.response.OrderResponse;
import com.dmg.fooddelivery.exception.ConflictException;
import com.dmg.fooddelivery.exception.ForbiddenException;
import com.dmg.fooddelivery.model.*;
import com.dmg.fooddelivery.repository.*;
import com.dmg.fooddelivery.service.DeliveryService;
import com.dmg.fooddelivery.service.OrderService;
import com.dmg.fooddelivery.service.RatingService;
import com.dmg.fooddelivery.dto.request.RatingRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class OrderLifecycleIntegrationTest {

    @Autowired private OrderService orderService;
    @Autowired private DeliveryService deliveryService;
    @Autowired private RatingService ratingService;
    @Autowired private UserRepository userRepository;
    @Autowired private CityRepository cityRepository;
    @Autowired private RestaurantRepository restaurantRepository;
    @Autowired private MenuItemRepository menuItemRepository;
    @Autowired private DeliveryPartnerRepository deliveryPartnerRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private City city;
    private User ownerUser;
    private User customerUser;
    private User partnerUser;
    private Restaurant restaurant;
    private MenuItem menuItem;

    @BeforeEach
    void setUp() {
        city = cityRepository.save(City.builder().name("LifecycleCity-" + System.nanoTime()).build());
        ownerUser = userRepository.save(User.builder()
                .name("Owner").email("owner-" + System.nanoTime() + "@test.com")
                .passwordHash(passwordEncoder.encode("pw")).role(Role.RESTAURANT_OWNER).build());
        customerUser = userRepository.save(User.builder()
                .name("Cust").email("cust-" + System.nanoTime() + "@test.com")
                .passwordHash(passwordEncoder.encode("pw")).role(Role.CUSTOMER).build());
        partnerUser = userRepository.save(User.builder()
                .name("Partner").email("partner-" + System.nanoTime() + "@test.com")
                .passwordHash(passwordEncoder.encode("pw")).role(Role.DELIVERY_PARTNER).build());

        restaurant = restaurantRepository.save(Restaurant.builder()
                .name("Lifecycle Restaurant").city(city).owner(ownerUser).address("1 Rd").build());
        menuItem = menuItemRepository.save(MenuItem.builder()
                .restaurant(restaurant).name("Pizza").price(new BigDecimal("12.00"))
                .stockQuantity(5).available(true).build());
        deliveryPartnerRepository.save(DeliveryPartner.builder()
                .user(partnerUser).city(city).status(DeliveryPartnerStatus.AVAILABLE).build());
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    @Test
    void fullHappyPathFromPlacementToRating() {
        TestAuth.loginAs(customerUser);
        OrderResponse placed = orderService.placeOrder(new PlaceOrderRequest(
                restaurant.getId(), List.of(new OrderItemRequest(menuItem.getId(), 2))));
        assertThat(placed.status()).isEqualTo(OrderStatus.PLACED);
        assertThat(placed.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(placed.totalAmount()).isEqualByComparingTo("24.00");

        MenuItem afterOrder = menuItemRepository.findById(menuItem.getId()).orElseThrow();
        assertThat(afterOrder.getStockQuantity()).isEqualTo(3);

        TestAuth.loginAs(ownerUser);
        orderService.updateStatus(placed.id(), OrderStatus.ACCEPTED);
        OrderResponse preparing = orderService.updateStatus(placed.id(), OrderStatus.PREPARING);
        assertThat(preparing.status()).isEqualTo(OrderStatus.PREPARING);

        TestAuth.loginAs(partnerUser);
        OrderResponse assigned = deliveryService.acceptOrder(placed.id());
        assertThat(assigned.deliveryPartnerId()).isNotNull();

        orderService.updateStatus(placed.id(), OrderStatus.OUT_FOR_DELIVERY);
        OrderResponse delivered = orderService.updateStatus(placed.id(), OrderStatus.DELIVERED);
        assertThat(delivered.status()).isEqualTo(OrderStatus.DELIVERED);

        DeliveryPartner partnerAfter = deliveryPartnerRepository.findByUserId(partnerUser.getId()).orElseThrow();
        assertThat(partnerAfter.getStatus()).isEqualTo(DeliveryPartnerStatus.AVAILABLE);

        TestAuth.loginAs(customerUser);
        var rating = ratingService.rate(placed.id(), new RatingRequest(5, "Great!"));
        assertThat(rating.score()).isEqualTo(5);

        assertThatThrownBy(() -> ratingService.rate(placed.id(), new RatingRequest(4, "again")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void cancelledOrderReleasesReservedStock() {
        TestAuth.loginAs(customerUser);
        OrderResponse placed = orderService.placeOrder(new PlaceOrderRequest(
                restaurant.getId(), List.of(new OrderItemRequest(menuItem.getId(), 3))));
        assertThat(menuItemRepository.findById(menuItem.getId()).orElseThrow().getStockQuantity()).isEqualTo(2);

        orderService.updateStatus(placed.id(), OrderStatus.CANCELLED);
        assertThat(menuItemRepository.findById(menuItem.getId()).orElseThrow().getStockQuantity()).isEqualTo(5);
    }

    @Test
    void customerCannotSkipStatusesOrActAsRestaurant() {
        TestAuth.loginAs(customerUser);
        OrderResponse placed = orderService.placeOrder(new PlaceOrderRequest(
                restaurant.getId(), List.of(new OrderItemRequest(menuItem.getId(), 1))));

        assertThatThrownBy(() -> orderService.updateStatus(placed.id(), OrderStatus.ACCEPTED))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void orderPlacementFailsCleanlyWhenStockInsufficient() {
        TestAuth.loginAs(customerUser);
        assertThatThrownBy(() -> orderService.placeOrder(new PlaceOrderRequest(
                restaurant.getId(), List.of(new OrderItemRequest(menuItem.getId(), 999)))))
                .isInstanceOf(ConflictException.class);

        // Stock must be untouched after the rolled-back attempt.
        assertThat(menuItemRepository.findById(menuItem.getId()).orElseThrow().getStockQuantity()).isEqualTo(5);
    }
}
