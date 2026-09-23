package com.dmg.fooddelivery.service;

import com.dmg.fooddelivery.dto.request.RegisterDeliveryPartnerRequest;
import com.dmg.fooddelivery.dto.response.DeliveryPartnerResponse;
import com.dmg.fooddelivery.dto.response.OrderResponse;
import com.dmg.fooddelivery.exception.BadRequestException;
import com.dmg.fooddelivery.exception.ConflictException;
import com.dmg.fooddelivery.exception.ForbiddenException;
import com.dmg.fooddelivery.exception.NotFoundException;
import com.dmg.fooddelivery.model.*;
import com.dmg.fooddelivery.repository.DeliveryPartnerRepository;
import com.dmg.fooddelivery.repository.OrderRepository;
import com.dmg.fooddelivery.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryPartnerRepository deliveryPartnerRepository;
    private final OrderRepository orderRepository;
    private final CityService cityService;
    private final OrderService orderService;

    @Transactional
    public DeliveryPartnerResponse register(RegisterDeliveryPartnerRequest request) {
        User caller = SecurityUtils.currentUser();
        if (caller.getRole() != Role.DELIVERY_PARTNER) {
            throw new ForbiddenException("Only delivery-partner accounts can register as a partner");
        }
        if (deliveryPartnerRepository.findByUserId(caller.getId()).isPresent()) {
            throw new BadRequestException("Delivery partner profile already exists for this user");
        }
        City city = cityService.getOrThrow(request.cityId());
        DeliveryPartner partner = DeliveryPartner.builder()
                .user(caller)
                .city(city)
                .status(DeliveryPartnerStatus.AVAILABLE)
                .build();
        partner = deliveryPartnerRepository.save(partner);
        return toResponse(partner);
    }

    /** Orders in PREPARING state, unassigned, in the partner's city - the pool partners contend over. */
    public List<OrderResponse> listAssignableOrders() {
        DeliveryPartner partner = getCurrentPartnerOrThrow();
        return orderRepository.findAssignableOrdersByCity(partner.getCity().getId())
                .stream().map(orderService::toResponse).toList();
    }

    /**
     * Accepting an order is the contended operation: several delivery
     * partners may call this for the same order at nearly the same time.
     * We take a pessimistic write lock on the Order row first, so only
     * one transaction can observe deliveryPartner == null and win; every
     * other concurrent caller blocks until the winner commits, then sees
     * deliveryPartner already set and gets a 409 Conflict instead of
     * silently double-assigning the order.
     *
     * We also pessimistically lock the DeliveryPartner row and flip it to
     * BUSY, so the same partner can't simultaneously "win" two different
     * orders from two racing requests either.
     */
    @Transactional
    public OrderResponse acceptOrder(Long orderId) {
        DeliveryPartner partner = getCurrentPartnerOrThrow();

        DeliveryPartner lockedPartner = deliveryPartnerRepository.findByIdForUpdate(partner.getId())
                .orElseThrow(() -> new NotFoundException("Delivery partner not found"));
        if (lockedPartner.getStatus() != DeliveryPartnerStatus.AVAILABLE) {
            throw new ConflictException("You are not available to accept new orders (current status: "
                    + lockedPartner.getStatus() + ")");
        }

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));

        if (order.getDeliveryPartner() != null) {
            throw new ConflictException("Order has already been claimed by another delivery partner");
        }
        if (order.getStatus() != OrderStatus.PREPARING) {
            throw new ConflictException("Order is not ready for pickup (status: " + order.getStatus() + ")");
        }
        if (!order.getRestaurant().getCity().getId().equals(lockedPartner.getCity().getId())) {
            throw new BadRequestException("Order is not in your city");
        }

        order.setDeliveryPartner(lockedPartner);
        lockedPartner.setStatus(DeliveryPartnerStatus.BUSY);

        deliveryPartnerRepository.save(lockedPartner);
        order = orderRepository.save(order);
        return orderService.toResponse(order);
    }

    private DeliveryPartner getCurrentPartnerOrThrow() {
        User caller = SecurityUtils.currentUser();
        return deliveryPartnerRepository.findByUserId(caller.getId())
                .orElseThrow(() -> new NotFoundException("No delivery partner profile for current user"));
    }

    private DeliveryPartnerResponse toResponse(DeliveryPartner p) {
        return new DeliveryPartnerResponse(p.getId(), p.getUser().getId(), p.getUser().getName(),
                p.getCity().getId(), p.getStatus());
    }
}
