package com.dmg.fooddelivery.repository;

import com.dmg.fooddelivery.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
}
