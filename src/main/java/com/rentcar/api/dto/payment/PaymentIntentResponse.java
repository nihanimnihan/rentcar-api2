package com.rentcar.api.dto.payment;

import java.math.BigDecimal;

/**
 * Response for {@code POST /api/bookings/{id}/payments/intent}.
 *
 * <p>{@link #clientSecret()} is {@code null} for the fake dev provider.
 * Real Stripe returns a {@code pi_xxx_secret_yyy} string here, which the frontend
 * passes to {@code stripe.confirmCardPayment(clientSecret)} to authorise the charge.
 *
 * <p>Amount and currency are always derived from the booking stored on the server —
 * the frontend must never supply or override these values.
 */
public record PaymentIntentResponse(
        Long bookingId,
        String bookingReference,
        BigDecimal amount,
        String currency,
        String provider,
        String clientSecret,  // nullable: present for Stripe; synthetic for FakePaymentProvider
        Long paymentId
) {
}
