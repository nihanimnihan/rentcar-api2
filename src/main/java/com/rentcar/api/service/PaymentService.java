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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentProvider paymentProvider;
    private final AppClock appClock;

    @Transactional
    public void createPendingPayment(Booking booking) {
        paymentRepository.save(buildPendingPayment(booking));
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
                // Throw so the transaction rolls back: booking stays un-cancelled
                // until the refund issue is resolved manually.
                throw new RefundFailedException(payment.getId());
            }
            payment.setStatus(PaymentStatus.REFUNDED);
        } else {
            // PENDING or FAILED — no money was collected, just void the record.
            payment.setStatus(PaymentStatus.CANCELLED);
        }

        paymentRepository.save(payment);
    }

    @Transactional
    public Payment processLatestPaymentForBooking(Booking booking, String paymentMethodId) {
        Payment payment = getLatestPaymentForBooking(booking);

        // Guard: if a previous attempt already succeeded, do not charge again.
        if (payment.getStatus() == PaymentStatus.PAID) {
            throw new InvalidBookingStateException(
                    "Booking " + booking.getId() + " has already been paid (payment " + payment.getId() + ")");
        }

        PaymentResult result = paymentProvider.pay(payment, paymentMethodId);

        payment.setProviderReference(result.providerReference());

        if (result.successful()) {
            payment.setStatus(PaymentStatus.PAID);
            payment.setPaidAt(appClock.nowUtc());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
        }

        return paymentRepository.save(payment);
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