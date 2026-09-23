package com.dmg.fooddelivery.service;

import com.dmg.fooddelivery.model.Order;
import com.dmg.fooddelivery.model.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Fans a status change out to the customer, restaurant, and (once assigned)
 * delivery partner. Each notification runs on the "notificationExecutor"
 * pool (see AsyncConfig) so the caller of updateStatus() gets its HTTP
 * response back immediately instead of waiting on notification delivery.
 *
 * This is a stub: in production each branch would call an email/SMS/push
 * provider or publish to a message queue. Listening on
 * AFTER_COMMIT ensures we never notify about a status change that was
 * later rolled back.
 */
@Service
@Slf4j
public class NotificationService {

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        Order order = event.order();
        OrderStatus status = event.newStatus();

        notifyCustomer(order, status);
        notifyRestaurant(order, status);
        if (order.getDeliveryPartner() != null) {
            notifyDeliveryPartner(order, status);
        }
    }

    private void notifyCustomer(Order order, OrderStatus status) {
        log.info("[notify:customer={}] order={} status={}", order.getCustomer().getId(), order.getId(), status);
    }

    private void notifyRestaurant(Order order, OrderStatus status) {
        log.info("[notify:restaurant={}] order={} status={}", order.getRestaurant().getId(), order.getId(), status);
    }

    private void notifyDeliveryPartner(Order order, OrderStatus status) {
        log.info("[notify:partner={}] order={} status={}", order.getDeliveryPartner().getId(), order.getId(), status);
    }

    public record OrderStatusChangedEvent(Order order, OrderStatus newStatus) {}
}
