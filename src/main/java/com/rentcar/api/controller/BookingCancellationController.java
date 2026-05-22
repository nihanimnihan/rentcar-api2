package com.rentcar.api.controller;

import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.dto.booking.BookingResponse;
import com.rentcar.api.dto.booking.CancellationPolicyResponse;
import com.rentcar.api.dto.booking.ManageCancelRequest;
import com.rentcar.api.mapper.BookingMapper;
import com.rentcar.api.service.BookingService;
import com.rentcar.api.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingCancellationController {

    private final BookingService bookingService;
    private final BookingMapper bookingMapper;
    private final PaymentService paymentService;

    @GetMapping("/manage/cancellation-policy")
    public CancellationPolicyResponse cancellationPolicy(@RequestParam String bookingReference,
                                                         @RequestParam String lastName) {
        return bookingService.getCancellationPolicy(bookingReference, lastName);
    }

    /**
     * Customer-facing cancellation. Identity verified by bookingReference + lastName.
     * Evaluates cancellation policy and returns CANCELLED booking on success.
     * Returns 409 if the booking is not cancellable (already cancelled, past pickup, etc.).
     */
    @PostMapping("/manage/cancel")
    public BookingResponse cancelByReference(@Valid @RequestBody ManageCancelRequest request) {
        Booking booking = bookingService.cancelBookingByReference(
                request.bookingReference(), request.lastName());
        return enrichWithPayment(bookingMapper.toResponse(booking), booking);
    }

    /** Admin-only cancellation by numeric id. */
    @PostMapping("/{id}/cancel")
    public BookingResponse cancelBooking(@PathVariable Long id) {
        return bookingMapper.toResponse(bookingService.cancelBooking(id));
    }

    private BookingResponse enrichWithPayment(BookingResponse base, Booking booking) {
        return paymentService.findLatestPayment(booking)
                .map(p -> base.withPayment(p.getStatus(), p.getMethod()))
                .orElse(base);
    }
}
