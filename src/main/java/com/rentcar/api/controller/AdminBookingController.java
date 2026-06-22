package com.rentcar.api.controller;

import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.dto.admin.AdminCreateBookingRequest;
import com.rentcar.api.dto.admin.AdminBookingDetailedListItem;
import com.rentcar.api.dto.admin.AdminBookingListItem;
import com.rentcar.api.dto.booking.BookingResponse;
import com.rentcar.api.mapper.BookingMapper;
import com.rentcar.api.service.AdminBookingService;
import com.rentcar.api.service.BookingCancellationService;
import com.rentcar.api.service.BookingEmailNotificationService;
import com.rentcar.api.service.BookingService;
import com.rentcar.api.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin-only booking management endpoints.
 * All routes under /api/admin/** require ROLE_ADMIN or SUPER_ADMIN (session-based OAuth2 login).
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminBookingController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AdminBookingController.class);

    private final AdminBookingService adminBookingService;
    private final BookingService bookingService;
    private final BookingCancellationService bookingCancellationService;
    private final BookingMapper bookingMapper;
    private final PaymentService paymentService;
    private final BookingEmailNotificationService bookingEmailNotificationService;

    /**
     * Returns all bookings ordered newest first.
     * TODO: add pagination, search by bookingReference/status/email when volume grows.
     */
    @GetMapping("/bookings")
    public List<AdminBookingListItem> listBookings() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        String actor = (auth != null && auth.getName() != null) ? auth.getName() : "anonymous";
        String role = (auth != null && auth.getAuthorities() != null) ? auth.getAuthorities().toString() : "[]";
        log.info("Admin action=listBookings actor={} authorities={}", actor, role);
        return adminBookingService.listBookings();
    }

    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse createBooking(@Valid @RequestBody AdminCreateBookingRequest request) {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        String actor = (auth != null && auth.getName() != null) ? auth.getName() : "anonymous";
        log.info("Admin action=createBooking actor={} vehicleId={} paymentSource={}",
                actor, request.vehicleId(), request.paymentSource());
        Booking booking = bookingService.createAdminBooking(request);
        bookingEmailNotificationService.sendBookingConfirmation(booking);
        return paymentService.findLatestPayment(booking)
                .map(p -> bookingMapper.toResponse(booking).withPayment(p.getStatus(), p.getMethod()))
                .orElseGet(() -> bookingMapper.toResponse(booking));
    }

    @GetMapping("/bookings/{id}")
    public AdminBookingDetailedListItem getBookingById(@PathVariable Long id) {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        String actor = (auth != null && auth.getName() != null) ? auth.getName() : "anonymous";
        String role = (auth != null && auth.getAuthorities() != null) ? auth.getAuthorities().toString() : "[]";
        log.info("Admin action=getBookingById actor={} authorities={} targetBookingId={}", actor, role, id);
        return adminBookingService.getBookingById(id);
    }

    @PostMapping("/bookings/{id}/cancel")
    public BookingResponse cancelBooking(@PathVariable Long id) {
        Booking booking = bookingCancellationService.cancelBooking(id);
        return enrichWithPayment(bookingMapper.toResponse(booking), booking);
    }

    @PostMapping("/bookings/{id}/cancel-refund")
    public BookingResponse cancelAndRefundBooking(@PathVariable Long id) {
        Booking booking = bookingCancellationService.cancelBookingWithRefund(id);
        return enrichWithPayment(bookingMapper.toResponse(booking), booking);
    }

    @PostMapping("/bookings/{id}/no-show")
    public BookingResponse markNoShow(@PathVariable Long id) {
        Booking booking = bookingCancellationService.markNoShow(id);
        return enrichWithPayment(bookingMapper.toResponse(booking), booking);
    }

    private BookingResponse enrichWithPayment(BookingResponse base, Booking booking) {
        return paymentService.findLatestPayment(booking)
                .map(p -> base.withPayment(p.getStatus(), p.getMethod()))
                .orElse(base);
    }
}
