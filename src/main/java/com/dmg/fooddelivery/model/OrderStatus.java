package com.dmg.fooddelivery.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Order lifecycle: PLACED -> ACCEPTED -> PREPARING -> OUT_FOR_DELIVERY -> DELIVERED
 * With side branches: PLACED -> REJECTED, and PLACED/ACCEPTED -> CANCELLED.
 */
public enum OrderStatus {
    PLACED,
    ACCEPTED,
    REJECTED,
    PREPARING,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED;

    /**
     * Defines the only legal forward transitions. Used to reject invalid
     * status updates (e.g. jumping from PLACED straight to DELIVERED).
     */
    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case PLACED -> EnumSet.of(ACCEPTED, REJECTED, CANCELLED).contains(next);
            case ACCEPTED -> EnumSet.of(PREPARING, CANCELLED).contains(next);
            case PREPARING -> EnumSet.of(OUT_FOR_DELIVERY, CANCELLED).contains(next);
            case OUT_FOR_DELIVERY -> EnumSet.of(DELIVERED).contains(next);
            case DELIVERED, REJECTED, CANCELLED -> Set.of().contains(next);
        };
    }

    public boolean isTerminal() {
        return this == DELIVERED || this == REJECTED || this == CANCELLED;
    }
}
