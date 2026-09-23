package com.dmg.fooddelivery.service;

import com.dmg.fooddelivery.dto.request.RatingRequest;
import com.dmg.fooddelivery.dto.response.RatingResponse;
import com.dmg.fooddelivery.exception.BadRequestException;
import com.dmg.fooddelivery.exception.ConflictException;
import com.dmg.fooddelivery.exception.ForbiddenException;
import com.dmg.fooddelivery.model.Order;
import com.dmg.fooddelivery.model.OrderStatus;
import com.dmg.fooddelivery.model.Rating;
import com.dmg.fooddelivery.model.User;
import com.dmg.fooddelivery.repository.RatingRepository;
import com.dmg.fooddelivery.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RatingService {

    private final RatingRepository ratingRepository;
    private final OrderService orderService;

    @Transactional
    public RatingResponse rate(Long orderId, RatingRequest request) {
        Order order = orderService.getEntityOrThrow(orderId);
        User caller = SecurityUtils.currentUser();

        if (!order.getCustomer().getId().equals(caller.getId())) {
            throw new ForbiddenException("Not your order");
        }
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new BadRequestException("Order can only be rated after delivery");
        }
        if (ratingRepository.existsByOrderId(orderId)) {
            throw new ConflictException("Order has already been rated");
        }

        Rating rating = Rating.builder()
                .order(order)
                .customer(caller)
                .restaurant(order.getRestaurant())
                .score(request.score())
                .comment(request.comment())
                .build();
        rating = ratingRepository.save(rating);
        return toResponse(rating);
    }

    public List<RatingResponse> listForRestaurant(Long restaurantId) {
        return ratingRepository.findByRestaurantId(restaurantId).stream().map(this::toResponse).toList();
    }

    private RatingResponse toResponse(Rating r) {
        return new RatingResponse(r.getId(), r.getOrder().getId(), r.getRestaurant().getId(),
                r.getScore(), r.getComment(), r.getCreatedAt());
    }
}
