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

import static java.util.Objects.requireNonNull;
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
    private long restaurantId;
    private long menuItemId;
    private long customerId;
    private long partnerUserId;

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

        restaurantId = requireNonNull(restaurant.getId(), "Saved restaurant must have an ID");
        menuItemId = requireNonNull(menuItem.getId(), "Saved menu item must have an ID");
        customerId = requireNonNull(customerUser.getId(), "Saved customer must have an ID");
        partnerUserId = requireNonNull(partnerUser.getId(), "Saved partner user must have an ID");
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    @Test
    void cancellingAssignedOrderFreesPartnerAndRestoresStock() {
        TestAuth.loginAs(customerUser);
        var placed = orderService.placeOrder(new PlaceOrderRequest(
                restaurantId, List.of(new OrderItemRequest(menuItemId, 2))));
        long orderId = requireNonNull(placed.id(), "Placed order must have an ID");
        TestAuth.loginAs(ownerUser);
        orderService.updateStatus(orderId, OrderStatus.ACCEPTED);
        orderService.updateStatus(orderId, OrderStatus.PREPARING);
        TestAuth.loginAs(partnerUser);
        deliveryService.acceptOrder(orderId);
        TestAuth.loginAs(customerUser);
        orderService.updateStatus(orderId, OrderStatus.CANCELLED);
        assertThat(deliveryPartnerRepository.findByUserId(partnerUserId).orElseThrow().getStatus())
                .isEqualTo(DeliveryPartnerStatus.AVAILABLE);
        assertThat(menuItemRepository.findById(menuItemId).orElseThrow().getStockQuantity()).isEqualTo(5);
    }

    @Test
    void orderListsCanBeReadOutsideAWebRequest() {
        TestAuth.loginAs(customerUser);
        var placed = orderService.placeOrder(new PlaceOrderRequest(
                restaurantId, List.of(new OrderItemRequest(menuItemId, 1))));
        long orderId = requireNonNull(placed.id(), "Placed order must have an ID");
        assertThat(orderService.listForCustomer(customerId)).extracting(order -> order.id())
                .containsExactly(orderId);
        assertThat(orderService.listForRestaurant(restaurantId)).hasSize(1);
        TestAuth.loginAs(ownerUser);
        orderService.updateStatus(orderId, OrderStatus.ACCEPTED);
        orderService.updateStatus(orderId, OrderStatus.PREPARING);
        TestAuth.loginAs(partnerUser);
        assertThat(deliveryService.listAssignableOrders()).extracting(order -> order.id()).contains(orderId);
    }

    @Test
    void fullHappyPathFromPlacementToRating() {
        TestAuth.loginAs(customerUser);
        OrderResponse placed = orderService.placeOrder(new PlaceOrderRequest(
                restaurantId, List.of(new OrderItemRequest(menuItemId, 2))));
        long orderId = requireNonNull(placed.id(), "Placed order must have an ID");
        assertThat(placed.status()).isEqualTo(OrderStatus.PLACED);
        assertThat(placed.paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(placed.totalAmount()).isEqualByComparingTo("24.00");

        MenuItem afterOrder = menuItemRepository.findById(menuItemId).orElseThrow();
        assertThat(afterOrder.getStockQuantity()).isEqualTo(3);

        TestAuth.loginAs(ownerUser);
        orderService.updateStatus(orderId, OrderStatus.ACCEPTED);
        OrderResponse preparing = orderService.updateStatus(orderId, OrderStatus.PREPARING);
        assertThat(preparing.status()).isEqualTo(OrderStatus.PREPARING);

        TestAuth.loginAs(partnerUser);
        OrderResponse assigned = deliveryService.acceptOrder(orderId);
        assertThat(assigned.deliveryPartnerId()).isNotNull();

        orderService.updateStatus(orderId, OrderStatus.OUT_FOR_DELIVERY);
        OrderResponse delivered = orderService.updateStatus(orderId, OrderStatus.DELIVERED);
        assertThat(delivered.status()).isEqualTo(OrderStatus.DELIVERED);

        DeliveryPartner partnerAfter = deliveryPartnerRepository.findByUserId(partnerUserId).orElseThrow();
        assertThat(partnerAfter.getStatus()).isEqualTo(DeliveryPartnerStatus.AVAILABLE);

        TestAuth.loginAs(customerUser);
        var rating = ratingService.rate(orderId, new RatingRequest(5, "Great!"));
        assertThat(rating.score()).isEqualTo(5);

        assertThatThrownBy(() -> ratingService.rate(orderId, new RatingRequest(4, "again")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void cancelledOrderReleasesReservedStock() {
        TestAuth.loginAs(customerUser);
        OrderResponse placed = orderService.placeOrder(new PlaceOrderRequest(
                restaurantId, List.of(new OrderItemRequest(menuItemId, 3))));
        long orderId = requireNonNull(placed.id(), "Placed order must have an ID");
        assertThat(menuItemRepository.findById(menuItemId).orElseThrow().getStockQuantity()).isEqualTo(2);

        orderService.updateStatus(orderId, OrderStatus.CANCELLED);
        assertThat(menuItemRepository.findById(menuItemId).orElseThrow().getStockQuantity()).isEqualTo(5);
    }

    @Test
    void customerCannotSkipStatusesOrActAsRestaurant() {
        TestAuth.loginAs(customerUser);
        OrderResponse placed = orderService.placeOrder(new PlaceOrderRequest(
                restaurantId, List.of(new OrderItemRequest(menuItemId, 1))));
        long orderId = requireNonNull(placed.id(), "Placed order must have an ID");

        assertThatThrownBy(() -> orderService.updateStatus(orderId, OrderStatus.ACCEPTED))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void orderPlacementFailsCleanlyWhenStockInsufficient() {
        TestAuth.loginAs(customerUser);
        assertThatThrownBy(() -> orderService.placeOrder(new PlaceOrderRequest(
                restaurantId, List.of(new OrderItemRequest(menuItemId, 999)))))
                .isInstanceOf(ConflictException.class);

        // Stock must be untouched after the rolled-back attempt.
        assertThat(menuItemRepository.findById(menuItemId).orElseThrow().getStockQuantity()).isEqualTo(5);
    }
}
