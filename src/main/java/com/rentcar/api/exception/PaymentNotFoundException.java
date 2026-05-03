package com.rentcar.api.exception;

public class PaymentNotFoundException extends RuntimeException {
    private final Long bookingId;

    public PaymentNotFoundException(Long bookingId) {
        super("Payment not found for booking");
        this.bookingId = bookingId;
    }

    public Long getPaymentId() {
        return bookingId;
    }

}
