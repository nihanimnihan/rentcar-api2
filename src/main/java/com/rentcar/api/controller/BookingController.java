package com.rentcar.api.controller;

import com.rentcar.api.dto.booking.BookingResponse;
import com.rentcar.api.dto.booking.CreateBookingRequest;
import com.rentcar.api.dto.payment.ProcessPaymentRequest;
import com.rentcar.api.mapper.BookingMapper;
import com.rentcar.api.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final BookingMapper bookingMapper;

    @PostMapping
    public BookingResponse createBooking(@Valid @RequestBody CreateBookingRequest request) {
        return bookingMapper.toResponse(bookingService.createBooking(request));
    }

    @GetMapping("/{id}")
    public BookingResponse getBookingById(@PathVariable Long id) {
        return bookingMapper.toResponse(bookingService.getBookingById(id));
    }

    @PostMapping("/{id}/cancel")
    public BookingResponse cancelBooking(@PathVariable Long id) {
        return bookingMapper.toResponse(bookingService.cancelBooking(id));
    }

    @PostMapping("/{id}/payments/process")
    public BookingResponse processPayment(@PathVariable Long id,
                                          @Valid @RequestBody ProcessPaymentRequest request) {
        return bookingMapper.toResponse(bookingService.completePayment(id, request.paymentMethodId()));
    }
}