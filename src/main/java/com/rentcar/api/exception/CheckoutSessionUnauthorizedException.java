package com.rentcar.api.exception;

public class CheckoutSessionUnauthorizedException extends RuntimeException {
    public CheckoutSessionUnauthorizedException(String message) {
        super(message);
    }
}
