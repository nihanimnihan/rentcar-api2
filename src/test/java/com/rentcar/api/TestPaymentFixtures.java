package com.rentcar.api;

import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.domain.booking.BookingStatus;
import com.rentcar.api.domain.payment.Payment;
import com.rentcar.api.domain.payment.PaymentStatus;
import com.rentcar.api.payment.model.PaymentIntentResult;
import com.rentcar.api.payment.model.PaymentIntentVerification;
import com.rentcar.api.payment.provider.PaymentProvider;
import com.rentcar.api.repository.BookingRepository;
import com.rentcar.api.repository.PaymentRepository;
import com.rentcar.api.service.PaymentService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

final class TestPaymentFixtures {

    private TestPaymentFixtures() {
    }

    static Payment confirmByVerifiedStripeWebhook(
            BookingRepository bookingRepository,
            PaymentService paymentService,
            long bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        Payment payment = paymentService.findLatestPayment(booking).orElseThrow();
        if (payment.getStripePaymentIntentId() == null || payment.getStripePaymentIntentId().isBlank()) {
            payment.setStripePaymentIntentId("pi_test_" + payment.getId());
            payment = paymentService.save(payment);
        }
        return paymentService.applyStripePaymentIntentStatus(
                intent(payment, "succeeded"),
                "ch_test_" + payment.getId()
        ).orElseThrow();
    }

    static Payment failByVerifiedStripeWebhook(
            BookingRepository bookingRepository,
            PaymentService paymentService,
            long bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        Payment payment = paymentService.findLatestPayment(booking).orElseThrow();
        if (payment.getStripePaymentIntentId() == null || payment.getStripePaymentIntentId().isBlank()) {
            payment.setStripePaymentIntentId("pi_test_" + payment.getId());
            payment = paymentService.save(payment);
        }
        return paymentService.applyStripePaymentIntentStatus(
                intent(payment, "requires_payment_method"),
                null
        ).orElseThrow();
    }

    static void markConfirmedPaidWithoutStripeReference(
            BookingRepository bookingRepository,
            PaymentRepository paymentRepository,
            long bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setExpiresAt(null);
        booking.setCheckoutSessionToken(null);
        bookingRepository.saveAndFlush(booking);

        Payment payment = paymentRepository.findTopByBookingOrderByCreatedAtDescIdDesc(booking).orElseThrow();
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(Instant.now());
        payment.setProviderReference("TEST-OFFLINE-PAID");
        payment.setStripePaymentIntentId(null);
        paymentRepository.saveAndFlush(payment);
    }

    static void markFailedWithoutStripeReference(
            BookingRepository bookingRepository,
            PaymentRepository paymentRepository,
            long bookingId) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        booking.setStatus(BookingStatus.FAILED);
        bookingRepository.saveAndFlush(booking);

        Payment payment = paymentRepository.findTopByBookingOrderByCreatedAtDescIdDesc(booking).orElseThrow();
        payment.setStatus(PaymentStatus.FAILED);
        payment.setProviderReference("TEST-FAILED");
        payment.setStripePaymentIntentId(null);
        paymentRepository.saveAndFlush(payment);
    }

    static PaymentIntentVerification intent(Payment payment, String status) {
        return new PaymentIntentVerification(
                payment.getStripePaymentIntentId(),
                status,
                amountMinor(payment.getAmount()),
                payment.getCurrencyCode().toLowerCase(java.util.Locale.ROOT),
                Map.of(
                        "bookingId", String.valueOf(payment.getBooking().getId()),
                        "paymentId", String.valueOf(payment.getId()),
                        "paymentReference", payment.getPaymentReference()
                )
        );
    }

    static void configureStripeIntentProvider(PaymentProvider paymentProvider) {
        when(paymentProvider.providerName()).thenReturn("STRIPE");
        when(paymentProvider.createIntent(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            return new PaymentIntentResult(
                    "STRIPE",
                    "pi_test_secret_" + payment.getId(),
                    "pi_test_" + payment.getId());
        });
    }

    private static long amountMinor(BigDecimal amount) {
        return amount.setScale(2, java.math.RoundingMode.UNNECESSARY)
                .movePointRight(2)
                .longValueExact();
    }
}
