package com.rentcar.api.dto.payment;

/**
 * Request body for {@code POST /api/bookings/{id}/payments/intent}.
 *
 * <p>All fields are optional for now. The body may be empty {@code {}} or omitted
 * (caller sends no body) — Spring will inject a default instance via
 * {@code @RequestBody(required = false)}.
 *
 * <p>TODO: when multi-method support is added (BANK_TRANSFER, SEPA, etc.),
 *   validate {@code paymentMethodType} against an allowed-values list and set
 *   {@code Payment.method} accordingly instead of defaulting to CARD.
 */
public record CreatePaymentIntentRequest(

        /**
         * Intended payment method type.
         * Optional — defaults to {@code CARD} if absent.
         * Currently only {@code CARD} is supported.
         */
        String paymentMethodType
) {
    /** Canonical default when no body is supplied. */
    public static CreatePaymentIntentRequest defaults() {
        return new CreatePaymentIntentRequest(null);
    }
}
