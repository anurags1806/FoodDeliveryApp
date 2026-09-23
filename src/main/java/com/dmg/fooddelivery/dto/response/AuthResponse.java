package com.dmg.fooddelivery.dto.response;

public record AuthResponse(String token, Long userId, String name, String email, String role) {}
