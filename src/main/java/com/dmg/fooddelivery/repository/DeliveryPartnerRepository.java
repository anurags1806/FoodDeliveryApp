package com.dmg.fooddelivery.repository;

import com.dmg.fooddelivery.model.DeliveryPartner;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeliveryPartnerRepository extends JpaRepository<DeliveryPartner, Long> {

    Optional<DeliveryPartner> findByUserId(Long userId);

    List<DeliveryPartner> findByCityIdAndStatus(Long cityId, com.dmg.fooddelivery.model.DeliveryPartnerStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select dp from DeliveryPartner dp where dp.id = :id")
    Optional<DeliveryPartner> findByIdForUpdate(@Param("id") Long id);
}
