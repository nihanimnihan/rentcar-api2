package com.rentcar.api.service;

import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.domain.payment.Payment;
import com.rentcar.api.domain.payment.PaymentChannel;
import com.rentcar.api.domain.payment.PaymentMethod;
import com.rentcar.api.domain.payment.PaymentStatus;
import com.rentcar.api.exception.InvalidBookingStateException;
import com.rentcar.api.exception.PaymentNotFoundException;
import com.rentcar.api.exception.RefundFailedException;
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
    public void createPendingPayment(Booking booking) {
        Payment saved = paymentRepository.save(buildPendingPayment(booking));
        log.debug("Pending payment created: paymentId={} bookingId={} amount={}",
                saved.getId(), booking.getId(), booking.getTotalPrice());
    }

    public List<Payment> getPayments() {
        return paymentRepository.findAll();
    }

    @Transactional
    public void cancelPaymentForBooking(Booking booking) {
        Payment payment = getLatestPaymentForBooking(booking);

        if (payment.getStatus() == PaymentStatus.PAID) {
            // Money was collected — issue a full refund before cancelling.
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
        return paymentRepository.findTopByBookingOrderByCreatedAtDesc(booking);
    }

    private Payment getLatestPaymentForBooking(Booking booking) {
        return paymentRepository.findTopByBookingOrderByCreatedAtDesc(booking)
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