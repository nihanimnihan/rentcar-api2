package com.rentcar.api.payment.provider;

import com.rentcar.api.domain.payment.Payment;
import com.rentcar.api.payment.model.PaymentIntentResult;
import com.rentcar.api.payment.model.PaymentIntentVerification;
import com.rentcar.api.payment.model.PaymentResult;

public interface PaymentProvider {

    PaymentResult pay(Payment payment, String paymentMethodId);

    PaymentResult refund(Payment payment);

    /**
     * Creates a payment intent with the provider without charging the customer.
     *
     * <p>The returned {@link PaymentIntentResult#clientSecret()} is forwarded to the
     * frontend, which uses it to confirm the payment (e.g. {@code stripe.confirmCardPayment()}).
     * Public checkout callers must receive a real Stripe client secret; fake/dev
     * synthetic secrets are rejected before they reach this interface.
     */
    PaymentIntentResult createIntent(Payment payment);

    /**
     * Short display name for this provider; included in API responses.
     * Examples: {@code "FAKE"}, {@code "STRIPE"}.
     */
    String providerName();

    /**
     * Fetch the provider-side PaymentIntent status for a payment (e.g. Stripe PaymentIntent.status).
     * Default implementation throws UnsupportedOperationException and should be overridden by providers
     * that support external intent verification (e.g. Stripe, Fake for local testing).
     */
    default String fetchPaymentIntentStatus(com.rentcar.api.domain.payment.Payment payment) {
        throw new UnsupportedOperationException("fetchPaymentIntentStatus not supported by provider");
    }

    /**
     * Fetch provider-side PaymentIntent details for reconciliation before a public
     * booking is marked paid. Providers that can confirm public checkout payments
     * must override this.
     */
    default PaymentIntentVerification fetchPaymentIntent(com.rentcar.api.domain.payment.Payment payment) {
        throw new UnsupportedOperationException("fetchPaymentIntent not supported by provider");
    }
}
