package com.rentcar.api.service;

import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.domain.payment.Payment;
import com.rentcar.api.domain.payment.PaymentChannel;
import com.rentcar.api.domain.payment.PaymentMethod;
import com.rentcar.api.domain.payment.PaymentStatus;
import com.rentcar.api.exception.InvalidBookingStateException;
import com.rentcar.api.exception.PaymentNotFoundException;
import com.rentcar.api.exception.RefundFailedException;
import com.rentcar.api.payment.model.PaymentIntentResult;
import com.rentcar.api.payment.model.PaymentResult;
import com.rentcar.api.payment.provider.PaymentProvider;
import com.rentcar.api.repository.PaymentRepository;
import com.rentcar.api.util.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentProvider paymentProvider;
    private final AppClock appClock;

    @Transactional
    public Payment createPendingPayment(Booking booking) {
        Payment saved = paymentRepository.save(buildPendingPayment(booking));
        log.debug("Pending payment created: paymentId={} bookingId={} amount={}",
                saved.getId(), booking.getId(), booking.getTotalPrice());
        return saved;
    }

    public List<Payment> getPayments() {
        return paymentRepository.findAll();
    }

    /**
     * Returns the full payment history for a booking, newest first.
     *
     * <p>A booking may accumulate multiple payment records over its lifetime
     * (e.g. one FAILED attempt followed by one PAID record after retry).
     * All records are preserved for audit — none are deleted on retry.
     */
    public List<Payment> getPaymentsForBooking(Booking booking) {
        return paymentRepository.findAllByBookingOrderByCreatedAtDescIdDesc(booking);
    }

    @Transactional
    public void handleCancellationPayment(Booking booking) {
        Payment payment = getLatestPaymentForBooking(booking);

        if (payment.getStatus() == PaymentStatus.PAID) {
            // Money was collected — issue a full refund before cancelling.
            //
            // TODO (async): with real Stripe, call stripe.refunds.create() here and set
            //   payment.setStatus(PaymentStatus.REFUND_PENDING) instead of REFUNDED.
            //   A PaymentWebhookHandler will then advance REFUND_PENDING → REFUNDED
            //   when Stripe sends the 'charge.refunded' event.
            //   FakePaymentProvider skips REFUND_PENDING and returns successful immediately.
            PaymentResult result = paymentProvider.refund(payment);
            if (!result.successful()) {
                log.warn("Refund failed for paymentId={} bookingId={} — manual intervention required",
                        payment.getId(), booking.getId());
                // Throw so the transaction rolls back: booking stays un-cancelled
                // until the refund issue is resolved manually.
                throw new RefundFailedException(payment.getId());
            }
            payment.setStatus(PaymentStatus.REFUNDED);
            log.info("Payment refunded: paymentId={} bookingId={}", payment.getId(), booking.getId());
        } else {
            // PENDING or FAILED — no money was collected, just void the record.
            payment.setStatus(PaymentStatus.CANCELLED);
            log.info("Payment voided (no charge): paymentId={} bookingId={}", payment.getId(), booking.getId());
        }

        paymentRepository.save(payment);
    }

    @Transactional
    public Payment processLatestPaymentForBooking(Booking booking, String paymentMethodId) {
        Payment payment = getLatestPaymentForBooking(booking);

        // Guard: if a previous attempt already succeeded, do not charge again.
        if (payment.getStatus() == PaymentStatus.PAID) {
            log.warn("Double-payment attempt blocked: bookingId={} paymentId={}", booking.getId(), payment.getId());
            throw new InvalidBookingStateException(
                    "Booking " + booking.getId() + " has already been paid (payment " + payment.getId() + ")");
        }

        PaymentResult result = paymentProvider.pay(payment, paymentMethodId);

        payment.setProviderReference(result.providerReference());

        if (result.successful()) {
            // TODO (async): with real Stripe, the synchronous charge call returns 'requires_action'
            //   or 'processing' for many payment methods (3DS, SEPA, etc.).
            //   A real implementation should set PENDING here and advance to PAID only when
            //   Stripe sends the 'payment_intent.succeeded' webhook event.
            //   FakePaymentProvider resolves synchronously — PAID immediately.
            payment.setStatus(PaymentStatus.PAID);
            payment.setPaidAt(appClock.nowUtc());
            log.info("Payment succeeded: paymentId={} bookingId={} ref={}",
                    payment.getId(), booking.getId(), result.providerReference());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            log.warn("Payment failed: paymentId={} bookingId={}", payment.getId(), booking.getId());
        }

        return paymentRepository.save(payment);
    }

    public Optional<Payment> findLatestPayment(Booking booking) {
        return paymentRepository.findTopByBookingOrderByCreatedAtDescIdDesc(booking);
    }

    /**
     * Creates a payment intent with the configured provider without charging the customer.
     * Used by {@code POST /api/bookings/{id}/payments/intent}.
     */
    public PaymentIntentResult createIntentForPayment(Payment payment) {
        return paymentProvider.createIntent(payment);
    }

    /**
     * Persist a payment record. Public helper used by higher-level services that
     * orchestrate provider operations and need to save provider-side ids.
     */
    public Payment save(Payment payment) {
        return paymentRepository.save(payment);
    }

    /** Short provider name for API responses (e.g. {@code "FAKE"}, {@code "STRIPE"}). */
    public String providerName() {
        return paymentProvider.providerName();
    }

    private Payment getLatestPaymentForBooking(Booking booking) {
        return paymentRepository.findTopByBookingOrderByCreatedAtDescIdDesc(booking)
                .orElseThrow(() -> new PaymentNotFoundException(booking.getId()));
    }

    private Payment buildPendingPayment(Booking booking) {
        return Payment.builder()
                .booking(booking)
                .amount(booking.getTotalPrice())
                .currencyCode("EUR")
                .method(PaymentMethod.CARD)
                .channel(PaymentChannel.ONLINE)
                .status(PaymentStatus.PENDING)
                .build();
    }
}