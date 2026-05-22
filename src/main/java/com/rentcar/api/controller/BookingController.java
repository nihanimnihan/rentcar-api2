package com.rentcar.api.controller;

import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.dto.booking.BookingResponse;
import com.rentcar.api.dto.booking.CreateBookingRequest;
import com.rentcar.api.mapper.BookingMapper;
import com.rentcar.api.service.BookingService;
import com.rentcar.api.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final BookingMapper bookingMapper;
    private final PaymentService paymentService;

    @PostMapping
    public BookingResponse createBooking(@Valid @RequestBody CreateBookingRequest request) {
        return bookingMapper.toResponse(bookingService.createBooking(request));
    }

    @GetMapping("/{id}")
    public BookingResponse getBookingById(@PathVariable Long id) {
        Booking booking = bookingService.getBookingById(id);
        return enrichWithPayment(bookingMapper.toResponse(booking), booking);
    }

    @GetMapping("/manage")
    public BookingResponse manageBooking(@RequestParam String bookingReference,
                                         @RequestParam String lastName) {
        Booking booking = bookingService.findBookingByReferenceAndLastName(bookingReference, lastName);
        return enrichWithPayment(bookingMapper.toResponse(booking), booking);
    }

    /** Enriches a mapper-produced response with the latest payment status and method. */
    private BookingResponse enrichWithPayment(BookingResponse base, Booking booking) {
        return paymentService.findLatestPayment(booking)
                .map(p -> base.withPayment(p.getStatus(), p.getMethod()))
                .orElse(base);
    }
}