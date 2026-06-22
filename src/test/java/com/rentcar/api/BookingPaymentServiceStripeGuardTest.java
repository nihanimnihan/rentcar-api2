package com.rentcar.api;

import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.domain.booking.BookingStatus;
import com.rentcar.api.domain.payment.Payment;
import com.rentcar.api.domain.payment.PaymentChannel;
import com.rentcar.api.domain.payment.PaymentMethod;
import com.rentcar.api.domain.payment.PaymentStatus;
import com.rentcar.api.dto.payment.CreatePaymentIntentRequest;
import com.rentcar.api.exception.PaymentProviderNotConfiguredException;
import com.rentcar.api.exception.InvalidBookingStateException;
import com.rentcar.api.payment.model.PaymentIntentResult;
import com.rentcar.api.repository.BookingRepository;
import com.rentcar.api.service.BookingEmailNotificationService;
import com.rentcar.api.service.BookingPaymentService;
import com.rentcar.api.service.PaymentService;
import com.rentcar.api.util.AppClock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingPaymentServiceStripeGuardTest {

    @Test
    void completePayment_stripeProviderWithoutIntent_rejectsInsteadOfChargingSynchronously() {
        Booking booking = Booking.builder()
                .id(42L)
                .status(BookingStatus.PENDING)
                .build();
        Payment payment = Payment.builder()
                .id(7L)
                .booking(booking)
                .amount(BigDecimal.valueOf(99))
                .currencyCode("EUR")
                .method(PaymentMethod.CARD)
                .channel(PaymentChannel.ONLINE)
                .status(PaymentStatus.PENDING)
                .build();
        BookingRepository bookingRepository = mock(BookingRepository.class);
        PaymentService paymentService = mock(PaymentService.class);
        BookingPaymentService service = new BookingPaymentService(
                bookingRepository,
                paymentService,
                mock(BookingEmailNotificationService.class),
                mock(AppClock.class));

        when(bookingRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(booking));
        when(paymentService.findLatestPayment(booking)).thenReturn(Optional.of(payment));
        when(paymentService.providerName()).thenReturn("STRIPE");

        assertThatThrownBy(() -> service.completePayment(42L, "pm_test_123", null))
                .isInstanceOf(InvalidBookingStateException.class)
                .hasMessageContaining("PaymentIntent");

        verify(paymentService, never()).processLatestPaymentForBooking(booking, "pm_test_123");
    }

    @Test
    void createPaymentIntent_stripeProviderCreatesStripeIntentAndStoresProviderIntentId() {
        Instant now = Instant.parse("2026-06-22T10:00:00Z");
        Booking booking = pendingBooking(now);
        Payment payment = pendingPayment(booking);
        BookingRepository bookingRepository = mock(BookingRepository.class);
        PaymentService paymentService = mock(PaymentService.class);
        AppClock appClock = mock(AppClock.class);
        BookingPaymentService service = new BookingPaymentService(
                bookingRepository,
                paymentService,
                mock(BookingEmailNotificationService.class),
                appClock);

        when(appClock.nowUtc()).thenReturn(now);
        when(bookingRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(booking));
        when(paymentService.providerName()).thenReturn("STRIPE");
        when(paymentService.findLatestPayment(booking)).thenReturn(Optional.of(payment));
        when(paymentService.createIntentForPayment(payment))
                .thenReturn(new PaymentIntentResult("STRIPE", "pi_test_secret_123", "pi_test_123"));
        when(paymentService.save(payment)).thenReturn(payment);

        var response = service.createPaymentIntent(42L, new CreatePaymentIntentRequest(null, "checkout-token"));

        assertThat(response.provider()).isEqualTo("STRIPE");
        assertThat(response.clientSecret()).isEqualTo("pi_test_secret_123");
        assertThat(payment.getStripePaymentIntentId()).isEqualTo("pi_test_123");
        verify(paymentService).createIntentForPayment(payment);
        verify(paymentService).save(payment);
    }

    @Test
    void createPaymentIntent_fakeProviderFailsClosedBeforeCreatingSyntheticIntent() {
        Instant now = Instant.parse("2026-06-22T10:00:00Z");
        Booking booking = pendingBooking(now);
        Payment payment = pendingPayment(booking);
        BookingRepository bookingRepository = mock(BookingRepository.class);
        PaymentService paymentService = mock(PaymentService.class);
        AppClock appClock = mock(AppClock.class);
        BookingPaymentService service = new BookingPaymentService(
                bookingRepository,
                paymentService,
                mock(BookingEmailNotificationService.class),
                appClock);

        when(appClock.nowUtc()).thenReturn(now);
        when(bookingRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(booking));
        when(paymentService.providerName()).thenReturn("FAKE");
        when(paymentService.findLatestPayment(booking)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.createPaymentIntent(42L,
                new CreatePaymentIntentRequest(null, "checkout-token")))
                .isInstanceOf(PaymentProviderNotConfiguredException.class)
                .hasMessageContaining("Stripe payment provider");

        verify(paymentService, never()).createIntentForPayment(payment);
        verify(paymentService, never()).save(payment);
    }

    @Test
    void createPaymentIntent_missingStripeConfigPropagatesProviderUnavailable() {
        Instant now = Instant.parse("2026-06-22T10:00:00Z");
        Booking booking = pendingBooking(now);
        Payment payment = pendingPayment(booking);
        BookingRepository bookingRepository = mock(BookingRepository.class);
        PaymentService paymentService = mock(PaymentService.class);
        AppClock appClock = mock(AppClock.class);
        BookingPaymentService service = new BookingPaymentService(
                bookingRepository,
                paymentService,
                mock(BookingEmailNotificationService.class),
                appClock);

        when(appClock.nowUtc()).thenReturn(now);
        when(bookingRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(booking));
        when(paymentService.providerName()).thenReturn("STRIPE");
        when(paymentService.findLatestPayment(booking)).thenReturn(Optional.of(payment));
        when(paymentService.createIntentForPayment(payment))
                .thenThrow(new PaymentProviderNotConfiguredException("Stripe"));

        assertThatThrownBy(() -> service.createPaymentIntent(42L,
                new CreatePaymentIntentRequest(null, "checkout-token")))
                .isInstanceOf(PaymentProviderNotConfiguredException.class);

        verify(paymentService).createIntentForPayment(payment);
        verify(paymentService, never()).save(payment);
    }

    private Booking pendingBooking(Instant now) {
        return Booking.builder()
                .id(42L)
                .bookingReference("RC-TEST")
                .status(BookingStatus.PENDING)
                .checkoutSessionToken("checkout-token")
                .expiresAt(now.plusSeconds(900))
                .build();
    }

    private Payment pendingPayment(Booking booking) {
        Payment payment = Payment.builder()
                .id(7L)
                .booking(booking)
                .amount(BigDecimal.valueOf(99))
                .currencyCode("EUR")
                .method(PaymentMethod.CARD)
                .channel(PaymentChannel.ONLINE)
                .status(PaymentStatus.PENDING)
                .build();
        payment.setPaymentReference("PAY-TEST123");
        return payment;
    }
}
