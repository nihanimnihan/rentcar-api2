package com.rentcar.api.payment.provider;

import com.rentcar.api.domain.payment.Payment;
import com.rentcar.api.payment.model.PaymentResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

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
