package com.rentcar.api.service;

import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.domain.payment.Payment;
import com.rentcar.api.domain.payment.PaymentChannel;
import com.rentcar.api.domain.payment.PaymentMethod;
import com.rentcar.api.domain.payment.PaymentStatus;
import com.rentcar.api.exception.PaymentNotFoundException;
import com.rentcar.api.payment.model.PaymentResult;
import com.rentcar.api.payment.provider.PaymentProvider;
import com.rentcar.api.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentProvider paymentProvider;

    @Transactional
    public void createPendingOnlinePayment(Booking savedBooking) {
        Payment payment = Payment.builder()
                .booking(savedBooking)
                .amount(savedBooking.getTotalPrice())
                .currencyCode("EUR")
                .method(PaymentMethod.CARD)
                .channel(PaymentChannel.ONLINE)
                .status(PaymentStatus.PENDING)
                .build();

        paymentRepository.save(payment);
    }

    public List<Payment> getPayments() {
        return paymentRepository.findAll();
    }

    @Transactional
    public void cancelPaymentForBooking(Booking booking) {
        Payment payment = getLatestPaymentForBooking(booking);
        payment.setStatus(PaymentStatus.CANCELLED);
        paymentRepository.save(payment);
    }

    @Transactional
    public Payment processLatestPaymentForBooking(Booking booking) {
        Payment payment = getLatestPaymentForBooking(booking);

        PaymentResult result = paymentProvider.pay(payment);

        payment.setProviderReference(result.providerReference());

        if (result.successful()) {
            payment.setStatus(PaymentStatus.PAID);
            payment.setPaidAt(LocalDateTime.now());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
        }

        return paymentRepository.save(payment);
    }

    private Payment getLatestPaymentForBooking(Booking booking) {
        return paymentRepository.findTopByBookingOrderByCreatedAtDesc(booking)
                .orElseThrow(() -> new PaymentNotFoundException(booking.getId()));
    }
}