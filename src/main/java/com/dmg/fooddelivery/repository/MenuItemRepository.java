package com.dmg.fooddelivery.repository;

import com.dmg.fooddelivery.model.MenuItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    List<MenuItem> findByRestaurantIdAndAvailableTrue(Long restaurantId);

    List<MenuItem> findByRestaurantId(Long restaurantId);

    /**
     * Pessimistic write lock: blocks any other transaction from reading
     * this row FOR UPDATE until this transaction commits/rolls back. This
     * is what makes concurrent order placement for the same menu item
     * safe from overselling - two transactions racing to decrement the
     * same item's stock are serialized here instead of both reading the
     * same stale stockQuantity.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MenuItem m where m.id = :id")
    Optional<MenuItem> findByIdForUpdate(@Param("id") Long id);
}
