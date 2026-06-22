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
    private final BookingCancellationPolicyService cancellationPolicyService;

    @Transactional
    public Booking cancelBooking(Long id) {
        // Lock the booking row to prevent concurrent cancel + completePayment races.
        Booking booking = bookingRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BookingNotFoundException(id));

        CancellationPolicyDecision policy = cancellationPolicyService.evaluateAdminPolicy(booking);
        if (!policy.adminOperationalCancellationAllowed()) {
            throw new BookingCannotBeCancelledException(id);
        }

        return cancelLockedBookingAsAdmin(booking, "Cancelled by admin", policy.refundEligible(), false);
    }

    @Transactional
    public Booking cancelBookingWithRefund(Long id) {
        Booking booking = bookingRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BookingNotFoundException(id));
        CancellationPolicyDecision policy = cancellationPolicyService.evaluateAdminPolicy(booking);
        if (!policy.cancellable() || !policy.refundEligible()) {
            throw new BookingCannotBeCancelledException(policy.reason() != null
                    ? policy.reason()
                    : "This booking is not eligible for cancellation with refund.");
        }
        return cancelLockedBookingAsAdmin(booking, "Cancelled by admin with refund", true, false);
    }

    @Transactional
    public Booking markNoShow(Long id) {
        Booking booking = bookingRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BookingNotFoundException(id));
        CancellationPolicyDecision policy = cancellationPolicyService.evaluateNoShow(booking);
        if (!policy.adminOperationalCancellationAllowed()) {
            throw new BookingCannotBeCancelledException(id);
        }
        return cancelLockedBookingAsAdmin(booking, policy.cancellationReason(), false, true);
    }

    /**
     * Customer-facing cancellation identified by bookingReference + lastName.
     * Evaluates the same policy rules as {@link #getCancellationPolicy} and
     * throws {@link BookingCannotBeCancelledException} with a human-readable
     * reason when cancellation is not allowed.
     *
     * <p>For PAID bookings that are refund-eligible, {@link PaymentService#handleCancellationPayment}
     * refunds only payments that have a real provider reference. Missing Stripe references
     * are left in {@code REFUND_PENDING} for operations review.
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
        CancellationPolicyDecision policy = cancellationPolicyService.evaluateCustomerPolicy(booking);
        if (!policy.cancellable()) {
            throw new BookingCannotBeCancelledException(policy.policyMessage());
        }

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
        policy = cancellationPolicyService.evaluateCustomerPolicy(locked);
        if (!policy.cancellable()) {
            throw new BookingCannotBeCancelledException(policy.policyMessage());
        }

        locked.setStatus(BookingStatus.CANCELLED);
        locked.setExpiresAt(null);
        locked.setCheckoutSessionToken(null);
        locked.setCancelledByType(BookingActorType.CUSTOMER_ANONYMOUS);
        locked.setCancelledChannel(BookingChannel.WEB);
        locked.setCancelledAt(businessTimezone.nowBusiness().toInstant());
        locked.setCancellationReason(cancellationReasonOrDefault(cancellationReason));
        Booking saved = bookingRepository.save(locked);
        if (policy.refundEligible()) {
            paymentService.handleCancellationPayment(saved);
        } else {
            paymentService.handleCancellationWithoutRefund(saved);
        }
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
        return cancellationPolicyService.toResponse(cancellationPolicyService.evaluateCustomerPolicy(booking));
    }

    public CancellationPolicyResponse getCancellationPolicyByManageToken(String manageToken) {
        Booking booking = manageBookingTokenService.findBookingByToken(manageToken);
        return cancellationPolicyService.toResponse(cancellationPolicyService.evaluateCustomerPolicy(booking));
    }

    private Booking cancelLockedBookingAsAdmin(Booking booking, String reason, boolean refundEligible, boolean noShow) {
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setExpiresAt(null);
        booking.setCheckoutSessionToken(null);
        booking.setCancelledByType(BookingActorType.ADMIN);
        booking.setCancelledChannel(BookingChannel.ADMIN_PANEL);
        booking.setCancelledAt(businessTimezone.nowBusiness().toInstant());
        booking.setCancellationReason(reason);
        Booking savedBooking = bookingRepository.save(booking);
        if (refundEligible) {
            paymentService.handleCancellationPayment(savedBooking);
        } else {
            paymentService.handleCancellationWithoutRefund(savedBooking);
        }
        if (noShow) {
            bookingEmailNotificationService.sendNoShowRecorded(savedBooking);
        } else {
            paymentService.findLatestPayment(savedBooking)
                    .ifPresent(payment -> bookingEmailNotificationService
                            .sendBookingCancellation(savedBooking, payment.getStatus()));
        }
        log.info("Booking cancelled by admin: bookingId={} reference={} status={} noShow={} refundEligible={}",
                savedBooking.getId(), savedBooking.getBookingReference(), savedBooking.getStatus(),
                noShow, refundEligible);
        return savedBooking;
    }

    private String cancellationReasonOrDefault(String cancellationReason) {
        if (cancellationReason == null || cancellationReason.isBlank()) {
            return "Customer requested cancellation from manage booking";
        }
        String trimmed = cancellationReason.trim();
        return trimmed.length() > 512 ? trimmed.substring(0, 512) : trimmed;
    }
}
