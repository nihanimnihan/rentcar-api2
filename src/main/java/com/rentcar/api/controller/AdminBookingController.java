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
 * All routes under /api/admin/** require ROLE_ADMIN (HTTP Basic).
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminBookingController {

    private final AdminBookingService adminBookingService;

    /**
     * Returns all bookings ordered newest first.
     * TODO: add pagination, search by bookingReference/status/email when volume grows.
     */
    @GetMapping("/bookings")
    public List<AdminBookingListItem> listBookings() {
        return adminBookingService.listBookings();
    }

    @GetMapping("/{id}")
    public AdminBookingDetailedListItem getBookingById(@PathVariable Long id) {
        return adminBookingService.getBookingById(id);
    }
}
