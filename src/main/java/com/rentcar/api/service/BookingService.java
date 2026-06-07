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
import com.rentcar.api.dto.booking.CreateBookingRequest;
import com.rentcar.api.dto.pricing.PriceBreakdown;
import com.rentcar.api.exception.BookingNotFoundException;
import com.rentcar.api.exception.CarNotAvailableException;
import com.rentcar.api.exception.InvalidBookingDateException;
import com.rentcar.api.repository.AddonRepository;
import com.rentcar.api.repository.BookingAddonRepository;
import com.rentcar.api.repository.BookingRepository;
import com.rentcar.api.util.BookingReferenceGenerator;
import com.rentcar.api.util.BusinessTimezone;
import com.rentcar.api.util.NameNormalizer;
import com.rentcar.api.util.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
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
                .expiresAt(appClock.nowUtc().plus(Duration.ofMinutes(15)))
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


}