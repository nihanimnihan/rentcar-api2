package com.rentcar.api.dto.payment;

import jakarta.validation.constraints.NotBlank;

public record ProcessPaymentRequest(

        /**
         * Stripe PaymentMethod ID created by the frontend using Stripe.js.
         * Example: "pm_1234abcd..."
         */
        @NotBlank(message = "paymentMethodId is required")
        String paymentMethodId
) {
}
