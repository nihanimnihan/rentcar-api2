package com.rentcar.api.payment.model;

/**
 * Returned by {@link com.rentcar.api.payment.provider.PaymentProvider#createIntent}.
 *
 * <p>{@link #clientSecret()} is nullable. The fake provider returns a synthetic value;
 * real Stripe returns a {@code pi_xxx_secret_yyy} string that the frontend uses with
 * {@code stripe.confirmCardPayment()}.
 */
public record PaymentIntentResult(
        String providerName,
        String clientSecret,      // nullable; present for Stripe frontend confirmation
        String providerIntentId   // provider-side intent reference for reconciliation
) {
}
