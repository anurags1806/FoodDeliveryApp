package com.dmg.fooddelivery.integration;

import com.dmg.fooddelivery.model.*;
import com.dmg.fooddelivery.repository.*;
import com.dmg.fooddelivery.service.DeliveryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that when many delivery partners race to accept the same
 * PREPARING order, exactly one of them wins and the rest are rejected
 * with a conflict - never a double assignment.
 */
@SpringBootTest
@ActiveProfiles("test")
class DeliveryAssignmentConcurrencyIntegrationTest {

    @Autowired private DeliveryService deliveryService;
    @Autowired private UserRepository userRepository;
    @Autowired private CityRepository cityRepository;
    @Autowired private RestaurantRepository restaurantRepository;
    @Autowired private MenuItemRepository menuItemRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private DeliveryPartnerRepository deliveryPartnerRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final int CONCURRENT_PARTNERS = 20;

    private Order order;
    private final List<DeliveryPartner> partners = new ArrayList<>();

    @BeforeEach
    void setUp() {
        City city = cityRepository.save(City.builder().name("DeliveryCity-" + System.nanoTime()).build());
        User owner = userRepository.save(User.builder()
                .name("Owner").email("owner-" + System.nanoTime() + "@test.com")
                .passwordHash(passwordEncoder.encode("password")).role(Role.RESTAURANT_OWNER).build());
        Restaurant restaurant = restaurantRepository.save(Restaurant.builder()
                .name("Test Restaurant").city(city).owner(owner).address("123 St").build());
        MenuItem menuItem = menuItemRepository.save(MenuItem.builder()
                .restaurant(restaurant).name("Burger").price(new BigDecimal("5.00"))
                .stockQuantity(100).available(true).build());
        User customer = userRepository.save(User.builder()
                .name("Cust").email("cust-" + System.nanoTime() + "@test.com")
                .passwordHash(passwordEncoder.encode("password")).role(Role.CUSTOMER).build());

        Order o = Order.builder()
                .customer(customer).restaurant(restaurant)
                .status(OrderStatus.PREPARING)
                .totalAmount(new BigDecimal("5.00"))
                .paymentStatus(PaymentStatus.PAID)
                .build();
        OrderItem item = OrderItem.builder().menuItem(menuItem).quantity(1).priceAtOrder(new BigDecimal("5.00")).build();
        o.addItem(item);
        order = orderRepository.save(o);

        for (int i = 0; i < CONCURRENT_PARTNERS; i++) {
            User partnerUser = userRepository.save(User.builder()
                    .name("Partner" + i).email("partner" + i + "-" + System.nanoTime() + "@test.com")
                    .passwordHash(passwordEncoder.encode("password")).role(Role.DELIVERY_PARTNER).build());
            partners.add(deliveryPartnerRepository.save(DeliveryPartner.builder()
                    .user(partnerUser).city(city).status(DeliveryPartnerStatus.AVAILABLE).build()));
        }
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    @Test
    void exactlyOnePartnerWinsTheOrder() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_PARTNERS);
        CountDownLatch startLine = new CountDownLatch(1);
        CountDownLatch finishLine = new CountDownLatch(CONCURRENT_PARTNERS);
        AtomicInteger succeeded = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        for (DeliveryPartner partner : partners) {
            pool.submit(() -> {
                try {
                    startLine.await();
                    TestAuth.loginAs(partner.getUser());
                    deliveryService.acceptOrder(order.getId());
                    succeeded.incrementAndGet();
                } catch (Exception e) {
                    failed.incrementAndGet();
                } finally {
                    TestAuth.clear();
                    finishLine.countDown();
                }
            });
        }

        startLine.countDown();
        boolean completed = finishLine.await(60, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(completed).isTrue();
        assertThat(succeeded.get()).isEqualTo(1);
        assertThat(failed.get()).isEqualTo(CONCURRENT_PARTNERS - 1);

        Order finalOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(finalOrder.getDeliveryPartner()).isNotNull();

        long busyCount = partners.stream()
                .map(p -> deliveryPartnerRepository.findById(p.getId()).orElseThrow())
                .filter(p -> p.getStatus() == DeliveryPartnerStatus.BUSY)
                .count();
        assertThat(busyCount).isEqualTo(1);
    }
}
