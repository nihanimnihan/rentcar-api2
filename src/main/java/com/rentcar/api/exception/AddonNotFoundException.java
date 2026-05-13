package com.rentcar.api.exception;

public class AddonNotFoundException extends RuntimeException {

    public AddonNotFoundException(Long id) {
        super("Addon not found with id: " + id);
    }
}
