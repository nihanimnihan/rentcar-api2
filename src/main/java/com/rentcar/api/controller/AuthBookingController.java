package com.rentcar.api.controller;

import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.dto.booking.BookingResponse;
import com.rentcar.api.mapper.BookingMapper;
import com.rentcar.api.repository.BookingRepository;
import com.rentcar.api.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthBookingController {

    private static final Logger log = LoggerFactory.getLogger(AuthBookingController.class);

    private final BookingRepository bookingRepository;
    private final BookingMapper bookingMapper;
    private final PaymentService paymentService;

    @GetMapping("/bookings")
    public List<BookingResponse> getMyBookings(HttpServletRequest request) {
        String email = (String) request.getSession().getAttribute("APP_USER_EMAIL");
        if (email == null) {
            log.warn("Unauthorized bookings access attempt (no session)");
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED);
        }
        log.info("Loading bookings for email={}", email);
        List<Booking> bookings = bookingRepository.findByCustomerEmailOrderByCreatedAtDesc(email);
        // Only include PENDING and CONFIRMED bookings for the customer-facing list
        java.util.List<com.rentcar.api.domain.booking.BookingStatus> allowed = java.util.List.of(com.rentcar.api.domain.booking.BookingStatus.PENDING, com.rentcar.api.domain.booking.BookingStatus.CONFIRMED);
        List<Booking> filtered = bookings.stream().filter(b -> allowed.contains(b.getStatus())).toList();
        log.info("Found {} bookings for email={}", filtered.size(), email);
        return filtered.stream()
                .map(b -> {
                    BookingResponse base = bookingMapper.toResponse(b);
                    return paymentService.findLatestPayment(b)
                            .map(p -> base.withPayment(p.getStatus(), p.getMethod()))
                            .orElse(base);
                })
                .collect(Collectors.toList());
    }
}
