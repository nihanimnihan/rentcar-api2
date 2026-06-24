package com.rentcar.api.payment.provider;

import com.rentcar.api.domain.handover.BookingDeposit;
import com.rentcar.api.domain.payment.Payment;
import com.rentcar.api.exception.PaymentProviderNotConfiguredException;
import com.rentcar.api.payment.model.PaymentIntentResult;
import com.rentcar.api.payment.model.PaymentIntentVerification;
import com.rentcar.api.payment.model.PaymentResult;
import com.rentcar.api.payment.model.DepositCheckoutResult;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.param.PaymentIntentConfirmParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.net.RequestOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Minimal Stripe payment provider implementation for MVP intent creation.
 *
 * <p>When {@code stripe.api-key} is not configured (or this class is directly
 * instantiated in unit tests), methods throw {@link PaymentProviderNotConfiguredException}
 * to yield a 503 Service Unavailable instead of a raw NPE or 500.
 */
@Profile({"dev", "prod"})
@Component
public class StripePaymentProvider implements PaymentProvider {

    @Value("${stripe.api-key:}")
    private String stripeApiKey;

    @Override
    public PaymentResult pay(Payment payment, String paymentMethodId) {
        requireConfigured();
        if (paymentMethodId == null || paymentMethodId.isBlank()) {
            throw new IllegalArgumentException("paymentMethodId is required for direct Stripe payment processing");
        }

        try {
            PaymentIntent intent;
            if (payment.getStripePaymentIntentId() != null && !payment.getStripePaymentIntentId().isBlank()) {
                intent = PaymentIntent.retrieve(payment.getStripePaymentIntentId(), requestOptions());
                if ("requires_payment_method".equals(intent.getStatus())
                        || "requires_confirmation".equals(intent.getStatus())) {
                    PaymentIntentConfirmParams.Builder builder = PaymentIntentConfirmParams.builder()
                            .setPaymentMethod(paymentMethodId);
                    String email = customerEmail(payment);
                    if (email != null && !email.isBlank()) {
                        builder.setReceiptEmail(email);
                    }
                    PaymentIntentConfirmParams params = builder.build();
                    intent = intent.confirm(params, requestOptions("stripe-confirm-" + payment.getPaymentReference()));
                }
            } else {
                PaymentIntentCreateParams params = baseIntentParams(payment)
                        .setPaymentMethod(paymentMethodId)
                        .setConfirm(true)
                        .build();
                intent = PaymentIntent.create(params, requestOptions("stripe-pay-" + payment.getPaymentReference()));
            }

            return new PaymentResult(isSucceeded(intent.getStatus()), intent.getId(), intent.getStatus());
        } catch (StripeException e) {
            throw new RuntimeException("Stripe error while processing payment: " + e.getMessage(), e);
        }
    }

    @Override
    public PaymentResult refund(Payment payment) {
        requireConfigured();

        String paymentIntentId = payment.getStripePaymentIntentId();
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            paymentIntentId = payment.getProviderReference();
        }
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            throw new IllegalStateException("Stripe payment intent or charge id is required before refunding payment " + payment.getId());
        }

        try {
            RefundCreateParams.Builder builder = RefundCreateParams.builder()
                    .putMetadata("bookingReference", payment.getBooking().getBookingReference())
                    .putMetadata("bookingId", String.valueOf(payment.getBooking().getId()))
                    .putMetadata("paymentId", String.valueOf(payment.getId()))
                    .putMetadata("paymentReference", payment.getPaymentReference());
            if (paymentIntentId.startsWith("pi_")) {
                builder.setPaymentIntent(paymentIntentId);
            } else if (paymentIntentId.startsWith("ch_")) {
                builder.setCharge(paymentIntentId);
            } else {
                throw new IllegalStateException("Unsupported Stripe refund reference: " + paymentIntentId);
            }

            Refund refund = Refund.create(builder.build(), requestOptions("stripe-refund-" + payment.getPaymentReference()));
            return new PaymentResult("succeeded".equals(refund.getStatus()), refund.getId(), refund.getStatus());
        } catch (StripeException e) {
            throw new RuntimeException("Stripe error while refunding payment: " + e.getMessage(), e);
        }
    }

    @Override
    public PaymentIntentResult createIntent(Payment payment) {
        requireConfigured();

        try {
            if (payment.getStripePaymentIntentId() != null && !payment.getStripePaymentIntentId().isBlank()) {
                PaymentIntent existing = PaymentIntent.retrieve(payment.getStripePaymentIntentId(), requestOptions());
                return new PaymentIntentResult(providerName(), existing.getClientSecret(), existing.getId());
            }

            PaymentIntentCreateParams params = baseIntentParams(payment).build();
            PaymentIntent pi = PaymentIntent.create(params, requestOptions("stripe-intent-" + payment.getPaymentReference()));

            return new PaymentIntentResult(providerName(), pi.getClientSecret(), pi.getId());
        } catch (StripeException e) {
            throw new RuntimeException("Stripe error while creating payment intent: " + e.getMessage(), e);
        }
    }

    @Override
    public PaymentIntentResult createDepositIntent(BookingDeposit deposit) {
        requireConfigured();

        try {
            if (deposit.getStripePaymentIntentId() != null && !deposit.getStripePaymentIntentId().isBlank()) {
                PaymentIntent existing = PaymentIntent.retrieve(deposit.getStripePaymentIntentId(), requestOptions());
                return new PaymentIntentResult(providerName(), existing.getClientSecret(), existing.getId());
            }

            PaymentIntentCreateParams.Builder builder = PaymentIntentCreateParams.builder()
                    .setAmount(amountInSmallestCurrencyUnit(deposit.getAmount()))
                    .setCurrency(deposit.getCurrency().toLowerCase(Locale.ROOT))
                    .addPaymentMethodType("card")
                    .setDescription("RentCar deposit " + deposit.getBooking().getBookingReference())
                    .putMetadata("bookingReference", deposit.getBooking().getBookingReference())
                    .putMetadata("bookingId", String.valueOf(deposit.getBooking().getId()))
                    .putMetadata("depositId", String.valueOf(deposit.getId()))
                    .putMetadata("paymentType", "DEPOSIT");
            String email = deposit.getBooking() != null && deposit.getBooking().getCustomer() != null
                    ? deposit.getBooking().getCustomer().getEmail()
                    : null;
            if (email != null && !email.isBlank()) {
                builder.setReceiptEmail(email);
            }

            PaymentIntent pi = PaymentIntent.create(builder.build(), requestOptions("stripe-deposit-" + deposit.getId()));
            return new PaymentIntentResult(providerName(), pi.getClientSecret(), pi.getId());
        } catch (StripeException e) {
            throw new RuntimeException("Stripe error while creating deposit payment intent: " + e.getMessage(), e);
        }
    }

    @Override
    public DepositCheckoutResult createDepositCheckoutSession(BookingDeposit deposit, String successUrl, String cancelUrl) {
        requireConfigured();

        try {
            SessionCreateParams.Builder builder = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancelUrl)
                    .putMetadata("bookingReference", deposit.getBooking().getBookingReference())
                    .putMetadata("bookingId", String.valueOf(deposit.getBooking().getId()))
                    .putMetadata("depositId", String.valueOf(deposit.getId()))
                    .putMetadata("paymentType", "DEPOSIT")
                    .setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder()
                            .putMetadata("bookingReference", deposit.getBooking().getBookingReference())
                            .putMetadata("bookingId", String.valueOf(deposit.getBooking().getId()))
                            .putMetadata("depositId", String.valueOf(deposit.getId()))
                            .putMetadata("paymentType", "DEPOSIT")
                            .build())
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency(deposit.getCurrency().toLowerCase(Locale.ROOT))
                                    .setUnitAmount(amountInSmallestCurrencyUnit(deposit.getAmount()))
                                    .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                            .setName("RentCar deposit " + deposit.getBooking().getBookingReference())
                                            .build())
                                    .build())
                            .build());
            String email = deposit.getBooking() != null && deposit.getBooking().getCustomer() != null
                    ? deposit.getBooking().getCustomer().getEmail()
                    : null;
            if (email != null && !email.isBlank()) {
                builder.setCustomerEmail(email);
            }

            Session session = Session.create(builder.build(), requestOptions("stripe-deposit-session-" + deposit.getId()));
            return new DepositCheckoutResult(providerName(), session.getId(), session.getUrl(), session.getPaymentIntent(), null);
        } catch (StripeException e) {
            throw new RuntimeException("Stripe error while creating deposit checkout session: " + e.getMessage(), e);
        }
    }

    @Override
    public PaymentResult refundDeposit(BookingDeposit deposit, BigDecimal amount) {
        requireConfigured();
        String paymentIntentId = deposit.getStripePaymentIntentId();
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            throw new IllegalStateException("Stripe deposit PaymentIntent id is required before refunding deposit " + deposit.getId());
        }
        try {
            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(paymentIntentId)
                    .setAmount(amountInSmallestCurrencyUnit(amount))
                    .putMetadata("bookingReference", deposit.getBooking().getBookingReference())
                    .putMetadata("bookingId", String.valueOf(deposit.getBooking().getId()))
                    .putMetadata("depositId", String.valueOf(deposit.getId()))
                    .putMetadata("paymentType", "DEPOSIT")
                    .build();
            Refund refund = Refund.create(params, requestOptions("stripe-deposit-refund-" + deposit.getId() + "-" + amount));
            return new PaymentResult("succeeded".equals(refund.getStatus()), refund.getId(), refund.getStatus());
        } catch (StripeException e) {
            throw new RuntimeException("Stripe error while refunding deposit: " + e.getMessage(), e);
        }
    }

    @Override
    public String providerName() {
        return "STRIPE";
    }

    @Override
    public String fetchPaymentIntentStatus(com.rentcar.api.domain.payment.Payment payment) {
        requireConfigured();
        String intentId = payment.getStripePaymentIntentId();
        if (intentId == null) throw new IllegalArgumentException("Payment has no stripePaymentIntentId");

        try {
            PaymentIntent pi = PaymentIntent.retrieve(intentId, requestOptions());
            return pi.getStatus();
        } catch (StripeException e) {
            throw new RuntimeException("Stripe error while fetching payment intent: " + e.getMessage(), e);
        }
    }

    @Override
    public PaymentIntentVerification fetchPaymentIntent(com.rentcar.api.domain.payment.Payment payment) {
        requireConfigured();
        String intentId = payment.getStripePaymentIntentId();
        if (intentId == null || intentId.isBlank()) {
            throw new IllegalArgumentException("Payment has no stripePaymentIntentId");
        }

        try {
            PaymentIntent pi = PaymentIntent.retrieve(intentId, requestOptions());
            return new PaymentIntentVerification(
                    pi.getId(),
                    pi.getStatus(),
                    pi.getAmount(),
                    pi.getCurrency(),
                    pi.getMetadata()
            );
        } catch (StripeException e) {
            throw new RuntimeException("Stripe error while fetching payment intent: " + e.getMessage(), e);
        }
    }

    private PaymentIntentCreateParams.Builder baseIntentParams(Payment payment) {
        PaymentIntentCreateParams.Builder builder = PaymentIntentCreateParams.builder()
                .setAmount(amountInSmallestCurrencyUnit(payment.getAmount()))
                .setCurrency(payment.getCurrencyCode().toLowerCase(Locale.ROOT))
                .addPaymentMethodType("card")
                .setDescription("RentCar booking " + payment.getBooking().getBookingReference())
                .putMetadata("bookingReference", payment.getBooking().getBookingReference())
                .putMetadata("bookingId", String.valueOf(payment.getBooking().getId()))
                .putMetadata("paymentId", String.valueOf(payment.getId()))
                .putMetadata("paymentReference", payment.getPaymentReference());
        String email = customerEmail(payment);
        if (email != null && !email.isBlank()) {
            builder.setReceiptEmail(email);
        }
        return builder;
    }

    private long amountInSmallestCurrencyUnit(BigDecimal amount) {
        BigDecimal normalized = amount.setScale(2, RoundingMode.UNNECESSARY);
        return normalized.multiply(BigDecimal.valueOf(100)).longValueExact();
    }

    private String customerEmail(Payment payment) {
        return payment.getBooking() != null
                && payment.getBooking().getCustomer() != null
                ? payment.getBooking().getCustomer().getEmail()
                : null;
    }

    private boolean isSucceeded(String status) {
        return "succeeded".equals(status);
    }

    private RequestOptions requestOptions() {
        return RequestOptions.builder().setApiKey(stripeApiKey).build();
    }

    private RequestOptions requestOptions(String idempotencyKey) {
        return RequestOptions.builder()
                .setApiKey(stripeApiKey)
                .setIdempotencyKey(idempotencyKey)
                .build();
    }

    private void requireConfigured() {
        if (stripeApiKey == null || stripeApiKey.isBlank()) {
            throw new PaymentProviderNotConfiguredException("Stripe");
        }
    }
}
