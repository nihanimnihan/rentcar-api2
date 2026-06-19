package com.rentcar.api.service;

import com.rentcar.api.dto.admin.AdminBookingDetailedListItem;
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
                            booking.getSource(),
                            booking.getCustomer().getFullName(),
                            booking.getCustomer().getEmail(),
                            booking.getCar().getBrand(),
                            booking.getCar().getModel(),
                            booking.getPickupDateTime(),
                            booking.getDropoffDateTime(),
                            booking.getRentalCharge(),
                            booking.getOneWayFee(),
                            booking.getPremiumLocationFee(),
                            booking.getTax(),
                            booking.getAddonCharge(),
                            booking.getTotalPrice(),
                            booking.getBookingOptionType(),
                            booking.getBookingOptionDailyFee(),
                            booking.getCancellationPolicyType(),
                            paymentStatus
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminBookingDetailedListItem getBookingById(Long id) {
        var booking = bookingRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new RuntimeException("Booking not found with id: " + id));
        var paymentStatus = paymentService.findLatestPayment(booking)
                .map(p -> p.getStatus())
                .orElse(null);
        var transferDetails = booking.getTransferDetails();
        return new AdminBookingDetailedListItem(
                booking.getId(),
                booking.getBookingReference(),
                booking.getStatus(),
                booking.getSource(),
                booking.getCustomer().getFullName(),
                booking.getCustomer().getEmail(),
                booking.getCar().getBrand(),
                booking.getCar().getModel(),
                booking.getPickupDateTime(),
                booking.getDropoffDateTime(),
                booking.getRentalDays(),
                booking.getBaseDailyPrice(),
                booking.getDiscountedDailyPrice(),
                booking.getDiscountPercentage(),
                booking.getRentalCharge(),
                booking.getOneWayFee(),
                booking.getPremiumLocationFee(),
                booking.getTax(),
                booking.getAddonCharge(),
                booking.getTotalPrice(),
                booking.getIncludedKmSnapshot(),
                booking.getUnlimitedKmPriceSnapshot(),
                booking.getMileageOption(),
                booking.getBookingOptionType(),
                booking.getBookingOptionDailyFee(),
                booking.getCancellationPolicyType(),
                booking.getPassengers(),
                transferDetails != null ? transferDetails.getDurationHours() : null,
                transferDetails != null ? transferDetails.getHourlyPriceSnapshot() : null,
                transferDetails != null ? transferDetails.getChauffeurCategoryCode() : null,
                transferDetails != null ? transferDetails.getChauffeurCategoryName() : null,
                booking.getNotes(),
                booking.getCancellationReason(),
                paymentStatus
        );
    }
}
