package com.rentcar.api.payment.provider;

import com.rentcar.api.domain.payment.Payment;
import com.rentcar.api.payment.model.PaymentResult;

public interface PaymentProvider {
    PaymentResult pay(Payment payment, String paymentMethodId);
}
