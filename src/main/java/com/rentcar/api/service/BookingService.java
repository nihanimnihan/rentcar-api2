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
import com.rentcar.api.domain.booking.CancellationPolicyType;
import com.rentcar.api.domain.booking.MileageOption;
import com.rentcar.api.domain.booking.RentalBookingDetails;
import com.rentcar.api.domain.car.Car;
import com.rentcar.api.domain.customer.Customer;
import com.rentcar.api.dto.booking.CreateBookingRequest;
import com.rentcar.api.dto.admin.AdminCreateBookingRequest;
import com.rentcar.api.dto.pricing.PriceBreakdown;
import com.rentcar.api.exception.BookingNotFoundException;
import com.rentcar.api.exception.CarNotAvailableException;
import com.rentcar.api.exception.InvalidBookingDateException;
import com.rentcar.api.repository.AddonRepository;
import com.rentcar.api.repository.BookingAddonRepository;
import com.rentcar.api.repository.BookingRepository;
import com.rentcar.api.util.BookingReferenceGenerator;
import com.rentcar.api.util.BusinessTimezone;
import com.rentcar.api.util.LanguageNormalizer;
import com.rentcar.api.util.NameNormalizer;
import com.rentcar.api.util.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
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
    private final AppClock appClock;
    private final ManageBookingTokenService manageBookingTokenService;

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
                .existsByCarAndActiveStatusAndPickupDateTimeLessThanAndDropoffDateTimeGreaterThan(
                        car,
                        request.dropoffDateTime(),
                        request.pickupDateTime(),
                        appClock.nowUtc()
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

        String language = LanguageNormalizer.normalizeOrDefault(request.language());
        Customer customer = customerService.getOrCreateCustomer(
                request.customerName(),
                request.customerEmail(),
                request.customerPhone(),
                language);

        String bookingReference = generateUniqueReference();

        Booking booking = Booking.builder()
                .car(car)
                .customer(customer)
                .bookingReference(bookingReference)
                .pickupDateTime(request.pickupDateTime())
                .pickupLocation(request.pickupLocation())
                .pickupAddress(null)
                .pickupPlaceId(null)
                .dropoffDateTime(request.dropoffDateTime())
                .dropoffLocation(request.dropoffLocation())
                .dropoffAddress(null)
                .dropoffPlaceId(null)
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
                .bookingOptionDailyFee(BigDecimal.ZERO)
                .cancellationPolicyType(CancellationPolicyType.STRICT)
                .status(BookingStatus.PENDING)
                .expiresAt(appClock.nowUtc().plus(Duration.ofMinutes(15)))
                .checkoutSessionToken(generateUniqueCheckoutSessionToken())
                .language(language)
                .source(BookingSource.WEB)
                // Audit metadata: standard WEB checkout is always an anonymous customer.
                .createdByType(BookingActorType.CUSTOMER_ANONYMOUS)
                .createdChannel(BookingChannel.WEB)
                .build();

        booking.attachRentalDetails(RentalBookingDetails.builder()
                .rentalDays(price.rentalDays())
                .baseDailyPrice(price.baseDailyPrice())
                .discountedDailyPrice(price.effectiveDailyPrice())
                .discountPercentage(price.discountPercentage())
                .rentalCharge(price.rentalCharge())
                .oneWayFee(price.oneWayFee())
                .premiumLocationFee(price.premiumLocationFee())
                .tax(price.tax())
                .addonCharge(addonCharge)
                .includedKmSnapshot(price.includedKm())
                .unlimitedKmPriceSnapshot(price.unlimitedKmDailyPrice())
                .mileageOption(mileageOption)
                .bookingOptionType(BookingOptionType.BEST_PRICE)
                .bookingOptionDailyFee(BigDecimal.ZERO)
                .cancellationPolicyType(CancellationPolicyType.STRICT)
                .build());

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

    @Transactional
    public Booking createAdminBooking(AdminCreateBookingRequest request) {
        LocalDateTime pickupDateTime = LocalDateTime.of(request.pickupDate(), request.pickupTime());
        LocalDateTime dropoffDateTime = LocalDateTime.of(request.returnDate(), request.returnTime());
        CreateBookingRequest normalRequest = new CreateBookingRequest(
                request.vehicleId(),
                (trim(request.firstName()) + " " + trim(request.lastName())).trim(),
                trim(request.email()),
                normalizeAdminPhone(request.phoneCountryCode(), request.phoneNumber()),
                pickupDateTime,
                dropoffDateTime,
                trim(request.pickupLocation()),
                trim(request.returnLocation()),
                request.addonIds(),
                MileageOption.INCLUDED,
                null
        );

        validateDates(normalRequest);
        Car car = carService.getActiveCarByIdForUpdate(normalRequest.carId());

        boolean overlaps = bookingRepository
                .existsByCarAndActiveStatusAndPickupDateTimeLessThanAndDropoffDateTimeGreaterThan(
                        car,
                        normalRequest.dropoffDateTime(),
                        normalRequest.pickupDateTime(),
                        appClock.nowUtc()
                );
        if (overlaps) {
            log.warn("Admin booking rejected: car {} unavailable pickup={} dropoff={}",
                    normalRequest.carId(), normalRequest.pickupDateTime(), normalRequest.dropoffDateTime());
            throw new CarNotAvailableException(normalRequest.carId());
        }

        PriceBreakdown price = pricingService.calculate(
                car,
                normalRequest.pickupLocation(),
                normalRequest.dropoffLocation(),
                normalRequest.pickupDateTime(),
                normalRequest.dropoffDateTime());
        List<Addon> addons = resolveAdminAddons(request.addonIds());
        BigDecimal addonCharge = addons.stream()
                .map(addon -> computeAddonPrice(addon, price.rentalDays()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expectedTotal = price.totalPrice().add(addonCharge).setScale(2, RoundingMode.HALF_UP);
        BigDecimal submittedTotal = request.totalPrice().setScale(2, RoundingMode.HALF_UP);
        String notes = appendAdminOverrideNote(trimToNull(request.internalNote()), expectedTotal, submittedTotal);

        Customer customer = customerService.getOrCreateCustomer(
                normalRequest.customerName(),
                normalRequest.customerEmail(),
                normalRequest.customerPhone(),
                LanguageNormalizer.DEFAULT_LANGUAGE);
        Booking booking = Booking.builder()
                .car(car)
                .customer(customer)
                .bookingReference(generateUniqueReference())
                .pickupDateTime(normalRequest.pickupDateTime())
                .pickupLocation(normalRequest.pickupLocation())
                .pickupAddress(trimToNull(request.pickupAddress()))
                .pickupPlaceId(trimToNull(request.pickupPlaceId()))
                .dropoffDateTime(normalRequest.dropoffDateTime())
                .dropoffLocation(normalRequest.dropoffLocation())
                .dropoffAddress(trimToNull(request.returnAddress()))
                .dropoffPlaceId(trimToNull(request.returnPlaceId()))
                .rentalDays(price.rentalDays())
                .baseDailyPrice(price.baseDailyPrice())
                .discountedDailyPrice(price.effectiveDailyPrice())
                .discountPercentage(price.discountPercentage())
                .rentalCharge(price.rentalCharge())
                .oneWayFee(price.oneWayFee())
                .premiumLocationFee(price.premiumLocationFee())
                .tax(price.tax())
                .addonCharge(addonCharge)
                .totalPrice(submittedTotal)
                .includedKmSnapshot(price.includedKm())
                .unlimitedKmPriceSnapshot(price.unlimitedKmDailyPrice())
                .mileageOption(MileageOption.INCLUDED)
                .bookingOptionType(BookingOptionType.BEST_PRICE)
                .bookingOptionDailyFee(BigDecimal.ZERO)
                .cancellationPolicyType(CancellationPolicyType.STRICT)
                .status(BookingStatus.CONFIRMED)
                .expiresAt(null)
                .checkoutSessionToken(null)
                .language(LanguageNormalizer.DEFAULT_LANGUAGE)
                .source(BookingSource.OFFICE)
                .notes(notes)
                .createdByType(BookingActorType.ADMIN)
                .createdChannel(BookingChannel.ADMIN_PANEL)
                .build();
        booking.attachRentalDetails(RentalBookingDetails.builder()
                .rentalDays(price.rentalDays())
                .baseDailyPrice(price.baseDailyPrice())
                .discountedDailyPrice(price.effectiveDailyPrice())
                .discountPercentage(price.discountPercentage())
                .rentalCharge(price.rentalCharge())
                .oneWayFee(price.oneWayFee())
                .premiumLocationFee(price.premiumLocationFee())
                .tax(price.tax())
                .addonCharge(addonCharge)
                .includedKmSnapshot(price.includedKm())
                .unlimitedKmPriceSnapshot(price.unlimitedKmDailyPrice())
                .mileageOption(MileageOption.INCLUDED)
                .bookingOptionType(BookingOptionType.BEST_PRICE)
                .bookingOptionDailyFee(BigDecimal.ZERO)
                .cancellationPolicyType(CancellationPolicyType.STRICT)
                .build());

        Booking savedBooking = bookingRepository.save(booking);
        snapshotAddons(savedBooking, addons, price.rentalDays());
        paymentService.createPaidAdminPayment(savedBooking, request.paymentSource());
        log.info("Admin booking created: id={} carId={} customerId={} total={} paymentSource={}",
                savedBooking.getId(), car.getId(), customer.getId(), savedBooking.getTotalPrice(), request.paymentSource());
        return savedBooking;
    }

    private String generateUniqueCheckoutSessionToken() {
        java.util.Base64.Encoder enc = java.util.Base64.getUrlEncoder().withoutPadding();
        java.security.SecureRandom rnd = new java.security.SecureRandom();
        for (int attempt = 0; attempt < 10; attempt++) {
            byte[] bytes = new byte[32];
            rnd.nextBytes(bytes);
            String token = enc.encodeToString(bytes);
            if (!bookingRepository.existsByCheckoutSessionToken(token)) {
                return token;
            }
        }
        throw new IllegalStateException("Failed to generate unique checkout session token");
    }

    public Booking getBookingById(Long id) {
        return bookingRepository.findByIdWithDetails(id).orElseThrow(() -> new BookingNotFoundException(id));
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

    public Booking findBookingByManageToken(String token) {
        return manageBookingTokenService.findBookingByToken(token);
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

    private List<Addon> resolveAdminAddons(List<Long> requestedAddonIds) {
        if (requestedAddonIds == null || requestedAddonIds.isEmpty()) {
            return List.of();
        }
        List<Addon> addons = addonRepository.findAllById(requestedAddonIds);
        if (addons.size() != requestedAddonIds.stream().distinct().count()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more selected add-ons do not exist.");
        }
        List<Addon> inactive = addons.stream().filter(addon -> !addon.isActive()).toList();
        if (!inactive.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected add-on is inactive.");
        }
        return addons;
    }

    private void snapshotAddons(Booking savedBooking, List<Addon> addons, int rentalDays) {
        for (Addon addon : addons) {
            BookingAddon ba = BookingAddon.builder()
                    .booking(savedBooking)
                    .addon(addon)
                    .addonName(addon.getName())
                    .pricingTypeSnapshot(addon.getPricingType())
                    .priceAtBooking(computeAddonPrice(addon, rentalDays))
                    .build();
            bookingAddonRepository.save(ba);
            savedBooking.getBookingAddons().add(ba);
        }
    }

    private String normalizeAdminPhone(String phoneCountryCode, String phoneNumber) {
        return trim(phoneCountryCode).replace(" ", "") + trim(phoneNumber).replaceAll("\\s+", "");
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        String trimmed = trim(value);
        if (trimmed.isBlank()) {
            return null;
        }
        return trimmed.length() > 1000 ? trimmed.substring(0, 1000) : trimmed;
    }

    private String appendAdminOverrideNote(String note, BigDecimal expectedTotal, BigDecimal submittedTotal) {
        if (expectedTotal.compareTo(submittedTotal) == 0) {
            return note;
        }
        String marker = "Manual price override active: calculated "
                + expectedTotal.toPlainString()
                + " EUR, final "
                + submittedTotal.toPlainString()
                + " EUR.";
        if (note == null || note.isBlank()) {
            return marker;
        }
        return (note + "\n" + marker).trim();
    }


}
