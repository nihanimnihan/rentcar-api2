package com.rentcar.api.payment.model;

import java.util.Map;

/**
 * Provider-side PaymentIntent details used for server-side reconciliation before
 * a public booking is marked paid.
 */
public record PaymentIntentVerification(
        String id,
        String status,
        Long amountMinor,
        String currency,
        Map<String, String> metadata
) {
}
