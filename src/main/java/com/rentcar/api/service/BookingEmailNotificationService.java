package com.rentcar.api.service;

import com.rentcar.api.domain.booking.Booking;
import com.rentcar.api.domain.booking.TransferBookingDetails;
import com.rentcar.api.domain.car.Car;
import com.rentcar.api.domain.payment.Payment;
import com.rentcar.api.domain.payment.PaymentStatus;
import com.rentcar.api.email.CancellationEmailData;
import com.rentcar.api.email.ConfirmationEmailData;
import com.rentcar.api.email.EmailService;
import com.rentcar.api.email.RefundCompletedEmailData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingEmailNotificationService {

    private static final String REFUND_BANK_PROCESSING_MESSAGE =
            "If a refund applies, it may take a few business days to appear in your bank account.";

    private static final String REFUND_COMPLETED_BANK_PROCESSING_MESSAGE =
            "The refund is completed on our side, but it may still take a few business days to appear in your bank account.";

    private final EmailService emailService;

    @Value("${app.public-base-url:}")
    private String publicBaseUrl;

    public void sendBookingConfirmation(Booking booking) {
        try {
            emailService.sendBookingConfirmation(buildConfirmationEmailData(booking));
        } catch (Exception e) {
            log.warn("Confirmation email failed for bookingId={} reference={}: {}",
                    booking.getId(), booking.getBookingReference(), e.getMessage());
        }
    }

    public void sendBookingCancellation(Booking booking, PaymentStatus refundStatus) {
        try {
            emailService.sendBookingCancellation(buildCancellationEmailData(booking, refundStatus));
        } catch (Exception e) {
            log.warn("Cancellation email failed for bookingId={} reference={}: {}",
                    booking.getId(), booking.getBookingReference(), e.getMessage());
        }
    }

    public void sendRefundCompleted(Booking booking, Payment payment) {
        try {
            emailService.sendRefundCompleted(buildRefundCompletedEmailData(booking, payment));
        } catch (Exception e) {
            log.warn("Refund completed email failed for bookingId={} reference={}: {}",
                    booking.getId(), booking.getBookingReference(), e.getMessage());
        }
    }

    private ConfirmationEmailData buildConfirmationEmailData(Booking booking) {
        return new ConfirmationEmailData(
                booking.getBookingReference(),
                booking.getCustomer().getEmail(),
                booking.getCustomer().getFullName(),
                booking.getPickupDateTime(),
                booking.getPickupLocation(),
                booking.getDropoffDateTime(),
                booking.getDropoffLocation(),
                selectedService(booking),
                booking.getTotalPrice(),
                manageBookingUrl(booking)
        );
    }

    private CancellationEmailData buildCancellationEmailData(Booking booking, PaymentStatus refundStatus) {
        return new CancellationEmailData(
                booking.getBookingReference(),
                booking.getCustomer().getEmail(),
                booking.getCustomer().getFullName(),
                booking.getCancellationReason(),
                refundStatus,
                refundStatusLabel(refundStatus),
                REFUND_BANK_PROCESSING_MESSAGE,
                manageBookingUrl(booking)
        );
    }

    private RefundCompletedEmailData buildRefundCompletedEmailData(Booking booking, Payment payment) {
        return new RefundCompletedEmailData(
                booking.getBookingReference(),
                booking.getCustomer().getEmail(),
                booking.getCustomer().getFullName(),
                payment.getProviderReference(),
                REFUND_COMPLETED_BANK_PROCESSING_MESSAGE
        );
    }

    private String manageBookingUrl(Booking booking) {
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            return null;
        }
        String normalizedBaseUrl = publicBaseUrl.trim().replaceAll("/+$", "");
        return UriComponentsBuilder.fromUriString(normalizedBaseUrl)
                .path("/manage-booking.html")
                .queryParam("bookingReference", booking.getBookingReference())
                .toUriString();
    }

    private String selectedService(Booking booking) {
        String vehicle = vehicleLabel(booking.getCar());
        TransferBookingDetails transferDetails = booking.getTransferDetails();
        if (transferDetails != null) {
            String category = transferDetails.getChauffeurCategoryName();
            if (category != null && !category.isBlank()) {
                return category + " chauffeur service (" + vehicle + ")";
            }
            return "Chauffeur service (" + vehicle + ")";
        }
        return vehicle;
    }

    private String vehicleLabel(Car car) {
        if (car == null) {
            return "Selected vehicle";
        }
        String brand = car.getBrand() == null ? "" : car.getBrand().trim();
        String model = car.getModel() == null ? "" : car.getModel().trim();
        String label = (brand + " " + model).trim();
        return label.isBlank() ? "Selected vehicle" : label;
    }

    private String refundStatusLabel(PaymentStatus status) {
        if (status == null) {
            return "Not available";
        }
        return switch (status) {
            case REFUNDED -> "Refund completed";
            case REFUND_PENDING -> "Refund initiated";
            case PAID -> "Paid - refund not yet completed";
            case PENDING, FAILED, CANCELLED -> "No charge collected";
        };
    }
}
