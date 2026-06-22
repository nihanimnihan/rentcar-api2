package com.rentcar.api.dto.payment;

import java.math.BigDecimal;

/**
 * Response for {@code POST /api/bookings/{id}/payments/intent}.
 *
 * <p>{@link #clientSecret()} is the {@code pi_xxx_secret_yyy} Stripe value
 * that the frontend passes to {@code stripe.confirmCardPayment(clientSecret)}
 * to authorise the charge.
 *
 * <p>Amount and currency are always derived from the booking stored on the server —
 * the frontend must never supply or override these values.
 *
 * <p>{@link #paymentReference()} is the public-facing reference for this payment record
 * (e.g. {@code PAY-3F4A8B2C}). The internal numeric id is not exposed.
 */
public record PaymentIntentResponse(
        Long bookingId,
        String bookingReference,
        BigDecimal amount,
        String currency,
        String provider,
        String clientSecret,
        String paymentReference   // public payment reference, never the internal numeric id
) {
}
