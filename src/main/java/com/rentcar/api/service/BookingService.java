package com.rentcar.api.service;

import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.domain.booking.BookingSource;
import com.rentcar.api.domain.booking.BookingStatus;
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
import com.rentcar.api.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final CustomerService customerService;
    private final PaymentService paymentService;
    private final PricingService pricingService;
    private final CarService carService;

    public Booking createBooking(CreateBookingRequest request) {
        validateDates(request);

        Car car = carService.getActiveCarById(request.carId());

        boolean overlaps = bookingRepository
                .existsByCarAndStatusInAndPickupDateTimeLessThanAndDropoffDateTimeGreaterThan(
                        car,
                        List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED),
                        request.dropoffDateTime(), // existing pickup <= new dropoff
                        request.pickupDateTime()   // existing dropoff >= new pickup
                );

        if (overlaps) {
            throw new CarNotAvailableException(request.carId());
        }

        PriceBreakdown price = pricingService.calculate(car, request.pickupLocation(), request.dropoffLocation(), request.pickupDateTime(), request.dropoffDateTime());

        Customer customer = customerService.getOrCreateCustomer(request.customerName(), request.customerEmail(), request.customerPhone());

        Booking booking = Booking.builder()
                .car(car)
                .customer(customer)
                .pickupDateTime(request.pickupDateTime())
                .dropoffDateTime(request.dropoffDateTime())
                .rentalDays(price.rentalDays())
                .baseDailyPrice(price.baseDailyPrice())
                .discountedDailyPrice(price.discountedDailyPrice())
                .rentalCharge(price.rentalCharge())
                .oneWayFee(price.oneWayFee())
                .premiumLocationFee(price.premiumLocationFee())
                .tax(price.tax())
                .totalPrice(price.totalPrice())
                .status(BookingStatus.PENDING)
                .source(BookingSource.WEB)
                .build();

        Booking savedBooking = bookingRepository.save(booking);
        paymentService.createPendingOnlinePayment(savedBooking);
        return savedBooking;
    }

    public Booking getBookingById(Long id) {
        return bookingRepository.findById(id).orElseThrow(() -> new BookingNotFoundException(id));
    }

    public Booking cancelBooking(Long id) {
        Booking booking = bookingRepository.findById(id).orElseThrow(() -> new BookingNotFoundException(id));

        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BookingCannotBeCancelledException(id);
        }

        booking.setStatus(BookingStatus.CANCELLED);
        Booking savedBooking = bookingRepository.save(booking);
        paymentService.cancelPaymentForBooking(savedBooking);
        return savedBooking;
    }

    private void validateDates(CreateBookingRequest request) {
        if (request.pickupDateTime() == null || request.dropoffDateTime() == null) {
            throw new InvalidBookingDateException("Start date and end date are required");
        }
        if (!request.dropoffDateTime().isAfter(request.pickupDateTime())) {
            throw new InvalidBookingDateException("End date must be after start date");
        }
        if (request.pickupDateTime().isBefore(LocalDateTime.now().plusHours(1))) {
            throw new InvalidBookingDateException("Pickup must be at least 1 hour from now");
        }
    }

    public Booking completePayment(Long bookingId) {
        Booking booking = getBookingById(bookingId);

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new InvalidBookingStateException("Only pending bookings can complete payment");
        }

        Payment payment = paymentService.processLatestPaymentForBooking(booking);

        if (payment.getStatus() == PaymentStatus.PAID) {
            booking.setStatus(BookingStatus.CONFIRMED);
        } else {
            booking.setStatus(BookingStatus.FAILED);
        }
        return bookingRepository.save(booking);
    }

}