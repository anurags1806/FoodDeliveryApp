package com.dmg.fooddelivery.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "delivery_partner")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryPartner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "city_id")
    private City city;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private DeliveryPartnerStatus status = DeliveryPartnerStatus.AVAILABLE;

    /**
     * Optimistic lock. Accepting an assignment flips AVAILABLE -> BUSY;
     * version check stops two concurrent "accept" requests for the same
     * partner (across two different orders) from both succeeding.
     */
    @Version
    private Long version;
}
