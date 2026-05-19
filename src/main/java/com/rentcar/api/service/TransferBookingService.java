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

        if (request.passengers() != null && request.passengers() > category.getSeats()) {
            throw new InvalidTransferRequestException(
                    "Passenger count " + request.passengers()
                    + " exceeds category seat capacity of " + category.getSeats());
        }

        // Candidate cars — ordered by hourlyPrice ASC so we always pick the cheapest first.
        List<Car> candidates = carRepository.findAvailableChauffeurCars(
                category, request.pickupDateTime(), dropoffDateTime);

        if (candidates.isEmpty()) {
            throw new NoChauffeurCarAvailableException(request.categoryId());
        }

        // Lock the first candidate and re-verify availability atomically.
        Car car = carService.getActiveCarByIdForUpdate(candidates.get(0).getId());

        boolean overlaps = bookingRepository
                .existsByCarAndStatusInAndPickupDateTimeLessThanAndDropoffDateTimeGreaterThan(
                        car,
                        List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED),
                        dropoffDateTime,
                        request.pickupDateTime()
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

        int passengers = request.passengers() != null ? request.passengers() : 1;

        Booking booking = Booking.builder()
                .car(car)
                .customer(customer)
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
                .passengers(passengers)
                .notes(request.notes())
                .status(BookingStatus.PENDING)
                .source(BookingSource.TRANSFER)
                .build();

        Booking saved = bookingRepository.save(booking);

        log.info("Transfer booking created: id={} carId={} category={} customerId={} total={}",
                saved.getId(), car.getId(), category.getCode(), customer.getId(), totalPrice);

        return toResponse(saved, category, car, customer, passengers, hourlyPrice);
    }

    private TransferBookingResponse toResponse(
            Booking booking,
            ChauffeurCategory category,
            Car car,
            Customer customer,
            int passengers,
            BigDecimal hourlyPrice) {

        return new TransferBookingResponse(
                booking.getId(),
                booking.getStatus(),
                customer.getFullName(),
                customer.getEmail(),
                booking.getPickupDateTime(),
                booking.getDropoffDateTime(),
                booking.getRentalDays(),
                category.getCode(),
                category.getName(),
                car.getBrand(),
                car.getModel(),
                passengers,
                hourlyPrice,
                booking.getTotalPrice(),
                booking.getNotes()
        );
    }
}
