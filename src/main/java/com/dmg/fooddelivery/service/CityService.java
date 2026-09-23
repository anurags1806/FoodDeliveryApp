package com.dmg.fooddelivery.service;

import com.dmg.fooddelivery.dto.request.CityRequest;
import com.dmg.fooddelivery.dto.response.CityResponse;
import com.dmg.fooddelivery.exception.BadRequestException;
import com.dmg.fooddelivery.exception.NotFoundException;
import com.dmg.fooddelivery.model.City;
import com.dmg.fooddelivery.repository.CityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CityService {

    private final CityRepository cityRepository;

    @Transactional
    public CityResponse create(CityRequest request) {
        if (cityRepository.existsByNameIgnoreCase(request.name())) {
            throw new BadRequestException("City already exists: " + request.name());
        }
        City city = cityRepository.save(City.builder().name(request.name()).build());
        return toResponse(city);
    }

    public List<CityResponse> listAll() {
        return cityRepository.findAll().stream().map(this::toResponse).toList();
    }

    public City getOrThrow(Long id) {
        return cityRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("City not found: " + id));
    }

    private CityResponse toResponse(City c) {
        return new CityResponse(c.getId(), c.getName(), c.isActive());
    }
}
