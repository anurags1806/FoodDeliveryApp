package com.dmg.fooddelivery.exception;

import java.time.Instant;
import java.util.List;
import org.springframework.lang.Nullable;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        @Nullable List<String> details
) {
    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(Instant.now(), status, error, message, null);
    }

    public static ErrorResponse of(int status, String error, String message, @Nullable List<String> details) {
        return new ErrorResponse(Instant.now(), status, error, message, details);
    }
}
