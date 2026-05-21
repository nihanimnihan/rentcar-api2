package com.rentcar.api.service;

import com.rentcar.api.domain.addon.Addon;
import com.rentcar.api.domain.addon.AddonPricingType;
import com.rentcar.api.domain.addon.BookingAddon;
import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.domain.booking.BookingSource;
import com.rentcar.api.domain.booking.BookingStatus;
import com.rentcar.api.domain.booking.MileageOption;
import com.rentcar.api.domain.car.Car;
import com.rentcar.api.domain.customer.Customer;
import com.rentcar.api.domain.payment.Payment;
import com.rentcar.api.domain.payment.PaymentStatus;
import com.rentcar.api.dto.booking.CreateBookingRequest;
import com.rentcar.api.dto.pricing.PriceBreakdown;
import com.rentcar.api.exception.BookingCannotBeCancelledException;
import com.rentcar.api.exception.BookingNotFoundException;
import com.rentcar.api.exception.CarNotAvailableException;
import com.rentcar.api.exception.InvalidBookingDateException;
import com.rentcar.api.exception.InvalidBookingStateException;
import com.rentcar.api.repository.AddonRepository;
import com.rentcar.api.repository.BookingAddonRepository;
import com.rentcar.api.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import com.rentcar.api.util.BookingReferenceGenerator;
import com.rentcar.api.util.BusinessTimezone;
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
                .dropoffDateTime(request.dropoffDateTime())
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
                .status(BookingStatus.PENDING)
                .source(BookingSource.WEB)
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

    @Transactional
    public Booking cancelBooking(Long id) {
        // Lock the booking row to prevent concurrent cancel + completePayment races.
        Booking booking = bookingRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BookingNotFoundException(id));

        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BookingCannotBeCancelledException(id);
        }

        booking.setStatus(BookingStatus.CANCELLED);
        Booking savedBooking = bookingRepository.save(booking);
        paymentService.cancelPaymentForBooking(savedBooking);
        log.info("Booking cancelled: id={}", id);
        return savedBooking;
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
        return saved;
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