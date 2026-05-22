package com.rentcar.api.exception;

/**
 * Thrown when a payment provider operation is attempted but the provider
 * is not yet configured for the current environment.
 *
 * <p>Converted to a {@code 503 Service Unavailable} response by
 * {@link GlobalExceptionHandler} so that callers receive a clean error
 * instead of a raw {@code 500} or {@link UnsupportedOperationException}.
 *
 * <p>Typical cause: the {@code prod} profile is active but Stripe SDK
 * credentials ({@code stripe.api-key}) have not been configured yet.
 */
public class PaymentProviderNotConfiguredException extends RuntimeException {

    public PaymentProviderNotConfiguredException(String providerName) {
        super(providerName + " payment provider is not yet configured. "
                + "Full payment processing is deferred for post-MVP.");
    }
}
