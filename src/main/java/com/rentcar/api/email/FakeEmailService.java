package com.rentcar.api.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Dev/local fake implementation of {@link EmailService}.
 *
 * <p>No real email is sent. Every call is logged at INFO level and appended to an
 * in-memory lists so integration tests can assert that notifications were triggered.
 *
 * <p>Thread-safety: {@code sentEmails} is synchronized for parallel-test safety.
 * Call {@link #clearSentEmails()} in a {@code @BeforeEach} if tests share the
 * Spring context and need a clean slate.
 *
 * <p>The production profile uses {@link SmtpEmailService}; no call-site changes are
 * required when switching between fake and real delivery.
 */
@Slf4j
@Service
@Profile({"dev & !prod & !local-smtp", "local-postgres & !prod & !local-smtp"})
public class FakeEmailService implements EmailService {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private final EmailLocalizationService emailLocalizationService;

    private final List<ConfirmationEmailData> sentConfirmationEmails =
            Collections.synchronizedList(new ArrayList<>());
    private final List<CancellationEmailData> sentCancellationEmails =
            Collections.synchronizedList(new ArrayList<>());
    private final List<RefundCompletedEmailData> sentRefundCompletedEmails =
            Collections.synchronizedList(new ArrayList<>());

    public FakeEmailService(EmailLocalizationService emailLocalizationService) {
        this.emailLocalizationService = emailLocalizationService;
    }

    @Override
    public void sendBookingConfirmation(ConfirmationEmailData data) {
        sentConfirmationEmails.add(data);
        LocalizedEmail localized = emailLocalizationService.bookingConfirmation(data);

        String manageLink = (data.managementUrl() != null && !data.managementUrl().isBlank())
                ? "\n  Manage booking : " + data.managementUrl()
                : "";

        log.info("""
                [FAKE EMAIL] Booking confirmation
                  To            : {} <{}>
                  Subject       : {}
                  Reference     : {}
                  Pick-up       : {} — {}
                  Return        : {} — {}
                  Service       : {}
                  Total         : {} EUR{}
                """,
                data.customerName(), data.customerEmail(),
                localized.subject(),
                data.bookingReference(),
                data.pickupLocation(), data.pickupDateTime().format(DISPLAY_FMT),
                data.dropoffLocation(), data.dropoffDateTime().format(DISPLAY_FMT),
                data.selectedService(),
                data.totalPrice(),
                manageLink);
    }

    @Override
    public void sendBookingCancellation(CancellationEmailData data) {
        sentCancellationEmails.add(data);
        LocalizedEmail localized = emailLocalizationService.bookingCancellation(data);

        String manageLink = (data.managementUrl() != null && !data.managementUrl().isBlank())
                ? "\n  Manage booking : " + data.managementUrl()
                : "";

        log.info("""
                [FAKE EMAIL] Booking cancellation
                  To            : {} <{}>
                  Subject       : {}
                  Reference     : {}
                  Reason        : {}
                  Refund status : {}{}
                """,
                data.customerName(), data.customerEmail(),
                localized.subject(),
                data.bookingReference(),
                data.cancellationReason(),
                emailLocalizationService.refundStatusLabel(data.refundStatus(), data.language()),
                manageLink);
    }

    @Override
    public void sendRefundCompleted(RefundCompletedEmailData data) {
        sentRefundCompletedEmails.add(data);
        LocalizedEmail localized = emailLocalizationService.refundCompleted(data);

        String manageLink = (data.managementUrl() != null && !data.managementUrl().isBlank())
                ? "\n  Manage booking : " + data.managementUrl()
                : "";

        log.info("""
                [FAKE EMAIL] Refund completed
                  To            : {} <{}>
                  Subject       : {}
                  Reference     : {}
                  Refund ref    : {}{}
                """,
                data.customerName(), data.customerEmail(),
                localized.subject(),
                data.bookingReference(),
                data.refundReference(),
                manageLink);
    }

    /** Returns an unmodifiable snapshot of all confirmation emails sent since the last {@link #clearSentEmails()}. */
    public List<ConfirmationEmailData> getSentEmails() {
        return getSentConfirmationEmails();
    }

    public List<ConfirmationEmailData> getSentConfirmationEmails() {
        synchronized (sentConfirmationEmails) {
            return List.copyOf(sentConfirmationEmails);
        }
    }

    public List<CancellationEmailData> getSentCancellationEmails() {
        synchronized (sentCancellationEmails) {
            return List.copyOf(sentCancellationEmails);
        }
    }

    public List<RefundCompletedEmailData> getSentRefundCompletedEmails() {
        synchronized (sentRefundCompletedEmails) {
            return List.copyOf(sentRefundCompletedEmails);
        }
    }

    /** Clears the captured email list. Call in {@code @BeforeEach} when sharing a Spring context. */
    public void clearSentEmails() {
        sentConfirmationEmails.clear();
        sentCancellationEmails.clear();
        sentRefundCompletedEmails.clear();
    }
}
