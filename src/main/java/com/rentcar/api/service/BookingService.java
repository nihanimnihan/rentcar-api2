package com.rentcar.api.service;

import com.rentcar.api.domain.addon.Addon;
import com.rentcar.api.domain.addon.AddonPricingType;
import com.rentcar.api.domain.addon.BookingAddon;
import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.domain.booking.BookingActorType;
import com.rentcar.api.domain.booking.BookingChannel;
import com.rentcar.api.domain.booking.BookingOptionType;
import com.rentcar.api.domain.booking.BookingSource;
import com.rentcar.api.domain.booking.BookingStatus;
import com.rentcar.api.domain.booking.MileageOption;
import com.rentcar.api.domain.car.Car;
import com.rentcar.api.domain.customer.Customer;
import com.rentcar.api.domain.payment.Payment;
import com.rentcar.api.domain.payment.PaymentStatus;
import com.rentcar.api.dto.booking.CancellationPolicyResponse;
import com.rentcar.api.dto.booking.CreateBookingRequest;
import com.rentcar.api.dto.pricing.PriceBreakdown;
import com.rentcar.api.email.ConfirmationEmailData;
import com.rentcar.api.email.EmailService;
import com.rentcar.api.exception.BookingCannotBeCancelledException;
import com.rentcar.api.exception.BookingNotFoundException;
import com.rentcar.api.exception.CarNotAvailableException;
import com.rentcar.api.exception.InvalidBookingDateException;
import com.rentcar.api.exception.InvalidBookingStateException;
import com.rentcar.api.repository.AddonRepository;
import com.rentcar.api.repository.BookingAddonRepository;
import com.rentcar.api.repository.BookingRepository;
import com.rentcar.api.util.BookingReferenceGenerator;
import com.rentcar.api.util.BusinessTimezone;
import com.rentcar.api.util.NameNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;


@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final CustomerService customerService;
    private final PaymentService paymentService;
    private final PricingService pricingService;
    private final CarService carService;
    private final AddonRepository addonRepository;
    private final BookingAddonRepository bookingAddonRepository;
    private final BusinessTimezone businessTimezone;
    private final BookingReferenceGenerator referenceGenerator;
    private final EmailService emailService;

    /**
     * Optional public base URL of the application (e.g. {@code http://localhost:8091}).
     * Used to build manage-booking deep-links in confirmation emails.
     * Configure via {@code app.public-base-url} in application properties.
     * Defaults to empty — manage-booking link omitted from email if blank.
     */
    @Value("${app.public-base-url:}")
    private String publicBaseUrl;

    private static final String MANAGE_NOT_FOUND_MSG =
            "We couldn't find a booking with these details. Please check your reference and last name.";

    private static final int MAX_REF_RETRIES = 5;

    @Transactional
    public Booking createBooking(CreateBookingRequest request) {
        validateDates(request);

        // Acquires a PESSIMISTIC_WRITE (SELECT FOR UPDATE) lock on the car row.
        // Any concurrent createBooking() for the same car will block here until
        // this transaction commits, making the overlap check + insert atomic.
        Car car = carService.getActiveCarByIdForUpdate(request.carId());

        boolean overlaps = bookingRepository
                .existsByCarAndStatusInAndPickupDateTimeLessThanAndDropoffDateTimeGreaterThan(
                        car,
                        List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED),
                        request.dropoffDateTime(),
                        request.pickupDateTime()
                );

        if (overlaps) {
            log.warn("Car {} is not available for pickup={} dropoff={} — overlapping booking exists",
                    request.carId(), request.pickupDateTime(), request.dropoffDateTime());
            throw new CarNotAvailableException(request.carId());
        }

        PriceBreakdown price = pricingService.calculate(car, request.pickupLocation(), request.dropoffLocation(), request.pickupDateTime(), request.dropoffDateTime());

        // Resolve add-ons (ignore inactive ones; requesting a non-existent ID is silently skipped)
        List<Long> requestedAddonIds = request.addonIds() == null ? List.of() : request.addonIds();
        List<Addon> addons = requestedAddonIds.isEmpty()
                ? List.of()
                : addonRepository.findAllById(requestedAddonIds).stream()
                        .filter(Addon::isActive)
                        .toList();

        BigDecimal addonCharge = addons.stream()
                .map(addon -> computeAddonPrice(addon, price.rentalDays()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Unlimited km surcharge — computed server-side, never trusted from frontend.
        MileageOption mileageOption = request.mileageOption() != null
                ? request.mileageOption()
                : MileageOption.INCLUDED;
        BigDecimal unlimitedKmCharge = mileageOption == MileageOption.UNLIMITED
                ? price.unlimitedKmDailyPrice()
                        .multiply(BigDecimal.valueOf(price.rentalDays()))
                        .setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Customer customer = customerService.getOrCreateCustomer(request.customerName(), request.customerEmail(), request.customerPhone());

        String bookingReference = generateUniqueReference();

        Booking booking = Booking.builder()
                .car(car)
                .customer(customer)
                .bookingReference(bookingReference)
                .pickupDateTime(request.pickupDateTime())
                .pickupLocation(request.pickupLocation())
                .dropoffDateTime(request.dropoffDateTime())
                .dropoffLocation(request.dropoffLocation())
                .rentalDays(price.rentalDays())
                .baseDailyPrice(price.baseDailyPrice())
                .discountedDailyPrice(price.effectiveDailyPrice())
                .discountPercentage(price.discountPercentage())
                .rentalCharge(price.rentalCharge())
                .oneWayFee(price.oneWayFee())
                .premiumLocationFee(price.premiumLocationFee())
                .tax(price.tax())
                .addonCharge(addonCharge)
                .totalPrice(price.totalPrice().add(addonCharge).add(unlimitedKmCharge))
                .includedKmSnapshot(price.includedKm())
                .unlimitedKmPriceSnapshot(price.unlimitedKmDailyPrice())
                .mileageOption(mileageOption)
                // Default to BEST_PRICE; STAY_FLEXIBLE will be selectable once the
                // flexibility fee calculation and cancellation policy are implemented.
                .bookingOptionType(BookingOptionType.BEST_PRICE)
                .status(BookingStatus.PENDING)
                .source(BookingSource.WEB)
                // Audit metadata: standard WEB checkout is always an anonymous customer.
                .createdByType(BookingActorType.CUSTOMER_ANONYMOUS)
                .createdChannel(BookingChannel.WEB)
                .build();

        Booking savedBooking = bookingRepository.save(booking);

        // Snapshot each add-on's name and price at booking time so future price
        // changes on the Addon entity cannot retroactively alter this booking's total.
        for (Addon addon : addons) {
            BookingAddon ba = BookingAddon.builder()
                    .booking(savedBooking)
                    .addon(addon)
                    .addonName(addon.getName())
                    .pricingTypeSnapshot(addon.getPricingType())
                    .priceAtBooking(computeAddonPrice(addon, price.rentalDays()))
                    .build();
            bookingAddonRepository.save(ba);
            savedBooking.getBookingAddons().add(ba);
        }

        paymentService.createPendingPayment(savedBooking);
        log.info("Booking created: id={} carId={} customerId={} rentalDays={} mileage={} total={}",
                savedBooking.getId(), car.getId(), customer.getId(),
                price.rentalDays(), mileageOption, savedBooking.getTotalPrice());
        return savedBooking;
    }

    public Booking getBookingById(Long id) {
        return bookingRepository.findById(id).orElseThrow(() -> new BookingNotFoundException(id));
    }

    public Booking findBookingByReferenceAndLastName(String bookingReference, String lastName) {
        Booking booking = bookingRepository.findByBookingReferenceEager(bookingReference == null ? "" : bookingReference.trim())
                .orElseThrow(() -> new BookingNotFoundException(MANAGE_NOT_FOUND_MSG));

        // Compare the stored lastNameNormalized against the normalized input.
        // Using stored DB field avoids runtime string manipulation and enables future DB-level filtering.
        // Always throw the same generic message for "wrong name" and "reference not found"
        // to avoid leaking which condition failed.
        String inputNormalized = NameNormalizer.normalize(lastName);
        String storedNormalized = booking.getCustomer().getLastNameNormalized();
        if (storedNormalized == null || !storedNormalized.equals(inputNormalized)) {
            throw new BookingNotFoundException(MANAGE_NOT_FOUND_MSG);
        }
        return booking;
    }

    @Transactional
    public Booking cancelBooking(Long id) {
        // Lock the booking row to prevent concurrent cancel + completePayment races.
        Booking booking = bookingRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BookingNotFoundException(id));

        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BookingCannotBeCancelledException(id);
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledByType(BookingActorType.ADMIN);
        booking.setCancelledChannel(BookingChannel.ADMIN_PANEL);
        booking.setCancelledAt(businessTimezone.nowBusiness().toInstant());
        booking.setCancellationReason("Cancelled by admin");
        Booking savedBooking = bookingRepository.save(booking);
        paymentService.handleCancellationPayment(savedBooking);
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
        // Step 1: Verify identity and check policy (non-locked read).
        Booking booking = findBookingByReferenceAndLastName(bookingReference, lastName);
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

        // Re-check status under lock in case another request won the race.
        if (locked.getStatus() == BookingStatus.CANCELLED) {
            throw new BookingCannotBeCancelledException("This booking has already been cancelled.");
        }

        locked.setStatus(BookingStatus.CANCELLED);
        locked.setCancelledByType(BookingActorType.CUSTOMER_ANONYMOUS);
        locked.setCancelledChannel(BookingChannel.WEB);
        locked.setCancelledAt(businessTimezone.nowBusiness().toInstant());
        locked.setCancellationReason("Customer requested cancellation from manage booking");
        Booking saved = bookingRepository.save(locked);
        // handleCancellationPayment handles PAID (mock refund → REFUNDED) and
        // PENDING/FAILED (voids the record → CANCELLED).
        paymentService.handleCancellationPayment(saved);
        log.info("Booking cancelled by customer: bookingId={} reference={} status={} cancelledByType={} cancelledChannel={}",
                saved.getId(), bookingReference, saved.getStatus(),
                saved.getCancelledByType(), saved.getCancelledChannel());
        return saved;
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

        Payment payment = paymentService.processLatestPaymentForBooking(booking, paymentMethodId);

        if (payment.getStatus() == PaymentStatus.PAID) {
            booking.setStatus(BookingStatus.CONFIRMED);
        } else {
            booking.setStatus(BookingStatus.FAILED);
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
        Booking booking = findBookingByReferenceAndLastName(bookingReference, lastName);
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

    private String generateUniqueReference() {
        for (int attempt = 0; attempt < MAX_REF_RETRIES; attempt++) {
            String ref = referenceGenerator.generate();
            if (!bookingRepository.existsByBookingReference(ref)) {
                return ref;
            }
            log.warn("Booking reference collision on attempt {}: {}", attempt + 1, ref);
        }
        throw new IllegalStateException(
                "Failed to generate unique booking reference after " + MAX_REF_RETRIES + " attempts");
    }

    private void validateDates(CreateBookingRequest request) {
        if (request.pickupDateTime() == null || request.dropoffDateTime() == null) {
            throw new InvalidBookingDateException("Start date and end date are required");
        }
        if (!request.dropoffDateTime().isAfter(request.pickupDateTime())) {
            throw new InvalidBookingDateException("End date must be after start date");
        }
        if (request.pickupDateTime().isBefore(businessTimezone.nowBusinessLocal().plusHours(1))) {
            throw new InvalidBookingDateException("Pickup must be at least 1 hour from now");
        }
    }

    private BigDecimal computeAddonPrice(Addon addon, int rentalDays) {
        if (addon.getPricingType() == AddonPricingType.DAILY) {
            return addon.getPrice()
                    .multiply(BigDecimal.valueOf(rentalDays))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        return addon.getPrice().setScale(2, RoundingMode.HALF_UP);
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