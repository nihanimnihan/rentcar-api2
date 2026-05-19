package com.rentcar.api.exception;

public class InvalidTransferRequestException extends RuntimeException {

    public InvalidTransferRequestException(String message) {
        super(message);
    }
}
