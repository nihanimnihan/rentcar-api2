package com.rentcar.api.payment.model;

public record DepositCheckoutResult(
        String providerName,
        String checkoutSessionId,
        String checkoutUrl,
        String paymentIntentId,
        String clientSecret
) {}
