package com.rentcar.api.service;

import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.domain.booking.BookingActorType;
import com.rentcar.api.domain.booking.BookingChannel;
import com.rentcar.api.domain.booking.BookingStatus;
import com.rentcar.api.dto.booking.CancellationPolicyResponse;
import com.rentcar.api.exception.BookingCannotBeCancelledException;
import com.rentcar.api.exception.BookingNotFoundException;
import com.rentcar.api.repository.BookingRepository;
import com.rentcar.api.util.BusinessTimezone;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingCancellationService {

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final BusinessTimezone businessTimezone;
    private final BookingEmailNotificationService bookingEmailNotificationService;
    private final ManageBookingTokenService manageBookingTokenService;

    @Transactional
    public Booking cancelBooking(Long id) {
        // Lock the booking row to prevent concurrent cancel + completePayment races.
        Booking booking = bookingRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BookingNotFoundException(id));

        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BookingCannotBeCancelledException(id);
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setExpiresAt(null);
        booking.setCheckoutSessionToken(null);
        booking.setCancelledByType(BookingActorType.ADMIN);
        booking.setCancelledChannel(BookingChannel.ADMIN_PANEL);
        booking.setCancelledAt(businessTimezone.nowBusiness().toInstant());
        booking.setCancellationReason("Cancelled by admin");
        Booking savedBooking = bookingRepository.save(booking);
        paymentService.handleCancellationPayment(savedBooking);
        paymentService.findLatestPayment(savedBooking)
                .ifPresent(payment -> bookingEmailNotificationService
                        .sendBookingCancellation(savedBooking, payment.getStatus()));
        log.info("Booking cancelled by admin: bookingId={} reference={} status={} cancelledByType={} cancelledChannel={}",
                savedBooking.getId(), savedBooking.getBookingReference(), savedBooking.getStatus(),
                savedBooking.getCancelledByType(), savedBooking.getCancelledChannel());
        return savedBooking;
    }

    /**
     * Customer-facing cancellation identified by bookingReference + lastName.
     * Evaluates the same policy rules as {@link #getCancellationPolicy} and
     * throws {@link BookingCannotBeCancelledException} with a human-readable
     * reason when cancellation is not allowed.
     *
     * <p>For PAID bookings that are refund-eligible the mock refund provider is
     * invoked via {@link PaymentService#handleCancellationPayment}; it sets the
     * payment status to {@code REFUNDED}.
     *
     * <p>The numeric booking id is never exposed to the caller — authentication
     * is entirely through the reference + lastName pair.
     */
    @Transactional
    public Booking cancelBookingByReference(String bookingReference, String lastName) {
        return cancelBookingByReference(bookingReference, lastName, null);
    }

    @Transactional
    public Booking cancelBookingByReference(String bookingReference, String lastName, String cancellationReason) {
        // Step 1: Verify identity and check policy (non-locked read).
        Booking booking = bookingService.findBookingByReferenceAndLastName(bookingReference, lastName);
        return cancelVerifiedCustomerBooking(booking, cancellationReason, null);
    }

    @Transactional
    public Booking cancelBookingByManageToken(String manageToken, String cancellationReason) {
        Booking booking = manageBookingTokenService.findBookingByToken(manageToken);
        return cancelVerifiedCustomerBooking(booking, cancellationReason, manageToken);
    }

    private Booking cancelVerifiedCustomerBooking(Booking booking, String cancellationReason, String manageToken) {
        LocalDateTime now = businessTimezone.nowBusinessLocal();

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BookingCannotBeCancelledException("This booking has already been cancelled.");
        }
        if (booking.getPickupDateTime().isBefore(now)) {
            throw new BookingCannotBeCancelledException("The booking can no longer be modified after the pickup date.");
        }
        if (booking.getStatus() == BookingStatus.FAILED) {
            throw new BookingCannotBeCancelledException("This booking payment has already failed.");
        }
        // PENDING, CONFIRMED are all cancellable.

        // Step 2: Re-fetch with PESSIMISTIC_WRITE lock so concurrent
        // cancel + completePayment requests are serialised.
        Booking locked = bookingRepository.findByIdForUpdate(booking.getId())
                .orElseThrow(() -> new BookingNotFoundException(booking.getId()));

        if (manageToken != null && !manageToken.isBlank()) {
            manageBookingTokenService.validateTokenForBooking(locked, manageToken);
        }

        // Re-check status under lock in case another request won the race.
        if (locked.getStatus() == BookingStatus.CANCELLED) {
            throw new BookingCannotBeCancelledException("This booking has already been cancelled.");
        }

        locked.setStatus(BookingStatus.CANCELLED);
        locked.setExpiresAt(null);
        locked.setCheckoutSessionToken(null);
        locked.setCancelledByType(BookingActorType.CUSTOMER_ANONYMOUS);
        locked.setCancelledChannel(BookingChannel.WEB);
        locked.setCancelledAt(businessTimezone.nowBusiness().toInstant());
        locked.setCancellationReason(cancellationReasonOrDefault(cancellationReason));
        Booking saved = bookingRepository.save(locked);
        // handleCancellationPayment handles PAID (mock refund → REFUNDED) and
        // PENDING/FAILED (voids the record → CANCELLED).
        paymentService.handleCancellationPayment(saved);
        paymentService.findLatestPayment(saved)
                .ifPresent(payment -> bookingEmailNotificationService
                        .sendBookingCancellation(saved, payment.getStatus()));
        log.info("Booking cancelled by customer: bookingId={} reference={} status={} cancelledByType={} cancelledChannel={}",
                saved.getId(), saved.getBookingReference(), saved.getStatus(),
                saved.getCancelledByType(), saved.getCancelledChannel());
        return saved;
    }

    /**
     * Returns a cancellation policy preview for the booking identified by
     * {@code bookingReference} + {@code lastName} (accent-insensitive).
     *
     * <p>Policy rules (evaluated in order):
     * <ol>
     *   <li>CANCELLED → not cancellable</li>
     *   <li>pickup in the past → not cancellable</li>
     *   <li>CONFIRMED + pickup &gt; 24 h away → cancellable, full refund</li>
     *   <li>CONFIRMED + pickup ≤ 24 h away → cancellable, no refund (MVP)</li>
     *   <li>PENDING / FAILED → cancellable, no charge (never paid)</li>
     * </ol>
     *
     * TODO: when STAY_FLEXIBLE is implemented, rule 4 should grant a free
     * cancellation regardless of the 24-h window for that option type.
     */
    public CancellationPolicyResponse getCancellationPolicy(String bookingReference, String lastName) {
        Booking booking = bookingService.findBookingByReferenceAndLastName(bookingReference, lastName);
        return cancellationPolicyFor(booking);
    }

    public CancellationPolicyResponse getCancellationPolicyByManageToken(String manageToken) {
        Booking booking = manageBookingTokenService.findBookingByToken(manageToken);
        return cancellationPolicyFor(booking);
    }

    private CancellationPolicyResponse cancellationPolicyFor(Booking booking) {
        LocalDateTime now = businessTimezone.nowBusinessLocal();

        // Rule 1 — already cancelled
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return blocked(
                    "Booking is already cancelled.",
                    "This booking has already been cancelled and cannot be modified.");
        }

        // Rule 2 — pickup date has passed (applies to any non-cancelled status)
        if (booking.getPickupDateTime().isBefore(now)) {
            return blocked(
                    "Your pickup date has passed.",
                    "The booking can no longer be modified after the pickup date.");
        }

        // Rule 3 & 4 — CONFIRMED bookings: refund depends on 24-h window
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            boolean moreThan24h = booking.getPickupDateTime().isAfter(now.plusHours(24));
            if (moreThan24h) {
                return new CancellationPolicyResponse(
                        true, null, true,
                        booking.getTotalPrice().setScale(2, RoundingMode.HALF_UP),
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        "Full refund will be applied.");
            } else {
                return new CancellationPolicyResponse(
                        true, null, false,
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                        "Cancellation within 24 hours of pickup — no refund applies.");
            }
        }

        // Rule 5 — PENDING or FAILED: payment was never collected
        return new CancellationPolicyResponse(
                true, null, false,
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                "Your booking has not been paid — no charge applies.");
    }

    private static CancellationPolicyResponse blocked(String reason, String policyMessage) {
        return new CancellationPolicyResponse(
                false, reason, false,
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                policyMessage);
    }

    private String cancellationReasonOrDefault(String cancellationReason) {
        if (cancellationReason == null || cancellationReason.isBlank()) {
            return "Customer requested cancellation from manage booking";
        }
        String trimmed = cancellationReason.trim();
        return trimmed.length() > 512 ? trimmed.substring(0, 512) : trimmed;
    }
}
