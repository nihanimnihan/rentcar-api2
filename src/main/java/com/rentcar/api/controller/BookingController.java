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
    public org.springframework.http.ResponseEntity<BookingResponse> createBooking(@Valid @RequestBody CreateBookingRequest request) {
        var booking = bookingService.createBooking(request);
        BookingResponse response = bookingMapper.toResponse(booking);
        String token = booking.getCheckoutSessionToken();
        // Return token in a response header so it is only available to the creator and not included
        // in standard BookingResponse bodies used by other APIs or admin tooling.
        org.springframework.http.ResponseEntity.BodyBuilder builder = org.springframework.http.ResponseEntity.ok();
        if (token != null) builder.header("X-Checkout-Session-Token", token);
        return builder.body(response);
    }

    @GetMapping("/{id}")
    public BookingResponse getBookingById(@PathVariable Long id,
                                          @RequestHeader(value = "X-Checkout-Session-Token", required = false) String token) {
        Booking booking = bookingService.getBookingById(id);
        // If booking is still owned by a checkout session, require the token to view via numeric id.
        if (booking.getCheckoutSessionToken() != null) {
            if (token == null || !token.equals(booking.getCheckoutSessionToken())) {
                throw new com.rentcar.api.exception.CheckoutSessionUnauthorizedException("Missing or invalid checkout session token");
            }
        }
        return enrichWithPayment(bookingMapper.toResponse(booking), booking);
    }

    @GetMapping("/manage")
    public BookingResponse manageBooking(@RequestParam String bookingReference,
                                         @RequestParam String lastName) {
        Booking booking = bookingService.findBookingByReferenceAndLastName(bookingReference, lastName);
        return enrichWithPayment(bookingMapper.toResponse(booking), booking);
    }

    @GetMapping("/manage/token")
    public BookingResponse manageBookingByToken(@RequestParam String token) {
        Booking booking = bookingService.findBookingByManageToken(token);
        return enrichWithPayment(bookingMapper.toResponse(booking), booking);
    }

    /** Enriches a mapper-produced response with the latest payment status and method. */
    private BookingResponse enrichWithPayment(BookingResponse base, Booking booking) {
        return paymentService.findLatestPayment(booking)
                .map(p -> base.withPayment(p.getStatus(), p.getMethod()))
                .orElse(base);
    }
}
