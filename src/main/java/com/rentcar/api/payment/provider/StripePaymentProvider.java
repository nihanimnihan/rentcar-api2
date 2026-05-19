package com.rentcar.api.payment.provider;

import com.rentcar.api.domain.payment.Payment;
import com.rentcar.api.payment.model.PaymentResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Placeholder for the Stripe payment provider.
 *
 * <p>Full Stripe integration is deferred for post-MVP. This stub throws
 * {@link UnsupportedOperationException} on every invocation so that any
 * accidental activation in a live environment fails fast and clearly, instead
 * of silently returning {@code null} or propagating a configuration NPE.
 *
 * <p>TODO (post-MVP): replace stub bodies with real Stripe SDK calls once
 *   {@code stripe.api-key} is configured and end-to-end payment flows are
 *   verified. Re-add {@code @Value("${stripe.api-key}")}, {@code @PostConstruct
 *   init()}, and the Stripe SDK imports that were removed from this placeholder.
 */
@Profile("prod")
@Component
public class StripePaymentProvider implements PaymentProvider {

    public static final String NOT_IMPLEMENTED_MSG =
            "Stripe payment integration is not yet configured. "
            + "Full payment processing is deferred for post-MVP.";

    @Override
    public PaymentResult pay(Payment payment, String paymentMethodId) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED_MSG);
    }

    @Override
    public PaymentResult refund(Payment payment) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED_MSG);
    }
}

