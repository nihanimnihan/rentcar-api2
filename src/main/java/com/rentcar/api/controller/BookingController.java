package com.rentcar.api.controller;

import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.dto.booking.BookingResponse;
import com.rentcar.api.dto.booking.CreateBookingRequest;
import com.rentcar.api.dto.payment.CreatePaymentIntentRequest;
import com.rentcar.api.dto.payment.PaymentIntentResponse;
import com.rentcar.api.dto.payment.PaymentResponse;
import com.rentcar.api.dto.payment.ProcessPaymentRequest;
import com.rentcar.api.mapper.BookingMapper;
import com.rentcar.api.mapper.PaymentMapper;
import com.rentcar.api.service.BookingService;
import com.rentcar.api.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final BookingMapper bookingMapper;
    private final PaymentService paymentService;
    private final PaymentMapper paymentMapper;

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

    @PostMapping("/{id}/payments/intent")
    public PaymentIntentResponse createPaymentIntent(
            @PathVariable Long id,
            @RequestBody(required = false) CreatePaymentIntentRequest request) {
        return bookingService.createPaymentIntent(id, request);
    }

    @PostMapping("/{id}/payments/process")
    public BookingResponse processPayment(@PathVariable Long id,
                                          @Valid @RequestBody ProcessPaymentRequest request) {
        return bookingMapper.toResponse(bookingService.completePayment(id, request.paymentMethodId()));
    }

    /**
     * Returns the full payment history for a booking, newest first (admin only).
     *
     * <p>A booking accumulates one record per attempt: FAILED attempts are preserved
     * alongside the eventual PAID record so operators can diagnose retry patterns.
     * Useful for verifying the payment lifecycle in admin tooling and tests.
     */
    @GetMapping("/{id}/payments")
    public List<PaymentResponse> getPaymentHistory(@PathVariable Long id) {
        Booking booking = bookingService.getBookingById(id);
        return paymentService.getPaymentsForBooking(booking)
                .stream().map(paymentMapper::toResponse).toList();
    }

    /** Enriches a mapper-produced response with the latest payment status and method. */
    private BookingResponse enrichWithPayment(BookingResponse base, Booking booking) {
        return paymentService.findLatestPayment(booking)
                .map(p -> base.withPayment(p.getStatus(), p.getMethod()))
                .orElse(base);
    }
}