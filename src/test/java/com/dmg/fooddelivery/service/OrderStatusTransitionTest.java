package com.dmg.fooddelivery.service;

import com.dmg.fooddelivery.model.OrderStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTransitionTest {

    @Test
    void placedCanMoveToAcceptedRejectedOrCancelled() {
        assertThat(OrderStatus.PLACED.canTransitionTo(OrderStatus.ACCEPTED)).isTrue();
        assertThat(OrderStatus.PLACED.canTransitionTo(OrderStatus.REJECTED)).isTrue();
        assertThat(OrderStatus.PLACED.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
    }

    @Test
    void placedCannotSkipStraightToDelivered() {
        assertThat(OrderStatus.PLACED.canTransitionTo(OrderStatus.DELIVERED)).isFalse();
        assertThat(OrderStatus.PLACED.canTransitionTo(OrderStatus.OUT_FOR_DELIVERY)).isFalse();
        assertThat(OrderStatus.PLACED.canTransitionTo(OrderStatus.PREPARING)).isFalse();
    }

    @Test
    void fullHappyPathIsSequential() {
        assertThat(OrderStatus.ACCEPTED.canTransitionTo(OrderStatus.PREPARING)).isTrue();
        assertThat(OrderStatus.PREPARING.canTransitionTo(OrderStatus.OUT_FOR_DELIVERY)).isTrue();
        assertThat(OrderStatus.OUT_FOR_DELIVERY.canTransitionTo(OrderStatus.DELIVERED)).isTrue();
    }

    @Test
    void terminalStatesHaveNoOutgoingTransitions() {
        for (OrderStatus terminal : new OrderStatus[]{OrderStatus.DELIVERED, OrderStatus.REJECTED, OrderStatus.CANCELLED}) {
            assertThat(terminal.isTerminal()).isTrue();
            for (OrderStatus next : OrderStatus.values()) {
                assertThat(terminal.canTransitionTo(next)).isFalse();
            }
        }
    }

    @Test
    void cannotGoBackwards() {
        assertThat(OrderStatus.PREPARING.canTransitionTo(OrderStatus.ACCEPTED)).isFalse();
        assertThat(OrderStatus.OUT_FOR_DELIVERY.canTransitionTo(OrderStatus.PREPARING)).isFalse();
    }
}
