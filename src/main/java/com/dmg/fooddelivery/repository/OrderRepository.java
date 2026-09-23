package com.dmg.fooddelivery.repository;

import com.dmg.fooddelivery.model.Order;
import com.dmg.fooddelivery.model.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByCustomerId(Long customerId);

    List<Order> findByRestaurantId(Long restaurantId);

    List<Order> findByDeliveryPartnerId(Long deliveryPartnerId);

    List<Order> findByRestaurantIdAndStatusIn(Long restaurantId, List<OrderStatus> statuses);

    /**
     * Orders ready for a delivery partner to pick up in a given city:
     * PREPARING and not yet assigned to any partner. Used as the
     * "available assignments" pool that multiple partners contend over.
     */
    @Query("select o from Order o where o.status = com.dmg.fooddelivery.model.OrderStatus.PREPARING " +
           "and o.deliveryPartner is null and o.restaurant.city.id = :cityId")
    List<Order> findAssignableOrdersByCity(@Param("cityId") Long cityId);

    /**
     * Pessimistic write lock used both when a delivery partner accepts an
     * order (prevents two partners from both winning the same order) and
     * when placing/updating an order atomically alongside stock and
     * payment.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);
}
