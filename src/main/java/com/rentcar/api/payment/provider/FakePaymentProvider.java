package com.rentcar.api.payment.provider;

import com.rentcar.api.domain.payment.Payment;
import com.rentcar.api.payment.model.PaymentResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile({"dev", "local-postgres"})
@Component
public class FakePaymentProvider implements PaymentProvider {

    @Override
    public PaymentResult pay(Payment payment, String paymentMethodId) {
        return new PaymentResult(true, "FAKE-" + payment.getId());
    }
}
