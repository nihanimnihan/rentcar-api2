package com.rentcar.api;

import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.domain.booking.BookingStatus;
import com.rentcar.api.domain.payment.Payment;
import com.rentcar.api.domain.payment.PaymentChannel;
import com.rentcar.api.domain.payment.PaymentMethod;
import com.rentcar.api.domain.payment.PaymentStatus;
import com.rentcar.api.exception.InvalidBookingStateException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceStripeVerificationTest {

    private PaymentRepository paymentRepository;
    private PaymentProvider paymentProvider;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        paymentProvider = mock(PaymentProvider.class);
        AppClock appClock = mock(AppClock.class);
        when(appClock.nowUtc()).thenReturn(Instant.parse("2026-06-21T12:00:00Z"));
        paymentService = new PaymentService(
                paymentRepository,
                paymentProvider,
                appClock,
                mock(BookingEmailNotificationService.class));
    }

    @Test
    void verifyPaymentIntent_succeededMatchingIntent_marksPaymentPaid() {
        Payment payment = payment("pi_match", BigDecimal.valueOf(125.50), 42L, 7L);
        when(paymentRepository.findTopByBookingOrderByCreatedAtDescIdDesc(payment.getBooking()))
                .thenReturn(Optional.of(payment));
        when(paymentProvider.fetchPaymentIntent(payment))
                .thenReturn(intent(payment, "succeeded", 12550L, "eur", 42L, 7L));
        when(paymentRepository.save(payment)).thenReturn(payment);

        Payment saved = paymentService.verifyLatestPaymentIntentForBooking(payment.getBooking());

        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(saved.getPaidAt()).isEqualTo(Instant.parse("2026-06-21T12:00:00Z"));
    }

    @Test
    void verifyPaymentIntent_missingProviderIntentId_rejects() {
        Payment payment = payment(null, BigDecimal.valueOf(125.50), 42L, 7L);
        when(paymentRepository.findTopByBookingOrderByCreatedAtDescIdDesc(payment.getBooking()))
                .thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.verifyLatestPaymentIntentForBooking(payment.getBooking()))
                .isInstanceOf(InvalidBookingStateException.class)
                .hasMessageContaining("PaymentIntent");

        verify(paymentProvider, never()).fetchPaymentIntent(payment);
    }

    @Test
    void verifyPaymentIntent_unsucceededIntent_rejectsWithoutMarkingPaid() {
        Payment payment = payment("pi_processing", BigDecimal.valueOf(125.50), 42L, 7L);
        when(paymentRepository.findTopByBookingOrderByCreatedAtDescIdDesc(payment.getBooking()))
                .thenReturn(Optional.of(payment));
        when(paymentProvider.fetchPaymentIntent(payment))
                .thenReturn(intent(payment, "requires_payment_method", 12550L, "eur", 42L, 7L));

        assertThatThrownBy(() -> paymentService.verifyLatestPaymentIntentForBooking(payment.getBooking()))
                .isInstanceOf(InvalidBookingStateException.class)
                .hasMessageContaining("has not succeeded");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentRepository, never()).save(payment);
    }

    @Test
    void verifyPaymentIntent_amountMismatch_rejects() {
        Payment payment = payment("pi_wrong_amount", BigDecimal.valueOf(125.50), 42L, 7L);
        when(paymentRepository.findTopByBookingOrderByCreatedAtDescIdDesc(payment.getBooking()))
                .thenReturn(Optional.of(payment));
        when(paymentProvider.fetchPaymentIntent(payment))
                .thenReturn(intent(payment, "succeeded", 999L, "eur", 42L, 7L));

        assertThatThrownBy(() -> paymentService.verifyLatestPaymentIntentForBooking(payment.getBooking()))
                .isInstanceOf(InvalidBookingStateException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void verifyPaymentIntent_bookingMismatch_rejects() {
        Payment payment = payment("pi_wrong_booking", BigDecimal.valueOf(125.50), 42L, 7L);
        when(paymentRepository.findTopByBookingOrderByCreatedAtDescIdDesc(payment.getBooking()))
                .thenReturn(Optional.of(payment));
        when(paymentProvider.fetchPaymentIntent(payment))
                .thenReturn(intent(payment, "succeeded", 12550L, "eur", 99L, 7L));

        assertThatThrownBy(() -> paymentService.verifyLatestPaymentIntentForBooking(payment.getBooking()))
                .isInstanceOf(InvalidBookingStateException.class)
                .hasMessageContaining("booking metadata");
    }

    @Test
    void verifyPaymentIntent_paymentMismatch_rejects() {
        Payment payment = payment("pi_wrong_payment", BigDecimal.valueOf(125.50), 42L, 7L);
        when(paymentRepository.findTopByBookingOrderByCreatedAtDescIdDesc(payment.getBooking()))
                .thenReturn(Optional.of(payment));
        when(paymentProvider.fetchPaymentIntent(payment))
                .thenReturn(intent(payment, "succeeded", 12550L, "eur", 42L, 8L));

        assertThatThrownBy(() -> paymentService.verifyLatestPaymentIntentForBooking(payment.getBooking()))
                .isInstanceOf(InvalidBookingStateException.class)
                .hasMessageContaining("payment metadata");
    }

    @Test
    void handleCancellationPayment_paidCardWithoutRealStripeReference_doesNotCreateFakeRefund() {
        Payment payment = payment("fake_intent_7", BigDecimal.valueOf(125.50), 42L, 7L);
        payment.setStatus(PaymentStatus.PAID);
        payment.setProviderReference("FAKE-7");
        when(paymentRepository.findTopByBookingOrderByCreatedAtDescIdDesc(payment.getBooking()))
                .thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);

        paymentService.handleCancellationPayment(payment.getBooking());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
        verify(paymentProvider, never()).refund(payment);
    }

    private Payment payment(String intentId, BigDecimal amount, Long bookingId, Long paymentId) {
        Booking booking = Booking.builder()
                .id(bookingId)
                .bookingReference("RC-260621-TEST")
                .status(BookingStatus.PENDING)
                .build();

        return Payment.builder()
                .id(paymentId)
                .booking(booking)
                .amount(amount)
                .currencyCode("EUR")
                .method(PaymentMethod.CARD)
                .channel(PaymentChannel.ONLINE)
                .status(PaymentStatus.PENDING)
                .stripePaymentIntentId(intentId)
                .paymentReference("PAY-TEST123")
                .build();
    }

    private PaymentIntentVerification intent(
            Payment payment,
            String status,
            long amountMinor,
            String currency,
            long bookingId,
            long paymentId) {

        return new PaymentIntentVerification(
                payment.getStripePaymentIntentId(),
                status,
                amountMinor,
                currency,
                Map.of(
                        "bookingId", String.valueOf(bookingId),
                        "paymentId", String.valueOf(paymentId),
                        "paymentReference", payment.getPaymentReference()
                )
        );
    }
}
