package com.rentcar.api.payment.provider;

import com.rentcar.api.domain.payment.Payment;
import com.rentcar.api.exception.PaymentProviderNotConfiguredException;
import com.rentcar.api.payment.model.PaymentIntentResult;
import com.rentcar.api.payment.model.PaymentResult;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.net.RequestOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Minimal Stripe payment provider implementation for MVP intent creation.
 *
 * <p>When {@code stripe.api-key} is not configured (or this class is directly
 * instantiated in unit tests), methods throw {@link PaymentProviderNotConfiguredException}
 * to yield a 503 Service Unavailable instead of a raw NPE or 500.
 */
@Profile("prod")
@Component
public class StripePaymentProvider implements PaymentProvider {

    @Value("${stripe.api-key:}")
    private String stripeApiKey;

    @Override
    public PaymentResult pay(Payment payment, String paymentMethodId) {
        throw new PaymentProviderNotConfiguredException("Stripe");
    }

    @Override
    public PaymentResult refund(Payment payment) {
        throw new PaymentProviderNotConfiguredException("Stripe");
    }

    @Override
    public PaymentIntentResult createIntent(Payment payment) {
        if (stripeApiKey == null || stripeApiKey.isBlank()) {
            throw new PaymentProviderNotConfiguredException("Stripe");
        }

        try {
            // Convert amount (BigDecimal with scale 2) to smallest currency unit (cents)
            BigDecimal amount = payment.getAmount().setScale(2, RoundingMode.UNNECESSARY);
            long amountInCents = amount.multiply(BigDecimal.valueOf(100)).longValueExact();

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency(payment.getCurrencyCode().toLowerCase())
                    .addPaymentMethodType("card")
                    .putMetadata("bookingReference", payment.getBooking().getBookingReference())
                    .build();

            RequestOptions requestOptions = RequestOptions.builder().setApiKey(stripeApiKey).build();
            PaymentIntent pi = PaymentIntent.create(params, requestOptions);

            return new PaymentIntentResult(providerName(), pi.getClientSecret(), pi.getId());
        } catch (StripeException e) {
            // Surface a clear error for higher layers; GlobalExceptionHandler will map to 500.
            throw new RuntimeException("Stripe error: " + e.getMessage(), e);
        }
    }

    @Override
    public String providerName() {
        return "STRIPE";
    }
}
