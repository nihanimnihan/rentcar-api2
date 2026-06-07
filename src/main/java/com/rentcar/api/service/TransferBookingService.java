package com.rentcar.api.service;

import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.domain.booking.BookingSource;
import com.rentcar.api.domain.booking.BookingStatus;
import com.rentcar.api.domain.car.Car;
import com.rentcar.api.domain.customer.Customer;
import com.rentcar.api.domain.transfer.ChauffeurCategory;
import com.rentcar.api.dto.transfer.CreateTransferBookingRequest;
import com.rentcar.api.dto.transfer.TransferBookingResponse;
import com.rentcar.api.exception.ChauffeurCategoryNotFoundException;
import com.rentcar.api.exception.InvalidTransferRequestException;
import com.rentcar.api.exception.NoChauffeurCarAvailableException;
import com.rentcar.api.repository.BookingRepository;
import com.rentcar.api.repository.CarRepository;
import com.rentcar.api.repository.ChauffeurCategoryRepository;
import com.rentcar.api.util.BookingReferenceGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferBookingService {

    private final BookingRepository bookingRepository;
    private final CarRepository carRepository;
    private final ChauffeurCategoryRepository chauffeurCategoryRepository;
    private final CarService carService;
    private final CustomerService customerService;
    private final BookingReferenceGenerator referenceGenerator;
    private final com.rentcar.api.util.AppClock appClock;

    /**
     * Creates a transfer booking for the first available chauffeur car in the
     * requested category.
     *
     * Pattern mirrors BookingService.createBooking():
     *  1. Validate the request.
     *  2. Find available cars (no lock — just a candidate list).
     *  3. Acquire a PESSIMISTIC_WRITE lock on the chosen car.
     *  4. Re-check overlap atomically inside the transaction.
     *  5. Persist the booking.
     */
    @Transactional
    public TransferBookingResponse createTransferBooking(CreateTransferBookingRequest request) {
        LocalDateTime dropoffDateTime = request.pickupDateTime().plusHours(request.durationHours());

        ChauffeurCategory category = chauffeurCategoryRepository
                .findById(request.categoryId())
                .filter(ChauffeurCategory::isActive)
                .orElseThrow(() -> new ChauffeurCategoryNotFoundException(request.categoryId()));

        if (request.passengerCount() != null && request.passengerCount() > category.getSeats()) {
            throw new InvalidTransferRequestException(
                    "Passenger count " + request.passengerCount()
                    + " exceeds category seat capacity of " + category.getSeats());
        }

        // Candidate cars — ordered by hourlyPrice ASC, filtered by available seats.
        List<Car> candidates = carRepository.findAvailableChauffeurCars(
                category, request.pickupDateTime(), dropoffDateTime, request.passengerCount(), appClock.nowUtc());

        if (candidates.isEmpty()) {
            throw new NoChauffeurCarAvailableException(request.categoryId());
        }

        // Lock the first candidate and re-verify availability atomically.
        Car car = carService.getActiveCarByIdForUpdate(candidates.get(0).getId());

        boolean overlaps = bookingRepository
                .existsByCarAndActiveStatusAndPickupDateTimeLessThanAndDropoffDateTimeGreaterThan(
                        car,
                        dropoffDateTime,
                        request.pickupDateTime(),
                        appClock.nowUtc()
                );

        if (overlaps) {
            // Another request won the race for the same car.
            throw new NoChauffeurCarAvailableException(request.categoryId());
        }

        Customer customer = customerService.getOrCreateCustomer(
                request.customerName(), request.customerEmail(), request.customerPhone());

        BigDecimal hourlyPrice = car.getHourlyPrice() != null ? car.getHourlyPrice() : BigDecimal.ZERO;
        BigDecimal totalPrice = hourlyPrice
                .multiply(BigDecimal.valueOf(request.durationHours()))
                .setScale(2, RoundingMode.HALF_UP);

        int passengerCount = request.passengerCount() != null ? request.passengerCount() : 1;

        Booking booking = Booking.builder()
                .car(car)
                .customer(customer)
                .bookingReference(referenceGenerator.generate())
                .pickupDateTime(request.pickupDateTime())
                .dropoffDateTime(dropoffDateTime)
                // durationHours stored as rentalDays — the semantics differ for transfer bookings
                .rentalDays(request.durationHours())
                .baseDailyPrice(BigDecimal.ZERO)
                .discountedDailyPrice(BigDecimal.ZERO)
                .discountPercentage(BigDecimal.ZERO)
                .rentalCharge(BigDecimal.ZERO)
                .oneWayFee(BigDecimal.ZERO)
                .premiumLocationFee(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .totalPrice(totalPrice)
                .includedKmSnapshot(0)
                .unlimitedKmPriceSnapshot(BigDecimal.ZERO)
                .passengers(passengerCount)
                .notes(request.notes())
                .status(BookingStatus.PENDING)
                .expiresAt(appClock.nowUtc().plus(java.time.Duration.ofMinutes(15)))
                .source(BookingSource.TRANSFER)
                .build();

        Booking saved = bookingRepository.save(booking);

        log.info("Transfer booking created: id={} carId={} category={} customerId={} total={}",
                saved.getId(), car.getId(), category.getCode(), customer.getId(), totalPrice);

        return toResponse(saved, category, car, hourlyPrice);
    }

    private TransferBookingResponse toResponse(
            Booking booking,
            ChauffeurCategory category,
            Car car,
            BigDecimal hourlyPrice) {

        return new TransferBookingResponse(
                booking.getId(),
                booking.getStatus(),
                booking.getCustomer().getFullName(),
                booking.getCustomer().getEmail(),
                booking.getPickupDateTime(),
                booking.getDropoffDateTime(),
                booking.getRentalDays(),
                category.getCode(),
                category.getName(),
                car.getBrand(),
                car.getModel(),
                booking.getPassengers() != null ? booking.getPassengers() : 1,
                hourlyPrice,
                booking.getTotalPrice(),
                booking.getNotes()
        );
    }
}
