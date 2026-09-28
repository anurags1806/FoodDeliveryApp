package com.dmg.fooddelivery.exception;

import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import java.util.Objects;

public class ApiException extends RuntimeException {
    @NonNull
    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    @NonNull
    public HttpStatus getStatus() {
        return status;
    }
}
