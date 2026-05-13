package com.rentcar.api.exception;

public class RefundFailedException extends RuntimeException {

    private final Long paymentId;

    public RefundFailedException(Long paymentId) {
        super("Refund failed for payment " + paymentId + ". Booking cancellation aborted.");
        this.paymentId = paymentId;
    }

    public Long getPaymentId() {
        return paymentId;
    }
}
