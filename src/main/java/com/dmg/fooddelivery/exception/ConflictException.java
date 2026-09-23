package com.dmg.fooddelivery.exception;

import org.springframework.http.HttpStatus;

/** Used for state races: stock exhausted, order already claimed by another
 *  delivery partner, invalid status transition, duplicate rating, etc. */
public class ConflictException extends ApiException {
    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
