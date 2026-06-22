package com.rentcar.api.payment.provider;

import com.rentcar.api.domain.payment.Payment;
import com.rentcar.api.payment.model.PaymentIntentResult;
import com.rentcar.api.payment.model.PaymentIntentVerification;
import com.rentcar.api.payment.model.PaymentResult;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Map;

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
@Profile("test")
@Component
public class FakePaymentProvider implements PaymentProvider, EnvironmentAware {

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

    @Override
    public PaymentIntentResult createIntent(Payment payment) {
        // Synthetic client secret for dev/test: deterministic per payment record.
        // Real Stripe would return a "pi_xxx_secret_yyy" string from the Stripe API.
        return new PaymentIntentResult(
                providerName(),
                "fake_client_secret_" + payment.getId(),
                "fake_intent_" + payment.getId()
        );
    }

    @Override
    public String providerName() {
        return "FAKE";
    }

    @Override
    public String fetchPaymentIntentStatus(com.rentcar.api.domain.payment.Payment payment) {
        // Fake provider resolves synchronously to 'succeeded' for local testing flows.
        return "succeeded";
    }

    @Override
    public PaymentIntentVerification fetchPaymentIntent(com.rentcar.api.domain.payment.Payment payment) {
        return new PaymentIntentVerification(
                payment.getStripePaymentIntentId(),
                fetchPaymentIntentStatus(payment),
                amountInMinorUnits(payment),
                payment.getCurrencyCode().toLowerCase(java.util.Locale.ROOT),
                Map.of(
                        "bookingId", String.valueOf(payment.getBooking().getId()),
                        "paymentId", String.valueOf(payment.getId()),
                        "paymentReference", payment.getPaymentReference()
                )
        );
    }

    @Override
    public void setEnvironment(Environment environment) {
        if (!Arrays.asList(environment.getActiveProfiles()).contains("test")) {
            throw new IllegalStateException("Fake payment provider is only allowed with the test profile");
        }
    }

    private long amountInMinorUnits(Payment payment) {
        return payment.getAmount()
                .setScale(2, RoundingMode.UNNECESSARY)
                .multiply(java.math.BigDecimal.valueOf(100))
                .longValueExact();
    }
}

