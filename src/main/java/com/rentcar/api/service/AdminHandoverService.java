package com.rentcar.api.service;

import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.domain.booking.BookingStatus;
import com.rentcar.api.domain.handover.BookingDeposit;
import com.rentcar.api.domain.handover.BookingDepositMethod;
import com.rentcar.api.domain.handover.BookingDepositStatus;
import com.rentcar.api.domain.handover.DepositRefund;
import com.rentcar.api.domain.handover.DepositRefundType;
import com.rentcar.api.domain.handover.VehicleDamage;
import com.rentcar.api.domain.handover.VehicleHandover;
import com.rentcar.api.dto.admin.handover.BookingDepositResponse;
import com.rentcar.api.dto.admin.handover.DepositPaymentIntentResponse;
import com.rentcar.api.dto.admin.handover.DepositRefundRequest;
import com.rentcar.api.dto.admin.handover.DepositRefundResponse;
import com.rentcar.api.dto.admin.handover.HandoverPageResponse;
import com.rentcar.api.dto.admin.handover.ManualDepositCollectionRequest;
import com.rentcar.api.dto.admin.handover.SaveHandoverRequest;
import com.rentcar.api.dto.admin.handover.VehicleDamageResponse;
import com.rentcar.api.dto.admin.handover.VehicleHandoverResponse;
import com.rentcar.api.exception.BookingNotFoundException;
import com.rentcar.api.exception.InvalidBookingStateException;
import com.rentcar.api.payment.provider.PaymentProvider;
import com.rentcar.api.repository.BookingDepositRepository;
import com.rentcar.api.repository.BookingRepository;
import com.rentcar.api.repository.DepositRefundRepository;
import com.rentcar.api.repository.VehicleDamageRepository;
import com.rentcar.api.repository.VehicleHandoverRepository;
import com.rentcar.api.util.AppClock;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminHandoverService {

    private static final int REFUND_WINDOW_DAYS = 45;

    private final BookingRepository bookingRepository;
    private final BookingDepositRepository depositRepository;
    private final DepositRefundRepository depositRefundRepository;
    private final VehicleDamageRepository vehicleDamageRepository;
    private final VehicleHandoverRepository handoverRepository;
    private final AdminBookingService adminBookingService;
    private final PaymentProvider paymentProvider;
    private final AppClock appClock;

    @Value("${app.public-base-url:}")
    private String publicBaseUrl;

    @Transactional(readOnly = true)
    public HandoverPageResponse getHandoverPage(Long bookingId) {
        Booking booking = bookingRepository.findByIdWithDetails(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        BookingDeposit deposit = depositRepository.findByBooking(booking).orElse(null);
        VehicleHandover handover = handoverRepository.findByBooking(booking).orElse(null);
        List<VehicleDamageResponse> damages = vehicleDamageRepository.findAllByCarAndActiveTrueOrderByIdAsc(booking.getCar())
                .stream()
                .map(this::toDamageResponse)
                .toList();
        return new HandoverPageResponse(
                adminBookingService.getBookingById(bookingId),
                toDepositResponse(deposit),
                toHandoverResponse(handover),
                damages,
                canStartHandover(booking, handover),
                canMarkPickedUp(booking, deposit, handover)
        );
    }

    @Transactional
    public BookingDepositResponse ensureDeposit(Long bookingId) {
        Booking booking = bookingRepository.findByIdWithDetails(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        return toDepositResponse(ensureDepositEntity(booking, BookingDepositMethod.CASH));
    }

    @Transactional
    public DepositPaymentIntentResponse createStripeDepositIntent(Long bookingId) {
        Booking booking = bookingRepository.findByIdWithDetails(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        BookingDeposit deposit = ensureDepositEntity(booking, BookingDepositMethod.STRIPE);
        if (deposit.getStatus() == BookingDepositStatus.COLLECTED) {
            throw new InvalidBookingStateException("Deposit has already been collected");
        }
        deposit.setMethod(BookingDepositMethod.STRIPE);
        String successUrl = depositLinkUrl(booking.getId(), deposit.getId()) + "&deposit=success";
        String cancelUrl = depositLinkUrl(booking.getId(), deposit.getId()) + "&deposit=cancelled";
        var session = paymentProvider.createDepositCheckoutSession(deposit, successUrl, cancelUrl);
        deposit.setStripePaymentIntentId(blankToNull(session.paymentIntentId()));
        deposit.setStripeCheckoutSessionId(session.checkoutSessionId());
        deposit.setStripePaymentReference(session.paymentIntentId() != null ? session.paymentIntentId() : session.checkoutSessionId());
        deposit.setStripePaymentLinkUrl(session.checkoutUrl());
        deposit.setStatus(BookingDepositStatus.PAYMENT_LINK_CREATED);
        BookingDeposit saved = depositRepository.save(deposit);
        return new DepositPaymentIntentResponse(
                toDepositResponse(saved),
                session.providerName(),
                session.clientSecret(),
                session.paymentIntentId(),
                saved.getStripePaymentLinkUrl()
        );
    }

    @Transactional
    public BookingDepositResponse markManualDepositCollected(Long bookingId, ManualDepositCollectionRequest request) {
        if (request.method() == BookingDepositMethod.STRIPE) {
            throw new InvalidBookingStateException("Stripe deposits must be confirmed by Stripe");
        }
        Booking booking = bookingRepository.findByIdWithDetails(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        BookingDeposit deposit = ensureDepositEntity(booking, request.method());
        deposit.setMethod(request.method());
        deposit.setAdminNote(request.note());
        markCollected(deposit, "ADMIN-" + request.method().name());
        return toDepositResponse(depositRepository.save(deposit));
    }

    @Transactional
    public BookingDepositResponse applyStripeDepositIntentStatus(String paymentIntentId, String status, Long amountMinor, String currency, Map<String, String> metadata) {
        return findDepositForStripeWebhook(paymentIntentId, metadata)
                .map(deposit -> {
                    if (deposit.getStripePaymentIntentId() == null || deposit.getStripePaymentIntentId().isBlank()) {
                        deposit.setStripePaymentIntentId(paymentIntentId);
                    }
                    validateStripeDeposit(deposit, paymentIntentId, amountMinor, currency, metadata);
                    switch (status) {
                        case "succeeded" -> markCollected(deposit, paymentIntentId);
                        case "requires_payment_method", "canceled", "failed" -> deposit.setStatus(BookingDepositStatus.FAILED);
                        default -> deposit.setStatus(BookingDepositStatus.PAYMENT_LINK_CREATED);
                    }
                    return toDepositResponse(depositRepository.save(deposit));
                })
                .orElse(null);
    }

    @Transactional
    public VehicleHandoverResponse saveHandover(Long bookingId, SaveHandoverRequest request) {
        Booking booking = bookingRepository.findByIdWithDetails(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        BookingDeposit deposit = ensureDepositEntity(booking, BookingDepositMethod.CASH);
        VehicleHandover handover = handoverRepository.findByBooking(booking)
                .orElseGet(() -> VehicleHandover.builder().booking(booking).deposit(deposit).build());
        handover.setKmOut(request.kmOut());
        handover.setFuelLevelOut(request.fuelLevelOut());
        handover.setBatteryLevelOut(request.batteryLevelOut());
        handover.setNotes(request.notes());
        if (request.customerSignatureData() != null && !request.customerSignatureData().isBlank()) {
            handover.setCustomerSignatureData(request.customerSignatureData());
            handover.setCustomerSignatureAt(appClock.nowUtc());
        }
        handover.setDeposit(deposit);
        return toHandoverResponse(handoverRepository.save(handover));
    }

    @Transactional
    public HandoverPageResponse markPickedUp(Long bookingId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        BookingDeposit deposit = depositRepository.findByBookingIdForUpdate(bookingId)
                .orElseThrow(() -> new InvalidBookingStateException("Deposit is required before pickup"));
        VehicleHandover handover = handoverRepository.findByBookingIdForUpdate(bookingId)
                .orElseThrow(() -> new InvalidBookingStateException("Handover details are required before pickup"));
        if (!canMarkPickedUp(booking, deposit, handover)) {
            throw new InvalidBookingStateException("Handover fields, customer signature, and collected deposit are required before pickup");
        }
        booking.setStatus(BookingStatus.PICKED_UP);
        handover.setHandoverAt(appClock.nowUtc());
        bookingRepository.save(booking);
        handoverRepository.save(handover);
        return getHandoverPage(bookingId);
    }

    @Transactional
    public BookingDepositResponse refundDeposit(Long bookingId, DepositRefundRequest request, String actor) {
        BookingDeposit deposit = depositRepository.findByBookingIdForUpdate(bookingId)
                .orElseThrow(() -> new InvalidBookingStateException("Deposit not found for booking " + bookingId));
        if (deposit.getCollectedAt() == null || deposit.getRefundDeadlineAt() == null) {
            throw new InvalidBookingStateException("Deposit has not been collected");
        }
        if (appClock.nowUtc().isAfter(deposit.getRefundDeadlineAt())) {
            throw new InvalidBookingStateException("Deposit refund deadline has passed");
        }
        BigDecimal amount = request.refundAmount().setScale(2, java.math.RoundingMode.UNNECESSARY);
        if (amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(deposit.getRemainingAmount()) > 0) {
            throw new InvalidBookingStateException("Refund amount must be greater than zero and not exceed remaining deposit");
        }

        String providerRefundId = null;
        if (deposit.getMethod() == BookingDepositMethod.STRIPE) {
            var result = paymentProvider.refundDeposit(deposit, amount);
            if (!result.successful()) {
                throw new InvalidBookingStateException("Stripe deposit refund did not succeed: " + result.providerStatus());
            }
            providerRefundId = result.providerReference();
        }

        deposit.setRefundedAmount(deposit.getRefundedAmount().add(amount));
        deposit.setRemainingAmount(deposit.getRemainingAmount().subtract(amount));
        deposit.setStatus(deposit.getRemainingAmount().compareTo(BigDecimal.ZERO) == 0
                ? BookingDepositStatus.REFUNDED
                : BookingDepositStatus.PARTIALLY_REFUNDED);
        depositRefundRepository.save(DepositRefund.builder()
                .deposit(deposit)
                .type(request.type())
                .amount(amount)
                .note(request.note())
                .stripeRefundId(providerRefundId)
                .createdBy(actor)
                .build());
        return toDepositResponse(depositRepository.save(deposit));
    }

    private BookingDeposit ensureDepositEntity(Booking booking, BookingDepositMethod defaultMethod) {
        BigDecimal amount = booking.getDepositAmountSnapshot();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidBookingStateException("Booking has no deposit snapshot");
        }
        return depositRepository.findByBooking(booking)
                .orElseGet(() -> depositRepository.save(BookingDeposit.builder()
                        .booking(booking)
                        .amount(amount.setScale(2, java.math.RoundingMode.UNNECESSARY))
                        .remainingAmount(amount.setScale(2, java.math.RoundingMode.UNNECESSARY))
                        .refundedAmount(BigDecimal.ZERO)
                        .currency("EUR")
                        .method(defaultMethod)
                        .status(BookingDepositStatus.NOT_COLLECTED)
                        .build()));
    }

    private void markCollected(BookingDeposit deposit, String reference) {
        deposit.setStatus(BookingDepositStatus.COLLECTED);
        deposit.setStripePaymentReference(reference);
        if (deposit.getCollectedAt() == null) {
            deposit.setCollectedAt(appClock.nowUtc());
        }
        deposit.setRefundDeadlineAt(deposit.getCollectedAt().plus(java.time.Duration.ofDays(REFUND_WINDOW_DAYS)));
        deposit.setRemainingAmount(deposit.getAmount().subtract(deposit.getRefundedAmount()));
    }

    private boolean canStartHandover(Booking booking, VehicleHandover handover) {
        return booking.getStatus() == BookingStatus.CONFIRMED && handover == null && !isNoShow(booking);
    }

    private boolean canMarkPickedUp(Booking booking, BookingDeposit deposit, VehicleHandover handover) {
        return booking.getStatus() == BookingStatus.CONFIRMED
                && deposit != null
                && deposit.getStatus() == BookingDepositStatus.COLLECTED
                && handover != null
                && handover.getKmOut() != null
                && handover.getFuelLevelOut() != null
                && handover.getBatteryLevelOut() != null
                && handover.getCustomerSignatureData() != null
                && !handover.getCustomerSignatureData().isBlank();
    }

    private boolean isNoShow(Booking booking) {
        return "NO_SHOW".equalsIgnoreCase(booking.getCancellationReason());
    }

    private void validateStripeDeposit(BookingDeposit deposit, String paymentIntentId, Long amountMinor, String currency, Map<String, String> metadata) {
        if (!paymentIntentId.equals(deposit.getStripePaymentIntentId())) {
            throw new InvalidBookingStateException("Stripe PaymentIntent does not match this deposit");
        }
        if (!amountInMinorUnits(deposit.getAmount()).equals(amountMinor)) {
            throw new InvalidBookingStateException("Stripe deposit amount does not match booking snapshot");
        }
        if (!deposit.getCurrency().equalsIgnoreCase(currency == null ? "" : currency)) {
            throw new InvalidBookingStateException("Stripe deposit currency does not match");
        }
        if (metadata == null
                || !"DEPOSIT".equals(metadata.get("paymentType"))
                || !String.valueOf(deposit.getBooking().getId()).equals(metadata.get("bookingId"))
                || !String.valueOf(deposit.getId()).equals(metadata.get("depositId"))) {
            throw new InvalidBookingStateException("Stripe deposit metadata does not match");
        }
    }

    private java.util.Optional<BookingDeposit> findDepositForStripeWebhook(String paymentIntentId, Map<String, String> metadata) {
        var byIntent = depositRepository.findByStripePaymentIntentIdForUpdate(paymentIntentId);
        if (byIntent.isPresent()) {
            return byIntent;
        }
        String depositId = metadata == null ? null : metadata.get("depositId");
        if (depositId == null || depositId.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            return depositRepository.findByIdForUpdate(Long.parseLong(depositId));
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
    }

    private Long amountInMinorUnits(BigDecimal amount) {
        return amount.setScale(2, java.math.RoundingMode.UNNECESSARY)
                .multiply(BigDecimal.valueOf(100))
                .longValueExact();
    }

    private String depositLinkUrl(Long bookingId, Long depositId) {
        String base = publicBaseUrl == null || publicBaseUrl.isBlank() ? "" : publicBaseUrl.replaceAll("/+$", "");
        return base + "/admin/booking-handover.html?bookingId=" + bookingId + "&depositId=" + depositId;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private BookingDepositResponse toDepositResponse(BookingDeposit deposit) {
        if (deposit == null) return null;
        List<DepositRefundResponse> refunds = depositRefundRepository.findAllByDepositOrderByCreatedAtDescIdDesc(deposit)
                .stream()
                .map(this::toRefundResponse)
                .toList();
        return new BookingDepositResponse(
                deposit.getId(),
                deposit.getBooking().getId(),
                deposit.getAmount(),
                deposit.getCurrency(),
                deposit.getMethod(),
                deposit.getStatus(),
                deposit.getStripePaymentIntentId(),
                deposit.getStripeCheckoutSessionId(),
                deposit.getStripePaymentLinkUrl(),
                deposit.getStripePaymentReference(),
                deposit.getAdminNote(),
                deposit.getCollectedAt(),
                deposit.getRefundDeadlineAt(),
                deposit.getRefundedAmount(),
                deposit.getRemainingAmount(),
                deposit.getCreatedAt(),
                deposit.getUpdatedAt(),
                refunds
        );
    }

    private DepositRefundResponse toRefundResponse(DepositRefund refund) {
        return new DepositRefundResponse(
                refund.getId(),
                refund.getDeposit().getId(),
                refund.getType(),
                refund.getAmount(),
                refund.getNote(),
                refund.getStripeRefundId(),
                refund.getCreatedAt(),
                refund.getCreatedBy()
        );
    }

    private VehicleHandoverResponse toHandoverResponse(VehicleHandover handover) {
        if (handover == null) return null;
        return new VehicleHandoverResponse(
                handover.getId(),
                handover.getBooking().getId(),
                handover.getHandoverAt(),
                handover.getKmOut(),
                handover.getFuelLevelOut(),
                handover.getBatteryLevelOut(),
                handover.getCustomerSignatureData() != null && !handover.getCustomerSignatureData().isBlank(),
                handover.getCustomerSignatureAt(),
                handover.getDeposit() != null ? handover.getDeposit().getId() : null,
                handover.getNotes(),
                handover.getCreatedAt(),
                handover.getUpdatedAt()
        );
    }

    private VehicleDamageResponse toDamageResponse(VehicleDamage damage) {
        return new VehicleDamageResponse(
                damage.getId(),
                damage.getCar().getId(),
                damage.getDamageCode(),
                damage.getTitle(),
                damage.getDescription(),
                damage.getLocation(),
                damage.getSeverity(),
                damage.isActive(),
                damage.getCreatedAt(),
                damage.getUpdatedAt()
        );
    }
}
