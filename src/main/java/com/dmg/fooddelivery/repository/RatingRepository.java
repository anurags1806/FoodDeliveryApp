package com.dmg.fooddelivery.repository;

import com.dmg.fooddelivery.model.Rating;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RatingRepository extends JpaRepository<Rating, Long> {
    List<Rating> findByRestaurantId(Long restaurantId);
    Optional<Rating> findByOrderId(Long orderId);
    boolean existsByOrderId(Long orderId);
}
