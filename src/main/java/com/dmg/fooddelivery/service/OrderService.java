package com.dmg.fooddelivery.service;

import com.dmg.fooddelivery.dto.request.OrderItemRequest;
import com.dmg.fooddelivery.dto.request.PlaceOrderRequest;
import com.dmg.fooddelivery.dto.response.OrderItemResponse;
import com.dmg.fooddelivery.dto.response.OrderResponse;
import com.dmg.fooddelivery.exception.BadRequestException;
import com.dmg.fooddelivery.exception.ConflictException;
import com.dmg.fooddelivery.exception.ForbiddenException;
import com.dmg.fooddelivery.exception.NotFoundException;
import com.dmg.fooddelivery.model.*;
import com.dmg.fooddelivery.repository.DeliveryPartnerRepository;
import com.dmg.fooddelivery.repository.MenuItemRepository;
import com.dmg.fooddelivery.repository.OrderRepository;
import com.dmg.fooddelivery.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final MenuItemRepository menuItemRepository;
    private final DeliveryPartnerRepository deliveryPartnerRepository;
    private final RestaurantService restaurantService;
    private final PaymentService paymentService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Places an order atomically:
     *  1. For each line item, pessimistically locks the MenuItem row
     *     (findByIdForUpdate) and checks/decrements stock in-place. Locking
     *     items in ascending id order avoids deadlocks between two orders
     *     that both reference the same two items in different order.
     *  2. Computes the total and charges payment synchronously.
     *  3. Persists the Order + OrderItems + payment status together.
     * If stock is insufficient or payment fails, the whole transaction
     * rolls back - no partial stock decrement, no orphaned order.
     */
    @Transactional
    public OrderResponse placeOrder(PlaceOrderRequest request) {
        User customer = SecurityUtils.currentUser();
        Restaurant restaurant = restaurantService.getEntityOrThrow(request.restaurantId());

        List<OrderItemRequest> sortedItems = request.items().stream()
                .sorted(Comparator.comparing(OrderItemRequest::menuItemId))
                .toList();

        Order order = Order.builder()
                .customer(customer)
                .restaurant(restaurant)
                .status(OrderStatus.PLACED)
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemRequest itemReq : sortedItems) {
            MenuItem menuItem = menuItemRepository.findByIdForUpdate(itemReq.menuItemId())
                    .orElseThrow(() -> new NotFoundException("Menu item not found: " + itemReq.menuItemId()));

            if (!menuItem.getRestaurant().getId().equals(restaurant.getId())) {
                throw new BadRequestException("Menu item " + menuItem.getId() + " does not belong to restaurant " + restaurant.getId());
            }
            if (!menuItem.isAvailable()) {
                throw new ConflictException("Menu item is not available: " + menuItem.getName());
            }
            if (menuItem.getStockQuantity() < itemReq.quantity()) {
                throw new ConflictException("Insufficient stock for " + menuItem.getName()
                        + " (requested " + itemReq.quantity() + ", available " + menuItem.getStockQuantity() + ")");
            }

            menuItem.setStockQuantity(menuItem.getStockQuantity() - itemReq.quantity());
            menuItemRepository.save(menuItem);

            BigDecimal lineTotal = menuItem.getPrice().multiply(BigDecimal.valueOf(itemReq.quantity()));
            total = total.add(lineTotal);

            OrderItem orderItem = OrderItem.builder()
                    .menuItem(menuItem)
                    .quantity(itemReq.quantity())
                    .priceAtOrder(menuItem.getPrice())
                    .build();
            order.addItem(orderItem);
        }

        order.setTotalAmount(total);

        // Payment happens inside the same DB transaction as the stock
        // decrement/order insert: if it fails, everything rolls back
        // together (order state, stock, and payment stay consistent).
        PaymentStatus paymentStatus = paymentService.charge(customer, total);
        if (paymentStatus != PaymentStatus.PAID) {
            throw new ConflictException("Payment failed for order; please retry");
        }
        order.setPaymentStatus(paymentStatus);

        order = orderRepository.save(order);
        return toResponse(order);
    }

    @Transactional
    public OrderResponse updateStatus(Long orderId, OrderStatus newStatus) {
        // Lock the order row so a concurrent status update (or delivery
        // partner assignment) can't race this one.
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));

        User caller = SecurityUtils.currentUser();
        authorizeStatusChange(order, caller, newStatus);

        if (!order.getStatus().canTransitionTo(newStatus)) {
            throw new ConflictException("Cannot transition order from " + order.getStatus() + " to " + newStatus);
        }

        order.setStatus(newStatus);
        order.setUpdatedAt(Instant.now());

        // If moving to PREPARING, restock is not applicable; if
        // CANCELLED/REJECTED before delivery, release the reserved stock
        // back to the menu items.
        if (newStatus == OrderStatus.CANCELLED || newStatus == OrderStatus.REJECTED) {
            releaseStock(order);
        }
        if (newStatus == OrderStatus.DELIVERED && order.getDeliveryPartner() != null) {
            releaseDeliveryPartner(order.getDeliveryPartner().getId());
        }

        order = orderRepository.save(order);
        eventPublisher.publishEvent(new NotificationService.OrderStatusChangedEvent(order, newStatus));
        return toResponse(order);
    }

    private void releaseStock(Order order) {
        for (OrderItem item : order.getItems()) {
            MenuItem menuItem = menuItemRepository.findByIdForUpdate(item.getMenuItem().getId())
                    .orElseThrow(() -> new NotFoundException("Menu item not found: " + item.getMenuItem().getId()));
            menuItem.setStockQuantity(menuItem.getStockQuantity() + item.getQuantity());
            menuItemRepository.save(menuItem);
        }
    }

    /** Flips the delivery partner back to AVAILABLE once their delivery is complete. */
    private void releaseDeliveryPartner(Long deliveryPartnerId) {
        deliveryPartnerRepository.findByIdForUpdate(deliveryPartnerId).ifPresent(p -> {
            p.setStatus(DeliveryPartnerStatus.AVAILABLE);
            deliveryPartnerRepository.save(p);
        });
    }

    private void authorizeStatusChange(Order order, User caller, OrderStatus newStatus) {
        switch (caller.getRole()) {
            case ADMIN -> { /* admin can do anything */ }
            case RESTAURANT_OWNER -> {
                restaurantService.assertOwnershipOrAdmin(order.getRestaurant(), caller);
                if (newStatus != OrderStatus.ACCEPTED && newStatus != OrderStatus.REJECTED
                        && newStatus != OrderStatus.PREPARING) {
                    throw new ForbiddenException("Restaurant owner cannot set status to " + newStatus);
                }
            }
            case DELIVERY_PARTNER -> {
                if (order.getDeliveryPartner() == null || !order.getDeliveryPartner().getUser().getId().equals(caller.getId())) {
                    throw new ForbiddenException("You are not the assigned delivery partner for this order");
                }
                if (newStatus != OrderStatus.OUT_FOR_DELIVERY && newStatus != OrderStatus.DELIVERED) {
                    throw new ForbiddenException("Delivery partner cannot set status to " + newStatus);
                }
            }
            case CUSTOMER -> {
                if (!order.getCustomer().getId().equals(caller.getId())) {
                    throw new ForbiddenException("Not your order");
                }
                if (newStatus != OrderStatus.CANCELLED) {
                    throw new ForbiddenException("Customer can only cancel an order");
                }
            }
        }
    }

    public OrderResponse getById(Long id) {
        return toResponse(getEntityOrThrow(id));
    }

    public Order getEntityOrThrow(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Order not found: " + id));
    }

    public List<OrderResponse> listForCustomer(Long customerId) {
        return orderRepository.findByCustomerId(customerId).stream().map(this::toResponse).toList();
    }

    public List<OrderResponse> listForRestaurant(Long restaurantId) {
        return orderRepository.findByRestaurantId(restaurantId).stream().map(this::toResponse).toList();
    }

    public List<OrderResponse> listForDeliveryPartner(Long deliveryPartnerId) {
        return orderRepository.findByDeliveryPartnerId(deliveryPartnerId).stream().map(this::toResponse).toList();
    }

    public OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(i -> new OrderItemResponse(i.getMenuItem().getId(), i.getMenuItem().getName(),
                        i.getQuantity(), i.getPriceAtOrder()))
                .toList();
        return new OrderResponse(
                order.getId(), order.getCustomer().getId(), order.getRestaurant().getId(),
                order.getRestaurant().getName(), items, order.getStatus(), order.getPaymentStatus(),
                order.getTotalAmount(),
                order.getDeliveryPartner() != null ? order.getDeliveryPartner().getId() : null,
                order.getCreatedAt(), order.getUpdatedAt());
    }
}
