package com.dmg.fooddelivery.service;

import static java.util.Objects.requireNonNull;

import com.dmg.fooddelivery.dto.request.CityRequest;
import com.dmg.fooddelivery.dto.response.CityResponse;
import com.dmg.fooddelivery.exception.BadRequestException;
import com.dmg.fooddelivery.exception.NotFoundException;
import com.dmg.fooddelivery.model.City;
import com.dmg.fooddelivery.repository.CityRepository;
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
public class CityService {

    private final CityRepository cityRepository;

    @Transactional
    public CityResponse create(@NotNull @Valid CityRequest request) {
        if (cityRepository.existsByNameIgnoreCase(requireNonNull(request.name()))) {
            throw new BadRequestException("City already exists: " + requireNonNull(request.name()));
        }
        City city = cityRepository.save(City.builder().name(requireNonNull(request.name())).build());
        return toResponse(city);
    }

    public List<CityResponse> listAll() {
        return requireNonNull(cityRepository.findAll().stream().map(this::toResponse).toList());
    }

    public City getOrThrow(@NotNull Long id) {
        return requireNonNull(cityRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("City not found: " + id)));
    }

    private CityResponse toResponse(City c) {
        return new CityResponse(c.getId(), c.getName(), c.isActive());
    }
}
