package com.dmg.fooddelivery.controller;

import com.dmg.fooddelivery.dto.request.CityRequest;
import com.dmg.fooddelivery.dto.response.CityResponse;
import com.dmg.fooddelivery.service.CityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CityController {

    private final CityService cityService;

    /** Admin-only: managing cities is part of the admin role's scope. */
    @PostMapping("/api/admin/cities")
    public ResponseEntity<CityResponse> create(@Valid @RequestBody CityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cityService.create(request));
    }

    /** Public: customers/restaurants browse cities before authenticating. */
    @GetMapping("/api/cities")
    public ResponseEntity<List<CityResponse>> list() {
        return ResponseEntity.ok(cityService.listAll());
    }
}
