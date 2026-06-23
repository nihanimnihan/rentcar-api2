package com.rentcar.api.service;

import com.rentcar.api.dto.admin.AdminBookingDetailedListItem;
import com.rentcar.api.dto.admin.AdminBookingListItem;
import com.rentcar.api.dto.addon.BookingAddonResponse;
import com.rentcar.api.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminBookingService {

    private final BookingRepository bookingRepository;
    private final PaymentService paymentService;
    private final BookingCancellationPolicyService cancellationPolicyService;

    /**
     * Returns all rental bookings ordered newest first, enriched with the latest payment status.
     * For each booking one extra query is issued to fetch its latest payment (acceptable for admin use).
     */
    @Transactional(readOnly = true)
    public List<AdminBookingListItem> listBookings() {
        return bookingRepository.findAllOrderByCreatedAtDesc()
                .stream()
                .sorted(adminBookingOrdering())
                .map(booking -> {
                    var payment = paymentService.findLatestPayment(booking).orElse(null);
                    var paymentStatus = payment != null ? payment.getStatus() : null;
                    var paymentMethod = payment != null ? payment.getMethod() : null;
                    var policy = cancellationPolicyService.evaluateAdminPolicy(booking);
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
                            booking.getInsuranceTotalSnapshot(),
                            booking.getTotalPrice(),
                            booking.getBookingOptionType(),
                            booking.getBookingOptionDailyFee(),
                            booking.getCancellationPolicyType(),
                            policy.cancellable(),
                            policy.refundEligible(),
                            policy.refundAmount(),
                            policy.noShow() || isNoShow(booking.getCancellationReason()),
                            paymentStatus,
                            paymentMethod
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminBookingDetailedListItem getBookingById(Long id) {
        var booking = bookingRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new RuntimeException("Booking not found with id: " + id));
        var payment = paymentService.findLatestPayment(booking).orElse(null);
        var paymentStatus = payment != null ? payment.getStatus() : null;
        var paymentMethod = payment != null ? payment.getMethod() : null;
        var policy = cancellationPolicyService.evaluateAdminPolicy(booking);
        var transferDetails = booking.getTransferDetails();
        var addons = booking.getBookingAddons().stream()
                .map(ba -> new BookingAddonResponse(
                        ba.getAddon() != null ? ba.getAddon().getId() : null,
                        ba.getAddonName(),
                        ba.getPricingTypeSnapshot().name(),
                        ba.getPriceAtBooking()))
                .toList();
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
                booking.getPickupLocation(),
                booking.getPickupAddress(),
                booking.getPickupPlaceId(),
                booking.getDropoffDateTime(),
                booking.getDropoffLocation(),
                booking.getDropoffAddress(),
                booking.getDropoffPlaceId(),
                booking.getRentalDays(),
                booking.getBaseDailyPrice(),
                booking.getDiscountedDailyPrice(),
                booking.getDiscountPercentage(),
                booking.getRentalCharge(),
                booking.getOneWayFee(),
                booking.getPremiumLocationFee(),
                booking.getTax(),
                booking.getAddonCharge(),
                booking.getInsurancePackageId(),
                booking.getInsuranceCode(),
                booking.getInsuranceNameSnapshot(),
                booking.getInsuranceDailyPriceSnapshot(),
                booking.getInsuranceTotalSnapshot(),
                booking.getDepositAmountSnapshot(),
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
                policy.cancellable(),
                policy.adminOperationalCancellationAllowed(),
                policy.refundEligible(),
                policy.refundAmount(),
                policy.policyMessage(),
                policy.noShow() || isNoShow(booking.getCancellationReason()),
                addons,
                paymentStatus,
                paymentMethod
        );
    }

    private Comparator<com.rentcar.api.domain.booking.Booking> adminBookingOrdering() {
        return Comparator
                .comparing((com.rentcar.api.domain.booking.Booking booking) -> !isActive(booking))
                .thenComparing(com.rentcar.api.domain.booking.Booking::getPickupDateTime)
                .thenComparing(com.rentcar.api.domain.booking.Booking::getCreatedAt, Comparator.reverseOrder());
    }

    private boolean isActive(com.rentcar.api.domain.booking.Booking booking) {
        return booking.getStatus() == com.rentcar.api.domain.booking.BookingStatus.PENDING
                || booking.getStatus() == com.rentcar.api.domain.booking.BookingStatus.CONFIRMED;
    }

    private boolean isNoShow(String cancellationReason) {
        return "NO_SHOW".equalsIgnoreCase(cancellationReason);
    }
}
