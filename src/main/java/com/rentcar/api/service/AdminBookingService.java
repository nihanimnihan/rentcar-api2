package com.rentcar.api.service;

import com.rentcar.api.dto.admin.AdminBookingListItem;
import com.rentcar.api.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminBookingService {

    private final BookingRepository bookingRepository;
    private final PaymentService paymentService;

    /**
     * Returns all rental bookings ordered newest first, enriched with the latest payment status.
     * For each booking one extra query is issued to fetch its latest payment (acceptable for admin use).
     */
    @Transactional(readOnly = true)
    public List<AdminBookingListItem> listBookings() {
        return bookingRepository.findAllOrderByCreatedAtDesc()
                .stream()
                .map(booking -> {
                    var paymentStatus = paymentService.findLatestPayment(booking)
                            .map(p -> p.getStatus())
                            .orElse(null);
                    return new AdminBookingListItem(
                            booking.getId(),
                            booking.getBookingReference(),
                            booking.getStatus(),
                            booking.getCustomer().getFullName(),
                            booking.getCustomer().getEmail(),
                            booking.getCar().getBrand(),
                            booking.getCar().getModel(),
                            booking.getPickupDateTime(),
                            booking.getDropoffDateTime(),
                            booking.getTotalPrice(),
                            paymentStatus
                    );
                })
                .toList();
    }
}
