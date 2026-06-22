package com.rentcar.api.service;

import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.domain.booking.BookingStatus;
import com.rentcar.api.domain.payment.Payment;
import com.rentcar.api.domain.payment.PaymentStatus;
import com.rentcar.api.dto.payment.CreatePaymentIntentRequest;
import com.rentcar.api.dto.payment.PaymentIntentResponse;
import com.rentcar.api.exception.BookingNotFoundException;
import com.rentcar.api.exception.InvalidBookingStateException;
import com.rentcar.api.exception.PaymentNotFoundException;
import com.rentcar.api.exception.PaymentProviderNotConfiguredException;
import com.rentcar.api.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingPaymentService {

    private final BookingRepository bookingRepository;
    private final PaymentService paymentService;
    private final BookingEmailNotificationService bookingEmailNotificationService;

    private final com.rentcar.api.util.AppClock appClock;

    /**
     * Creates a payment intent for a PENDING or FAILED booking without charging the customer.
     *
     * <p><b>State machine:</b>
     * <ul>
     *   <li>PENDING → uses the existing PENDING payment record (idempotent: repeated calls
     *       return the same {@code paymentReference} and a deterministic {@code clientSecret}).</li>
     *   <li>FAILED → creates a fresh PENDING payment, resets booking to PENDING.
     *       The previous FAILED record is preserved for audit history.</li>
     *   <li>CONFIRMED → rejected with 409 (booking is already paid — intent not needed).</li>
     *   <li>CANCELLED → rejected with 409 (payment is not possible).</li>
     * </ul>
     *
     * <p>The row lock prevents concurrent cancel + intent races.
     *
     * <p>The returned {@link PaymentIntentResponse#clientSecret()} is the
     * Stripe secret that the frontend passes to
     * {@code stripe.confirmCardPayment()}.
     *
     * <p>Amount and currency are always derived from the booking — the frontend must never
     * supply or override these values.
     */
    @Transactional
    public PaymentIntentResponse createPaymentIntent(Long bookingId, CreatePaymentIntentRequest request) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        String token = request != null ? request.checkoutSessionToken() : null;

        // Ownership & state validation rules (strict):
        // - PENDING: must present valid token matching booking.checkoutSessionToken and expiresAt must be in future
        // - FAILED: allow retry only if token matches and expiresAt is in future
        // - CONFIRMED/CANCELLED/EXPIRED: reject
        if (booking.getStatus() == BookingStatus.PENDING) {
            if (booking.getCheckoutSessionToken() == null) {
                // Booking has no ownership token (legacy) — allow existing behavior
            } else {
                if (token == null || !token.equals(booking.getCheckoutSessionToken())) {
                    throw new com.rentcar.api.exception.CheckoutSessionUnauthorizedException("Invalid or missing checkout session token");
                }
                if (booking.getExpiresAt() == null || !booking.getExpiresAt().isAfter(appClock.nowUtc())) {
                    // Expired — mark as EXPIRED and reject
                    booking.setStatus(com.rentcar.api.domain.booking.BookingStatus.EXPIRED);
                    booking.setExpiresAt(null);
                    booking.setCheckoutSessionToken(null);
                    bookingRepository.save(booking);
                    throw new InvalidBookingStateException("Booking hold has expired");
                }
            }
        } else if (booking.getStatus() == BookingStatus.FAILED) {
            // Allow retry only if token matches
            if (booking.getCheckoutSessionToken() != null) {
                if (token == null || !token.equals(booking.getCheckoutSessionToken())) {
                    throw new com.rentcar.api.exception.CheckoutSessionUnauthorizedException("Invalid or missing checkout session token");
                }
                if (booking.getExpiresAt() == null || !booking.getExpiresAt().isAfter(appClock.nowUtc())) {
                    booking.setStatus(com.rentcar.api.domain.booking.BookingStatus.EXPIRED);
                    booking.setExpiresAt(null);
                    booking.setCheckoutSessionToken(null);
                    bookingRepository.save(booking);
                    throw new InvalidBookingStateException("Booking hold has expired");
                }
            } else {
                // legacy failed booking without token — reject to avoid allowing arbitrary retries
                throw new com.rentcar.api.exception.CheckoutSessionUnauthorizedException("Missing checkout session token for failed booking");
            }
        } else if (booking.getStatus() == BookingStatus.CONFIRMED || booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.EXPIRED) {
            throw new InvalidBookingStateException("Booking " + bookingId + " is not in a state that permits creating a payment intent");
        }

        // TODO: when multi-method support is added (BANK_TRANSFER, SEPA, etc.),
        //   map request.paymentMethodType() to PaymentMethod enum and pass it to
        //   createPendingPayment so Payment.method is set correctly.
        //   Currently only CARD is supported and paymentMethodType is intentionally ignored.
        String requestedMethodType = request != null ? request.paymentMethodType() : null;
        if (requestedMethodType != null) {
            log.debug("paymentMethodType '{}' received but not yet applied — defaulting to CARD", requestedMethodType);
        }

        if (!"STRIPE".equals(paymentService.providerName())) {
            throw new PaymentProviderNotConfiguredException("Stripe");
        }

        return switch (booking.getStatus()) {
            case CONFIRMED -> throw new InvalidBookingStateException(
                    "Booking " + bookingId + " is already confirmed — payment intent not needed");
            case CANCELLED -> throw new InvalidBookingStateException(
                    "Booking " + bookingId + " is cancelled — payment is not possible");
            case FAILED -> {
                // Previous charge attempt failed — reset with a fresh PENDING payment and refresh the hold.
                Payment fresh = paymentService.createPendingPayment(booking);
                booking.setStatus(BookingStatus.PENDING);
                booking.setExpiresAt(appClock.nowUtc().plus(java.time.Duration.ofMinutes(15)));
                bookingRepository.save(booking);
                yield buildIntentResponse(booking, fresh);
            }
            default -> {
                // PENDING — idempotent: return intent for existing payment record.
                Payment payment = paymentService.findLatestPayment(booking)
                        .orElseThrow(() -> new PaymentNotFoundException(bookingId));
                yield buildIntentResponse(booking, payment);
            }
        };
    }

    @Transactional
    public Booking completePayment(Long bookingId, String paymentMethodId, String checkoutSessionToken) {
        // Lock the booking row to prevent concurrent cancel + completePayment races.
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        validateCheckoutSessionForPayment(booking, checkoutSessionToken);

        if (booking.getStatus() == BookingStatus.FAILED) {
            // Previous attempt failed — create a fresh PENDING payment and reset the
            // booking so processLatestPaymentForBooking picks up the new record.
            // The old FAILED payment row is preserved for audit history.
            paymentService.createPendingPayment(booking);
            booking.setStatus(BookingStatus.PENDING);
        } else if (booking.getStatus() != BookingStatus.PENDING) {
            throw new InvalidBookingStateException(
                    "Payment can only be processed for bookings in PENDING or FAILED status");
        }

        Payment latestPayment = paymentService.findLatestPayment(booking)
                .orElseThrow(() -> new PaymentNotFoundException(bookingId));
        if (!hasRealStripePaymentIntent(latestPayment)) {
            throw new InvalidBookingStateException(
                    "Payment can only be completed after a real Stripe PaymentIntent has been created");
        }

        Payment payment = paymentService.verifyLatestPaymentIntentForBooking(booking);

        // With Stripe, payment may stay PENDING while the provider processes it
        // (e.g. 3DS challenge, bank redirect). The Stripe webhook advances it to
        // CONFIRMED when Stripe sends payment_intent.succeeded.
        if (payment.getStatus() == PaymentStatus.PAID) {
            booking.setStatus(BookingStatus.CONFIRMED);
            booking.setExpiresAt(null);
            booking.setCheckoutSessionToken(null);
        } else if (payment.getStatus() == PaymentStatus.FAILED) {
            booking.setStatus(BookingStatus.FAILED);
            // Keep expiresAt so the owner retains the hold during the session window.
        } else {
            // Keep booking PENDING for processing/pending payment intents
            booking.setStatus(BookingStatus.PENDING);
        }

        Booking saved = bookingRepository.save(booking);
        log.info("Payment completed: bookingId={} bookingStatus={} paymentStatus={}",
                bookingId, saved.getStatus(), payment.getStatus());

        if (payment.getStatus() == PaymentStatus.PAID) {
            bookingEmailNotificationService.sendBookingConfirmation(saved);
        }

        return saved;
    }

    /**
     * Public payment completion is allowed only for the anonymous checkout owner.
     * Admin/customer-account flows should use separate authenticated endpoints.
     */
    private void validateCheckoutSessionForPayment(Booking booking, String token) {
        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.FAILED) {
            return;
        }
        if (booking.getCheckoutSessionToken() == null && booking.getStatus() == BookingStatus.PENDING) {
            // Legacy pending booking created before checkout tokens existed.
            return;
        }
        if (booking.getCheckoutSessionToken() == null) {
            throw new com.rentcar.api.exception.CheckoutSessionUnauthorizedException(
                    "Missing checkout session token for payment retry");
        }
        if (token == null || !token.equals(booking.getCheckoutSessionToken())) {
            throw new com.rentcar.api.exception.CheckoutSessionUnauthorizedException(
                    "Invalid or missing checkout session token");
        }
        if (booking.getExpiresAt() == null || !booking.getExpiresAt().isAfter(appClock.nowUtc())) {
            booking.setStatus(BookingStatus.EXPIRED);
            booking.setExpiresAt(null);
            booking.setCheckoutSessionToken(null);
            bookingRepository.save(booking);
            throw new InvalidBookingStateException("Booking hold has expired");
        }
    }

    private PaymentIntentResponse buildIntentResponse(Booking booking, Payment payment) {
        var result = paymentService.createIntentForPayment(payment);
        // Persist provider intent id (Stripe PaymentIntent) when present for reconciliation/audit.
        if (result.providerIntentId() != null) {
            payment.setStripePaymentIntentId(result.providerIntentId());
            paymentService.save(payment);
        }
        log.info("Payment intent created: bookingId={} paymentRef={} provider={}",
                booking.getId(), payment.getPaymentReference(), result.providerName());
        return new PaymentIntentResponse(
                booking.getId(),
                booking.getBookingReference(),
                payment.getAmount(),
                payment.getCurrencyCode(),
                result.providerName(),
                result.clientSecret(),
                payment.getPaymentReference()
        );
    }

    private boolean hasRealStripePaymentIntent(Payment payment) {
        String intentId = payment.getStripePaymentIntentId();
        return intentId != null
                && intentId.startsWith("pi_")
                && "STRIPE".equals(paymentService.providerName());
    }

}
