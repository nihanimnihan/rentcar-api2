package com.rentcar.api.email;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

/**
 * Production transactional email sender backed by Spring Mail.
 *
 * <p>Contract/PDF attachments are intentionally not attached here because this
 * codebase does not currently contain a booking contract/PDF generator.
 */
@Slf4j
@Service
@Profile({"prod", "local-smtp"})
public class SmtpEmailService implements EmailService {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String fromName;

    public SmtpEmailService(
            JavaMailSender mailSender,
            @Value("${rentcar.email.from}") String fromAddress,
            @Value("${rentcar.email.from-name:RentCar}") String fromName) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
    }

    @Override
    public void sendBookingConfirmation(ConfirmationEmailData data) {
        send(
                data.customerEmail(),
                "RentCar booking confirmed - " + data.bookingReference(),
                confirmationBody(data),
                data.bookingReference());
    }

    @Override
    public void sendBookingCancellation(CancellationEmailData data) {
        send(
                data.customerEmail(),
                "RentCar booking cancelled - " + data.bookingReference(),
                cancellationBody(data),
                data.bookingReference());
    }

    @Override
    public void sendRefundCompleted(RefundCompletedEmailData data) {
        send(
                data.customerEmail(),
                "RentCar refund completed - " + data.bookingReference(),
                refundCompletedBody(data),
                data.bookingReference());
    }

    private void send(String to, String subject, String body, String bookingReference) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    false,
                    StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(fromAddress, fromName));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(message);
            log.info("Transactional email sent: to={} subject={} bookingReference={}",
                    to, subject, bookingReference);
        } catch (Exception e) {
            log.warn("Transactional email failed: to={} subject={} bookingReference={} error={}",
                    to, subject, bookingReference, e.getMessage());
        }
    }

    private String confirmationBody(ConfirmationEmailData data) {
        return """
                Hi %s,

                Your RentCar booking is confirmed.

                Booking reference: %s
                Customer name: %s
                Pick-up: %s - %s
                Drop-off: %s - %s
                Selected car/service: %s
                Total price: EUR %s
                Manage booking: %s

                Thank you for choosing RentCar.
                """.formatted(
                data.customerName(),
                data.bookingReference(),
                data.customerName(),
                data.pickupLocation(),
                data.pickupDateTime().format(DISPLAY_FMT),
                data.dropoffLocation(),
                data.dropoffDateTime().format(DISPLAY_FMT),
                data.selectedService(),
                money(data.totalPrice()),
                optional(data.managementUrl(), "Not available"));
    }

    private String cancellationBody(CancellationEmailData data) {
        return """
                Hi %s,

                Your RentCar booking has been cancelled.

                Booking reference: %s
                Cancellation reason: %s
                Refund status: %s
                %s
                Manage booking: %s

                RentCar
                """.formatted(
                data.customerName(),
                data.bookingReference(),
                optional(data.cancellationReason(), "Not provided"),
                data.refundStatusLabel(),
                data.bankProcessingMessage(),
                optional(data.managementUrl(), "Not available"));
    }

    private String refundCompletedBody(RefundCompletedEmailData data) {
        return """
                Hi %s,

                Your refund for RentCar booking %s has been completed.

                Refund reference: %s
                %s

                RentCar
                """.formatted(
                data.customerName(),
                data.bookingReference(),
                optional(data.refundReference(), "Not available"),
                data.bankProcessingMessage());
    }

    private String optional(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String money(BigDecimal value) {
        if (value == null) {
            return "0.00";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
