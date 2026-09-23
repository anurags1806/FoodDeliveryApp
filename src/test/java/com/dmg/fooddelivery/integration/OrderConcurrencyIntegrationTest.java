package com.dmg.fooddelivery.integration;

import com.dmg.fooddelivery.dto.request.OrderItemRequest;
import com.dmg.fooddelivery.dto.request.PlaceOrderRequest;
import com.dmg.fooddelivery.model.*;
import com.dmg.fooddelivery.repository.*;
import com.dmg.fooddelivery.service.OrderService;
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
 * Proves the core concurrency guarantee: N customers racing to buy the
 * last few units of the same menu item can never collectively oversell
 * it, and the item's final stock count is always exactly
 * (initialStock - unitsActuallySold), with no lost updates.
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderConcurrencyIntegrationTest {

    @Autowired private OrderService orderService;
    @Autowired private UserRepository userRepository;
    @Autowired private CityRepository cityRepository;
    @Autowired private RestaurantRepository restaurantRepository;
    @Autowired private MenuItemRepository menuItemRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Restaurant restaurant;
    private MenuItem menuItem;
    private final List<User> customers = new ArrayList<>();

    private static final int STOCK = 10;
    private static final int CONCURRENT_CUSTOMERS = 30; // each tries to buy 1 unit

    @BeforeEach
    void setUp() {
        City city = cityRepository.save(City.builder().name("ConcurrencyCity-" + System.nanoTime()).build());
        User owner = userRepository.save(User.builder()
                .name("Owner").email("owner-" + System.nanoTime() + "@test.com")
                .passwordHash(passwordEncoder.encode("password")).role(Role.RESTAURANT_OWNER).build());
        restaurant = restaurantRepository.save(Restaurant.builder()
                .name("Test Restaurant").city(city).owner(owner).address("123 St").build());
        menuItem = menuItemRepository.save(MenuItem.builder()
                .restaurant(restaurant).name("Limited Burger")
                .price(new BigDecimal("9.99")).stockQuantity(STOCK).available(true).build());

        for (int i = 0; i < CONCURRENT_CUSTOMERS; i++) {
            customers.add(userRepository.save(User.builder()
                    .name("Customer" + i).email("customer" + i + "-" + System.nanoTime() + "@test.com")
                    .passwordHash(passwordEncoder.encode("password")).role(Role.CUSTOMER).build()));
        }
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    @Test
    void concurrentOrdersNeverOversellStock() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_CUSTOMERS);
        CountDownLatch startLine = new CountDownLatch(1);
        CountDownLatch finishLine = new CountDownLatch(CONCURRENT_CUSTOMERS);
        AtomicInteger succeeded = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);

        for (User customer : customers) {
            pool.submit(() -> {
                try {
                    startLine.await();
                    TestAuth.loginAs(customer);
                    PlaceOrderRequest request = new PlaceOrderRequest(
                            restaurant.getId(), List.of(new OrderItemRequest(menuItem.getId(), 1)));
                    orderService.placeOrder(request);
                    succeeded.incrementAndGet();
                } catch (Exception e) {
                    failed.incrementAndGet();
                } finally {
                    TestAuth.clear();
                    finishLine.countDown();
                }
            });
        }

        startLine.countDown(); // release all threads at once
        boolean completed = finishLine.await(60, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(completed).as("all threads finished within timeout").isTrue();

        // Exactly STOCK orders should succeed (one unit each); the rest
        // must fail with "insufficient stock" - never oversold.
        assertThat(succeeded.get()).isEqualTo(STOCK);
        assertThat(failed.get()).isEqualTo(CONCURRENT_CUSTOMERS - STOCK);

        MenuItem finalItem = menuItemRepository.findById(menuItem.getId()).orElseThrow();
        assertThat(finalItem.getStockQuantity()).isEqualTo(0);
    }
}
