package com.rentcar.api.payment.model;

public record PaymentResult(
        boolean successful,
        String providerReference,
        String providerStatus
) {
    public PaymentResult(boolean successful, String providerReference) {
        this(successful, providerReference, null);
    }
}
