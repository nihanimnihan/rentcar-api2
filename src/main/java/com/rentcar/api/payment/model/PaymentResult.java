package com.rentcar.api.payment.model;

public record PaymentResult(
        boolean successful,
        String providerReference
) {
}