package com.rentcar.api.payment.provider;

import com.rentcar.api.domain.payment.Payment;
import com.rentcar.api.payment.model.PaymentResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("prod")
@Component
public class StripePaymentProvider implements PaymentProvider {
    @Override
    public PaymentResult pay(Payment payment) {
        // Stripe call
        return null;
    }
}
