package com.rentcar.api.controller;

import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.dto.booking.BookingResponse;
import com.rentcar.api.dto.payment.CreatePaymentIntentRequest;
import com.rentcar.api.dto.payment.PaymentIntentResponse;
import com.rentcar.api.dto.payment.PaymentResponse;
import com.rentcar.api.dto.payment.ProcessPaymentRequest;
import com.rentcar.api.mapper.BookingMapper;
import com.rentcar.api.mapper.PaymentMapper;
import com.rentcar.api.service.BookingPaymentService;
import com.rentcar.api.service.BookingService;
import com.rentcar.api.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingPaymentController {

    private final BookingPaymentService bookingPaymentService;
    private final BookingService bookingService;
    private final BookingMapper bookingMapper;
    private final PaymentService paymentService;
    private final PaymentMapper paymentMapper;

    @PostMapping("/{id}/payments/intent")
    public PaymentIntentResponse createPaymentIntent(
            @PathVariable Long id,
            @RequestBody(required = false) CreatePaymentIntentRequest request) {
        return bookingPaymentService.createPaymentIntent(id, request);
    }

    @PostMapping("/{id}/payments/process")
    public BookingResponse processPayment(@PathVariable Long id,
                                          @Valid @RequestBody ProcessPaymentRequest request) {
        return bookingMapper.toResponse(bookingPaymentService.completePayment(id, request.paymentMethodId()));
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
}
