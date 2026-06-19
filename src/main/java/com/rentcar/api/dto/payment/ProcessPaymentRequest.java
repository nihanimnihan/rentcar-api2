package com.rentcar.api.dto.payment;

public record ProcessPaymentRequest(

        /**
         * Stripe PaymentMethod ID created by the frontend using Stripe.js.
         * Example: "pm_1234abcd..."
         */
        String paymentMethodId,

        /**
         * Checkout ownership token returned with booking creation. Required while
         * the booking is still held by an anonymous checkout session.
         */
        String checkoutSessionToken
) {
}
