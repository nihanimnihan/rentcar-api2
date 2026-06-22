package com.rentcar.api.email;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Production transactional email sender backed by Spring Mail.
 *
 * <p>Contract/PDF attachments are intentionally not attached here because this
 * codebase does not currently contain a booking contract/PDF generator.
 */
@Slf4j
@Service
@Profile({"dev", "prod"})
@ConditionalOnExpression("'${spring.mail.host:}' != ''")
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final EmailLocalizationService emailLocalizationService;
    private final String fromAddress;
    private final String fromName;

    public SmtpEmailService(
            JavaMailSender mailSender,
            EmailLocalizationService emailLocalizationService,
            @Value("${rentcar.email.from}") String fromAddress,
            @Value("${rentcar.email.from-name:Paradise Deluxe}") String fromName) {
        this.mailSender = mailSender;
        this.emailLocalizationService = emailLocalizationService;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
    }

    @Override
    public void sendBookingConfirmation(ConfirmationEmailData data) {
        LocalizedEmail email = emailLocalizationService.bookingConfirmation(data);
        send(
                data.customerEmail(),
                email.subject(),
                email.body(),
                data.bookingReference());
    }

    @Override
    public void sendBookingCancellation(CancellationEmailData data) {
        LocalizedEmail email = emailLocalizationService.bookingCancellation(data);
        send(
                data.customerEmail(),
                email.subject(),
                email.body(),
                data.bookingReference());
    }

    @Override
    public void sendRefundCompleted(RefundCompletedEmailData data) {
        LocalizedEmail email = emailLocalizationService.refundCompleted(data);
        send(
                data.customerEmail(),
                email.subject(),
                email.body(),
                data.bookingReference());
    }

    @Override
    public void sendNoShowRecorded(NoShowEmailData data) {
        LocalizedEmail email = emailLocalizationService.noShowRecorded(data);
        send(
                data.customerEmail(),
                email.subject(),
                email.body(),
                data.bookingReference());
    }

    @Override
    public void sendLoginOtp(LoginOtpEmailData data) {
        LocalizedEmail email = emailLocalizationService.loginOtp(data);
        send(data.customerEmail(), email.subject(), email.body(), "email-otp");
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
}
