package com.dmg.fooddelivery.service;

import static java.util.Objects.requireNonNull;

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
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Validated
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryPartnerRepository deliveryPartnerRepository;
    private final OrderRepository orderRepository;
    private final CityService cityService;
    private final OrderService orderService;

    @Transactional
    public DeliveryPartnerResponse register(@NotNull @Valid RegisterDeliveryPartnerRequest request) {
        User caller = SecurityUtils.currentUser();
        if (caller.getRole() != Role.DELIVERY_PARTNER) {
            throw new ForbiddenException("Only delivery-partner accounts can register as a partner");
        }
        if (deliveryPartnerRepository.findByUserId(SecurityUtils.currentUserId()).isPresent()) {
            throw new BadRequestException("Delivery partner profile already exists for this user");
        }
        City city = cityService.getOrThrow(requireNonNull(request.cityId()));
        DeliveryPartner partner = DeliveryPartner.builder()
                .user(caller)
                .city(city)
                .status(DeliveryPartnerStatus.AVAILABLE)
                .build();
        partner = deliveryPartnerRepository.save(partner);
        return toResponse(partner);
    }

    /** Orders in PREPARING state, unassigned, in the partner's city - the pool partners contend over. */
    @Transactional(readOnly = true)
    public List<OrderResponse> listAssignableOrders() {
        DeliveryPartner partner = getCurrentPartnerOrThrow();
        return requireNonNull(orderRepository.findAssignableOrdersByCity(requireNonNull(partner.getCity().getId()))
                .stream().map(orderService::toResponse).toList());
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
    public OrderResponse acceptOrder(@NotNull Long orderId) {
        Order order = requireNonNull(orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId)));

        DeliveryPartner lockedPartner = requireNonNull(deliveryPartnerRepository.findByUserIdForUpdate(SecurityUtils.currentUserId())
                .orElseThrow(() -> new NotFoundException("Delivery partner not found")));
        if (lockedPartner.getStatus() != DeliveryPartnerStatus.AVAILABLE) {
            throw new ConflictException("You are not available to accept new orders (current status: "
                    + lockedPartner.getStatus() + ")");
        }

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
        return requireNonNull(deliveryPartnerRepository.findByUserId(SecurityUtils.currentUserId())
                .orElseThrow(() -> new NotFoundException("No delivery partner profile for current user")));
    }

    private DeliveryPartnerResponse toResponse(DeliveryPartner p) {
        return new DeliveryPartnerResponse(p.getId(), p.getUser().getId(), p.getUser().getName(),
                p.getCity().getId(), p.getStatus());
    }
}
