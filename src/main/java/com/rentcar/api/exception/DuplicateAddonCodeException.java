package com.rentcar.api.exception;

public class DuplicateAddonCodeException extends RuntimeException {

    public DuplicateAddonCodeException(String code) {
        super("An add-on with code '" + code + "' already exists.");
    }
}
