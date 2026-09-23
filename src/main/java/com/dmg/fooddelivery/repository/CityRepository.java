package com.dmg.fooddelivery.repository;

import com.dmg.fooddelivery.model.City;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CityRepository extends JpaRepository<City, Long> {
    boolean existsByNameIgnoreCase(String name);
}
