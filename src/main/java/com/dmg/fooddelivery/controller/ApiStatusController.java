package com.dmg.fooddelivery.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class ApiStatusController {

    @GetMapping("/")
    public Map<String, String> status() {
        return Map.of(
                "application", "food-delivery-order-management",
                "status", "running",
                "api", "/api"
        );
    }
}