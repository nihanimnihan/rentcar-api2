package com.rentcar.api.controller;

import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.dto.admin.AdminBookingDetailedListItem;
import com.rentcar.api.dto.admin.AdminBookingListItem;
import com.rentcar.api.dto.booking.BookingResponse;
import com.rentcar.api.service.AdminBookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @GetMapping("/bookings/{id}")
    public AdminBookingDetailedListItem getBookingById(@PathVariable Long id) {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        String actor = (auth != null && auth.getName() != null) ? auth.getName() : "anonymous";
        String role = (auth != null && auth.getAuthorities() != null) ? auth.getAuthorities().toString() : "[]";
        log.info("Admin action=getBookingById actor={} authorities={} targetBookingId={}", actor, role, id);
        return adminBookingService.getBookingById(id);
    }
}
