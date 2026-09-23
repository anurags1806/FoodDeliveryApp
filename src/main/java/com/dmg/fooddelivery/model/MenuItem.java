package com.dmg.fooddelivery.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "menu_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "restaurant_id")
    private Restaurant restaurant;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal price;

    /**
     * Remaining sellable units. Decremented atomically inside the order
     * placement transaction using a pessimistic row lock (see
     * MenuItemRepository#findByIdForUpdate) so concurrent orders for the
     * same item can never oversell stock.
     */
    @Column(nullable = false)
    private Integer stockQuantity;

    @Builder.Default
    private boolean available = true;

    /**
     * Optimistic lock for ordinary field edits (price/availability updates
     * from the restaurant owner) that don't go through the pessimistic
     * stock-decrement path.
     */
    @Version
    private Long version;
}
