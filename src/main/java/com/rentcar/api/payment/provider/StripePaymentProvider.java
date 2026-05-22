package com.rentcar.api.payment.provider;

import com.rentcar.api.domain.payment.Payment;
import com.rentcar.api.exception.PaymentProviderNotConfiguredException;
import com.rentcar.api.payment.model.PaymentIntentResult;
import com.rentcar.api.payment.model.PaymentResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Placeholder for the Stripe payment provider.
 *
 * <p>Full Stripe integration is deferred for post-MVP. All methods throw
 * {@link PaymentProviderNotConfiguredException}, which is converted to a
 * {@code 503 Service Unavailable} response — never a raw 500 or NPE.
 *
 * <p>TODO (post-MVP): replace stub bodies with real Stripe SDK calls once
 *   {@code stripe.api-key} is configured and end-to-end payment flows are
 *   verified. Re-add {@code @Value("${stripe.api-key}")}, {@code @PostConstruct
 *   init()}, and the Stripe SDK imports that were removed from this placeholder.
 */
@Profile("prod")
@Component
public class StripePaymentProvider implements PaymentProvider {

    @Override
    public PaymentResult pay(Payment payment, String paymentMethodId) {
        throw new PaymentProviderNotConfiguredException("Stripe");
    }

    @Override
    public PaymentResult refund(Payment payment) {
        throw new PaymentProviderNotConfiguredException("Stripe");
    }

    /**
     * TODO (Stripe): implement via {@code stripe.paymentIntents().create()} with
     *   {@code amount} in smallest currency unit (cents), {@code currency},
     *   and {@code metadata.bookingReference} for reconciliation.
     *   Return the Stripe {@code PaymentIntent.clientSecret} and {@code PaymentIntent.id}.
     */
    @Override
    public PaymentIntentResult createIntent(Payment payment) {
        throw new PaymentProviderNotConfiguredException("Stripe");
    }

    @Override
    public String providerName() {
        return "STRIPE";
    }
}
