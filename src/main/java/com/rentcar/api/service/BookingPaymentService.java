package com.rentcar.api.service;

import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.domain.booking.BookingStatus;
import com.rentcar.api.domain.payment.Payment;
import com.rentcar.api.domain.payment.PaymentStatus;
import com.rentcar.api.dto.payment.CreatePaymentIntentRequest;
import com.rentcar.api.dto.payment.PaymentIntentResponse;
import com.rentcar.api.email.ConfirmationEmailData;
import com.rentcar.api.email.EmailService;
import com.rentcar.api.exception.BookingNotFoundException;
import com.rentcar.api.exception.InvalidBookingStateException;
import com.rentcar.api.exception.PaymentNotFoundException;
import com.rentcar.api.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingPaymentService {

    private final BookingRepository bookingRepository;
    private final PaymentService paymentService;
    private final EmailService emailService;

    /**
     * Optional public base URL of the application (e.g. {@code http://localhost:8091}).
     * Used to build manage-booking deep-links in confirmation emails.
     * Configure via {@code app.public-base-url} in application properties.
     * Defaults to empty — manage-booking link omitted from email if blank.
     */
    @Value("${app.public-base-url:}")
    private String publicBaseUrl;

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
     * <p>The returned {@link PaymentIntentResponse#clientSecret()} is {@code null} for the
     * fake dev provider. Real Stripe returns a secret that the frontend passes to
     * {@code stripe.confirmCardPayment()}.
     *
     * <p>Amount and currency are always derived from the booking — the frontend must never
     * supply or override these values.
     */
    @Transactional
    public PaymentIntentResponse createPaymentIntent(Long bookingId, CreatePaymentIntentRequest request) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        // TODO: when multi-method support is added (BANK_TRANSFER, SEPA, etc.),
        //   map request.paymentMethodType() to PaymentMethod enum and pass it to
        //   createPendingPayment so Payment.method is set correctly.
        //   Currently only CARD is supported and paymentMethodType is intentionally ignored.
        String requestedMethodType = request != null ? request.paymentMethodType() : null;
        if (requestedMethodType != null) {
            log.debug("paymentMethodType '{}' received but not yet applied — defaulting to CARD", requestedMethodType);
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
    public Booking completePayment(Long bookingId, String paymentMethodId) {
        // Lock the booking row to prevent concurrent cancel + completePayment races.
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

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

        // For Stripe flows (PaymentIntent created), verify the provider-side intent status
        // instead of calling the synchronous pay() method. Fake provider also supports
        // fetchPaymentIntentStatus and will return 'succeeded' for local dev flows.
        // Determine whether this booking has a provider intent that must be verified.
        Payment latestPayment = paymentService.findLatestPayment(booking)
                .orElseThrow(() -> new PaymentNotFoundException(bookingId));

        Payment payment;
        if (latestPayment.getStripePaymentIntentId() != null && paymentService.providerName().equals("STRIPE")) {
            payment = paymentService.verifyLatestPaymentIntentForBooking(booking);
        } else {
            payment = paymentService.processLatestPaymentForBooking(booking, paymentMethodId);
        }

        // TODO (async): with real Stripe, payment may stay PENDING here while the provider
        //   processes it (e.g. 3DS challenge, bank redirect). In that case, keep the booking
        //   PENDING and let a PaymentWebhookHandler advance it to CONFIRMED when Stripe sends
        //   'payment_intent.succeeded'. FakePaymentProvider resolves synchronously.
        if (payment.getStatus() == PaymentStatus.PAID) {
            booking.setStatus(BookingStatus.CONFIRMED);
            booking.setExpiresAt(null);
        } else if (payment.getStatus() == PaymentStatus.FAILED) {
            booking.setStatus(BookingStatus.FAILED);
            booking.setExpiresAt(null);
        } else {
            // Keep booking PENDING for processing/pending payment intents
            booking.setStatus(BookingStatus.PENDING);
        }

        Booking saved = bookingRepository.save(booking);
        log.info("Payment completed: bookingId={} bookingStatus={} paymentStatus={}",
                bookingId, saved.getStatus(), payment.getStatus());

        if (payment.getStatus() == PaymentStatus.PAID) {
            // Fire confirmation email. Failure must NEVER rollback the confirmed booking:
            // the booking is already saved; we only log a warning if the email layer fails.
            try {
                emailService.sendBookingConfirmation(buildConfirmationEmailData(saved));
            } catch (Exception e) {
                log.warn("Confirmation email failed for bookingId={} reference={}: {}",
                        bookingId, saved.getBookingReference(), e.getMessage());
            }
        }

        return saved;
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

    private ConfirmationEmailData buildConfirmationEmailData(Booking booking) {
        String manageUrl = (publicBaseUrl != null && !publicBaseUrl.isBlank())
                ? publicBaseUrl + "/manage-booking.html?bookingReference=" + booking.getBookingReference()
                : null;
        return new ConfirmationEmailData(
                booking.getBookingReference(),
                booking.getCustomer().getEmail(),
                booking.getCustomer().getFullName(),
                booking.getPickupDateTime(),
                booking.getPickupLocation(),
                booking.getDropoffDateTime(),
                booking.getDropoffLocation(),
                booking.getTotalPrice(),
                manageUrl
        );
    }
}
