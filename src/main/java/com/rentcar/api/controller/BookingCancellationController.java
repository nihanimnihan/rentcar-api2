package com.rentcar.api.controller;

import com.rentcar.api.dto.booking.BookingResponse;
import com.rentcar.api.dto.booking.CancellationPolicyResponse;
import com.rentcar.api.mapper.BookingMapper;
import com.rentcar.api.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingCancellationController {

    private final BookingService bookingService;
    private final BookingMapper bookingMapper;

    @GetMapping("/manage/cancellation-policy")
    public CancellationPolicyResponse cancellationPolicy(@RequestParam String bookingReference,
                                                         @RequestParam String lastName) {
        return bookingService.getCancellationPolicy(bookingReference, lastName);
    }

    @PostMapping("/{id}/cancel")
    public BookingResponse cancelBooking(@PathVariable Long id) {
        return bookingMapper.toResponse(bookingService.cancelBooking(id));
    }
}
