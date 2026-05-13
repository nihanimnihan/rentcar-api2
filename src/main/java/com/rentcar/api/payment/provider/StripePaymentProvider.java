package com.rentcar.api.payment.provider;

import com.rentcar.api.domain.payment.Payment;
import com.rentcar.api.payment.model.PaymentResult;
import com.stripe.Stripe;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Profile("prod")
@Component
public class StripePaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentProvider.class);

    @Value("${stripe.api-key}")
    private String apiKey;

    @PostConstruct
    public void init() {
        Stripe.apiKey = apiKey;
    }

    @Override
    public PaymentResult pay(Payment payment, String paymentMethodId) {
        long amountInCents = payment.getAmount()
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency(payment.getCurrencyCode().toLowerCase())
                .setPaymentMethod(paymentMethodId)
                .setConfirm(true)
                // Fail immediately if card requires 3DS instead of hanging in requires_action.
                // Frontend must handle authentication using Stripe.js before calling this endpoint.
                .setErrorOnRequiresAction(true)
                .build();

        try {
            PaymentIntent intent = PaymentIntent.create(params);
            boolean succeeded = "succeeded".equals(intent.getStatus());
            log.info("Stripe PaymentIntent {} for payment {}: status={}", intent.getId(), payment.getId(), intent.getStatus());
            return new PaymentResult(succeeded, intent.getId());
        } catch (CardException e) {
            log.warn("Card declined for payment {}: code={} declineCode={}",
                    payment.getId(), e.getCode(), e.getDeclineCode());
            return new PaymentResult(false, null);
        } catch (StripeException e) {
            log.error("Stripe error for payment {}: {}", payment.getId(), e.getMessage(), e);
            return new PaymentResult(false, null);
        }
    }

    @Override
    public PaymentResult refund(Payment payment) {
        RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(payment.getProviderReference())
                .build();

        try {
            Refund refund = Refund.create(params);
            boolean succeeded = "succeeded".equals(refund.getStatus());
            log.info("Stripe Refund {} for payment {}: status={}", refund.getId(), payment.getId(), refund.getStatus());
            return new PaymentResult(succeeded, refund.getId());
        } catch (StripeException e) {
            log.error("Stripe refund error for payment {}: {}", payment.getId(), e.getMessage(), e);
            return new PaymentResult(false, null);
        }
    }
}
