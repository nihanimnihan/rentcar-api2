package com.rentcar.api;

import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.domain.booking.BookingStatus;
import com.rentcar.api.domain.payment.Payment;
import com.rentcar.api.domain.payment.PaymentChannel;
import com.rentcar.api.domain.payment.PaymentMethod;
import com.rentcar.api.domain.payment.PaymentStatus;
import com.rentcar.api.payment.model.PaymentIntentVerification;
import com.rentcar.api.payment.provider.PaymentProvider;
import com.rentcar.api.repository.PaymentRepository;
import com.rentcar.api.service.BookingEmailNotificationService;
import com.rentcar.api.service.PaymentService;
import com.rentcar.api.util.AppClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentWebhookLockingTest {

    private PaymentRepository paymentRepository;
    private BookingEmailNotificationService bookingEmailNotificationService;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        PaymentProvider paymentProvider = mock(PaymentProvider.class);
        AppClock appClock = mock(AppClock.class);
        bookingEmailNotificationService = mock(BookingEmailNotificationService.class);
        paymentService = new PaymentService(
                paymentRepository,
                paymentProvider,
                appClock,
                bookingEmailNotificationService);
        when(appClock.nowUtc()).thenReturn(Instant.parse("2026-06-19T12:00:00Z"));
    }

    @Test
    void paymentIntentWebhook_usesLockedPaymentLookupBeforeSendingConfirmation() {
        Payment payment = payment(BookingStatus.PENDING, PaymentStatus.PENDING);
        when(paymentRepository.findByStripePaymentIntentIdForUpdate("pi_test_123"))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);

        paymentService.applyStripePaymentIntentStatus(
                intent(payment, "succeeded"),
                "ch_test_123");

        verify(paymentRepository).findByStripePaymentIntentIdForUpdate("pi_test_123");
        verify(paymentRepository, never()).findByStripePaymentIntentId("pi_test_123");
        verify(bookingEmailNotificationService).sendBookingConfirmation(payment.getBooking());
    }

    @Test
    void refundWebhook_usesLockedPaymentLookupBeforeSendingRefundCompleted() {
        Payment payment = payment(BookingStatus.CANCELLED, PaymentStatus.REFUND_PENDING);
        when(paymentRepository.findByStripePaymentIntentIdForUpdate("pi_test_123"))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);

        paymentService.applyStripeRefundStatus("pi_test_123", "re_test_123", "succeeded");

        verify(paymentRepository).findByStripePaymentIntentIdForUpdate("pi_test_123");
        verify(paymentRepository, never()).findByStripePaymentIntentId("pi_test_123");
        verify(bookingEmailNotificationService).sendRefundCompleted(payment.getBooking(), payment);
    }

    private Payment payment(BookingStatus bookingStatus, PaymentStatus paymentStatus) {
        Booking booking = Booking.builder()
                .id(42L)
                .bookingReference("RC-260619-TEST")
                .status(bookingStatus)
                .build();

        return Payment.builder()
                .id(7L)
                .booking(booking)
                .amount(BigDecimal.valueOf(95))
                .currencyCode("EUR")
                .method(PaymentMethod.CARD)
                .channel(PaymentChannel.ONLINE)
                .status(paymentStatus)
                .stripePaymentIntentId("pi_test_123")
                .paymentReference("PAY-LOCK123")
                .build();
    }

    private PaymentIntentVerification intent(Payment payment, String status) {
        return new PaymentIntentVerification(
                payment.getStripePaymentIntentId(),
                status,
                9500L,
                "eur",
                Map.of(
                        "bookingId", String.valueOf(payment.getBooking().getId()),
                        "paymentId", String.valueOf(payment.getId()),
                        "paymentReference", payment.getPaymentReference()
                )
        );
    }
}
