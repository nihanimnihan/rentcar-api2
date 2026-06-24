package com.rentcar.api.payment.provider;

import com.rentcar.api.domain.payment.Payment;
import com.rentcar.api.domain.handover.BookingDeposit;
import com.rentcar.api.payment.model.PaymentIntentResult;
import com.rentcar.api.payment.model.PaymentIntentVerification;
import com.rentcar.api.payment.model.PaymentResult;
import com.rentcar.api.payment.model.DepositCheckoutResult;

import java.math.BigDecimal;

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

    default PaymentIntentResult createDepositIntent(BookingDeposit deposit) {
        throw new UnsupportedOperationException("createDepositIntent not supported by provider");
    }

    default DepositCheckoutResult createDepositCheckoutSession(BookingDeposit deposit, String successUrl, String cancelUrl) {
        PaymentIntentResult intent = createDepositIntent(deposit);
        return new DepositCheckoutResult(
                intent.providerName(),
                null,
                successUrl,
                intent.providerIntentId(),
                intent.clientSecret()
        );
    }

    default PaymentResult refundDeposit(BookingDeposit deposit, BigDecimal amount) {
        throw new UnsupportedOperationException("refundDeposit not supported by provider");
    }

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
