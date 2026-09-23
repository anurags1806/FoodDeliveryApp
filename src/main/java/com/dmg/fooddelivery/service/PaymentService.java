package com.dmg.fooddelivery.service;

import com.dmg.fooddelivery.model.PaymentStatus;
import com.dmg.fooddelivery.model.User;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Stand-in for a real payment gateway integration. Charges synchronously
 * so the result can be folded into the same DB transaction as the order
 * and stock decrement (per the "order placement must atomically reflect
 * item stock, order state, and payment" requirement). A real integration
 * would instead use a two-phase flow (authorize here, capture via
 * webhook), which is explicitly out of scope for this assignment.
 */
@Service
public class PaymentService {

    public PaymentStatus charge(User customer, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return PaymentStatus.FAILED;
        }
        return PaymentStatus.PAID;
    }
}
