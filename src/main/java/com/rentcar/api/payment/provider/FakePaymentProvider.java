package com.rentcar.api.payment.provider;

import com.rentcar.api.domain.payment.Payment;
import com.rentcar.api.payment.model.PaymentResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Dev/local fake {@link PaymentProvider} that resolves synchronously.
 *
 * <p>Simulates provider success/failure without any real network call.
 * Use {@link #FORCE_FAIL_METHOD_ID} to exercise the FAILED booking path.
 *
 * <p><b>Async contract note:</b> this fake resolves immediately ({@code successful=true/false})
 * so both {@code pay()} and {@code refund()} return a final result in a single call.
 * A real Stripe implementation would typically:
 * <ul>
 *   <li>{@code pay()} → return {@code successful=false} with a "processing" reference,
 *       leaving the booking PENDING until the {@code payment_intent.succeeded} webhook arrives.</li>
 *   <li>{@code refund()} → set payment to {@link com.rentcar.api.domain.payment.PaymentStatus#REFUND_PENDING}
 *       until the {@code charge.refunded} webhook confirms the refund.</li>
 * </ul>
 */
@Profile({"dev", "local-postgres"})
@Component
public class FakePaymentProvider implements PaymentProvider {

    /**
     * Pass this paymentMethodId to simulate a provider-side payment failure.
     * Useful for integration tests that need to exercise the FAILED booking path
     * without mocking the Spring context.
     */
    public static final String FORCE_FAIL_METHOD_ID = "pm_fail";

    @Override
    public PaymentResult pay(Payment payment, String paymentMethodId) {
        if (FORCE_FAIL_METHOD_ID.equals(paymentMethodId)) {
            return new PaymentResult(false, "FAKE-FAIL-" + payment.getId());
        }
        return new PaymentResult(true, "FAKE-" + payment.getId());
    }

    @Override
    public PaymentResult refund(Payment payment) {
        return new PaymentResult(true, "FAKE-REFUND-" + payment.getId());
    }
}
