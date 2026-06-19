package com.rentcar.api.service;

import com.rentcar.api.exception.PaymentProviderNotConfiguredException;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StripeWebhookService {

    private final PaymentService paymentService;

    @Value("${stripe.webhook-secret:}")
    private String webhookSecret;

    public void handleEvent(String payload, String signatureHeader)
            throws SignatureVerificationException {

        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new PaymentProviderNotConfiguredException("Stripe webhook");
        }

        Event event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
        StripeObject stripeObject = dataObject(event);

        switch (event.getType()) {
            case "payment_intent.succeeded",
                    "payment_intent.payment_failed",
                    "payment_intent.canceled",
                    "payment_intent.processing",
                    "payment_intent.requires_action" -> handlePaymentIntentEvent(event, stripeObject);
            case "charge.refunded" -> handleChargeRefunded(event, stripeObject);
            case "refund.created", "refund.updated", "refund.failed" -> handleRefundEvent(event, stripeObject);
            default -> log.debug("Ignored Stripe webhook event type={} id={}", event.getType(), event.getId());
        }
    }

    private void handlePaymentIntentEvent(Event event, StripeObject stripeObject) {
        if (!(stripeObject instanceof PaymentIntent intent)) {
            throw new IllegalArgumentException("Expected PaymentIntent for event " + event.getType());
        }

        paymentService.applyStripePaymentIntentStatus(
                        intent.getId(),
                        intent.getStatus(),
                        intent.getLatestCharge() != null ? intent.getLatestCharge() : intent.getId())
                .ifPresentOrElse(
                        payment -> log.info("Stripe webhook applied: eventId={} type={} paymentId={} status={}",
                                event.getId(), event.getType(), payment.getId(), payment.getStatus()),
                        () -> log.warn("Stripe webhook payment not found: eventId={} type={} intentId={}",
                                event.getId(), event.getType(), intent.getId()));
    }

    private void handleChargeRefunded(Event event, StripeObject stripeObject) {
        if (!(stripeObject instanceof Charge charge)) {
            throw new IllegalArgumentException("Expected Charge for event " + event.getType());
        }

        String paymentIntentId = charge.getPaymentIntent();
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            log.warn("Stripe charge.refunded without payment_intent: eventId={} chargeId={}",
                    event.getId(), charge.getId());
            return;
        }

        paymentService.applyStripeRefundStatus(paymentIntentId, charge.getId(), "succeeded")
                .ifPresentOrElse(
                        payment -> log.info("Stripe charge refund applied: eventId={} paymentId={}",
                                event.getId(), payment.getId()),
                        () -> log.warn("Stripe charge refund payment not found: eventId={} intentId={}",
                                event.getId(), paymentIntentId));
    }

    private void handleRefundEvent(Event event, StripeObject stripeObject) {
        if (!(stripeObject instanceof Refund refund)) {
            throw new IllegalArgumentException("Expected Refund for event " + event.getType());
        }

        String paymentIntentId = refund.getPaymentIntent();
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            log.warn("Stripe refund event without payment_intent: eventId={} refundId={}",
                    event.getId(), refund.getId());
            return;
        }

        paymentService.applyStripeRefundStatus(paymentIntentId, refund.getId(), refund.getStatus())
                .ifPresentOrElse(
                        payment -> log.info("Stripe refund webhook applied: eventId={} type={} paymentId={} status={}",
                                event.getId(), event.getType(), payment.getId(), payment.getStatus()),
                        () -> log.warn("Stripe refund payment not found: eventId={} type={} intentId={}",
                                event.getId(), event.getType(), paymentIntentId));
    }

    private StripeObject dataObject(Event event) {
        return event.getDataObjectDeserializer()
                .getObject()
                .orElseGet(() -> deserializeUnsafe(event));
    }

    private StripeObject deserializeUnsafe(Event event) {
        try {
            return event.getDataObjectDeserializer().deserializeUnsafe();
        } catch (EventDataObjectDeserializationException e) {
            throw new IllegalArgumentException(
                    "Unable to deserialize Stripe webhook event " + event.getId(), e);
        }
    }
}
