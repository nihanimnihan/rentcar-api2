package com.rentcar.api.service;

import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.domain.booking.BookingStatus;
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
    private final BookingEmailNotificationService bookingEmailNotificationService;

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
            // Money was collected — issue a full provider refund before cancelling.
            PaymentResult result = paymentProvider.refund(payment);
            if (result.successful()) {
                payment.setProviderReference(result.providerReference());
                payment.setStatus(PaymentStatus.REFUNDED);
                log.info("Payment refunded: paymentId={} bookingId={} providerRef={}",
                        payment.getId(), booking.getId(), result.providerReference());
            } else if (isPendingRefundStatus(result.providerStatus())) {
                payment.setStatus(PaymentStatus.REFUND_PENDING);
                payment.setProviderReference(result.providerReference());
                log.info("Payment refund pending: paymentId={} bookingId={} providerRef={} providerStatus={}",
                        payment.getId(), booking.getId(), result.providerReference(), result.providerStatus());
            } else {
                log.warn("Refund failed for paymentId={} bookingId={} — manual intervention required",
                        payment.getId(), booking.getId());
                // Throw so the transaction rolls back: booking stays un-cancelled
                // until the refund issue is resolved manually.
                throw new RefundFailedException(payment.getId());
            }
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
            payment.setStatus(PaymentStatus.PAID);
            payment.setPaidAt(appClock.nowUtc());
            log.info("Payment succeeded: paymentId={} bookingId={} ref={}",
                    payment.getId(), booking.getId(), result.providerReference());
        } else if (isPendingPaymentIntentStatus(result.providerStatus())) {
            payment.setStatus(PaymentStatus.PENDING);
            log.info("Payment still pending: paymentId={} bookingId={} providerStatus={}",
                    payment.getId(), booking.getId(), result.providerStatus());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            log.warn("Payment failed: paymentId={} bookingId={}", payment.getId(), booking.getId());
        }

        return paymentRepository.save(payment);
    }

    /**
     * Verifies the latest provider-side PaymentIntent (Stripe) for the booking and
     * updates local Payment record accordingly.
     *
     * Mapping:
     *  - Stripe 'succeeded' => PaymentStatus.PAID
     *  - Stripe 'requires_payment_method'|'canceled'|'failed' => PaymentStatus.FAILED
     *  - Stripe 'processing'|'requires_action'|'requires_confirmation'|'requires_capture' => keep PENDING
     */
    @Transactional
    public Payment verifyLatestPaymentIntentForBooking(Booking booking) {
        Payment payment = getLatestPaymentForBooking(booking);

        String intentStatus;
        try {
            intentStatus = paymentProvider.fetchPaymentIntentStatus(payment);
        } catch (UnsupportedOperationException e) {
            throw new IllegalStateException("Payment provider does not support intent verification");
        }

        if (intentStatus == null) {
            throw new IllegalStateException("Provider returned null intent status");
        }

        switch (intentStatus) {
            case "succeeded" -> markPaymentPaid(payment, intentStatus);
            case "requires_payment_method", "canceled", "failed" -> markPaymentFailed(payment, intentStatus);
            default -> markPaymentPending(payment, intentStatus);
        }

        return paymentRepository.save(payment);
    }

    /**
     * Applies a Stripe PaymentIntent webhook update to the local payment and booking.
     * Idempotent: replaying the same Stripe event leaves the same final state.
     */
    @Transactional
    public Optional<Payment> applyStripePaymentIntentStatus(
            String stripePaymentIntentId,
            String providerStatus,
            String providerReference) {

        return paymentRepository.findByStripePaymentIntentIdForUpdate(stripePaymentIntentId)
                .map(payment -> {
                    BookingStatus previousBookingStatus = payment.getBooking().getStatus();
                    if (providerReference != null && !providerReference.isBlank()) {
                        payment.setProviderReference(providerReference);
                    }
                    switch (providerStatus) {
                        case "succeeded" -> {
                            markPaymentPaid(payment, providerStatus);
                            confirmBookingIfOpen(payment.getBooking());
                        }
                        case "requires_payment_method", "canceled", "failed" -> {
                            markPaymentFailed(payment, providerStatus);
                            failBookingIfOpen(payment.getBooking());
                        }
                        default -> {
                            markPaymentPending(payment, providerStatus);
                            keepBookingPendingIfOpen(payment.getBooking());
                        }
                    }
                    Payment saved = paymentRepository.save(payment);
                    if ("succeeded".equals(providerStatus)
                            && previousBookingStatus != BookingStatus.CONFIRMED
                            && saved.getBooking().getStatus() == BookingStatus.CONFIRMED) {
                        bookingEmailNotificationService.sendBookingConfirmation(saved.getBooking());
                    }
                    return saved;
                });
    }

    /**
     * Applies Stripe refund webhook updates. A successful refund is final; pending
     * states are preserved for operations visibility instead of being treated as a failure.
     */
    @Transactional
    public Optional<Payment> applyStripeRefundStatus(
            String stripePaymentIntentId,
            String refundId,
            String refundStatus) {

        return paymentRepository.findByStripePaymentIntentIdForUpdate(stripePaymentIntentId)
                .map(payment -> {
                    PaymentStatus previousStatus = payment.getStatus();
                    if (refundId != null && !refundId.isBlank()) {
                        payment.setProviderReference(refundId);
                    }
                    if ("succeeded".equals(refundStatus)) {
                        payment.setStatus(PaymentStatus.REFUNDED);
                        log.info("Stripe refund succeeded: paymentId={} bookingId={} refundId={}",
                                payment.getId(), payment.getBooking().getId(), refundId);
                    } else if (isPendingRefundStatus(refundStatus)) {
                        payment.setStatus(PaymentStatus.REFUND_PENDING);
                        log.info("Stripe refund pending: paymentId={} bookingId={} refundId={} status={}",
                                payment.getId(), payment.getBooking().getId(), refundId, refundStatus);
                    } else {
                        log.warn("Stripe refund status needs manual review: paymentId={} bookingId={} refundId={} status={}",
                                payment.getId(), payment.getBooking().getId(), refundId, refundStatus);
                    }
                    Payment saved = paymentRepository.save(payment);
                    if ("succeeded".equals(refundStatus)
                            && previousStatus != PaymentStatus.REFUNDED
                            && saved.getStatus() == PaymentStatus.REFUNDED) {
                        bookingEmailNotificationService.sendRefundCompleted(saved.getBooking(), saved);
                    }
                    return saved;
                });
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

    private void markPaymentPaid(Payment payment, String providerStatus) {
        payment.setStatus(PaymentStatus.PAID);
        if (payment.getPaidAt() == null) {
            payment.setPaidAt(appClock.nowUtc());
        }
        log.info("Payment intent succeeded: paymentId={} bookingId={} providerStatus={}",
                payment.getId(), payment.getBooking().getId(), providerStatus);
    }

    private void markPaymentFailed(Payment payment, String providerStatus) {
        payment.setStatus(PaymentStatus.FAILED);
        log.info("Payment intent failed: paymentId={} bookingId={} providerStatus={}",
                payment.getId(), payment.getBooking().getId(), providerStatus);
    }

    private void markPaymentPending(Payment payment, String providerStatus) {
        payment.setStatus(PaymentStatus.PENDING);
        log.info("Payment intent pending: paymentId={} bookingId={} providerStatus={}",
                payment.getId(), payment.getBooking().getId(), providerStatus);
    }

    private void confirmBookingIfOpen(Booking booking) {
        if (booking.getStatus() == BookingStatus.PENDING || booking.getStatus() == BookingStatus.FAILED) {
            booking.setStatus(BookingStatus.CONFIRMED);
            booking.setExpiresAt(null);
            booking.setCheckoutSessionToken(null);
        }
    }

    private void failBookingIfOpen(Booking booking) {
        if (booking.getStatus() == BookingStatus.PENDING || booking.getStatus() == BookingStatus.FAILED) {
            booking.setStatus(BookingStatus.FAILED);
        }
    }

    private void keepBookingPendingIfOpen(Booking booking) {
        if (booking.getStatus() == BookingStatus.FAILED) {
            booking.setStatus(BookingStatus.PENDING);
        }
    }

    private boolean isPendingPaymentIntentStatus(String providerStatus) {
        return switch (providerStatus == null ? "" : providerStatus) {
            case "processing", "requires_action", "requires_confirmation", "requires_capture" -> true;
            default -> false;
        };
    }

    private boolean isPendingRefundStatus(String providerStatus) {
        return switch (providerStatus == null ? "" : providerStatus) {
            case "pending", "requires_action" -> true;
            default -> false;
        };
    }
}
