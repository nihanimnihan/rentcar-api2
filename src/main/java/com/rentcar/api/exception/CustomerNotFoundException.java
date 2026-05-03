package com.rentcar.api.exception;

public class CustomerNotFoundException extends RuntimeException {
    private final Long customerId;

    public CustomerNotFoundException(Long customerId) {
        super("Customer not found for booking");
        this.customerId = customerId;
    }

    public Long getCustomerId() {
        return customerId;
    }
}
