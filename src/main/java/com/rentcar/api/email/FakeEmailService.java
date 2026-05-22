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
 * in-memory list so integration tests can assert that the confirmation was triggered.
 *
 * <p>Thread-safety: {@code sentEmails} is synchronized for parallel-test safety.
 * Call {@link #clearSentEmails()} in a {@code @BeforeEach} if tests share the
 * Spring context and need a clean slate.
 *
 * <p>Replace this bean with a real SMTP/SES/SendGrid implementation (different profile)
 * when real email delivery is needed — no other code changes required.
 */
@Slf4j
@Service
@Profile({"dev", "local-postgres"})
public class FakeEmailService implements EmailService {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private final List<ConfirmationEmailData> sentEmails =
            Collections.synchronizedList(new ArrayList<>());

    @Override
    public void sendBookingConfirmation(ConfirmationEmailData data) {
        sentEmails.add(data);

        String manageLink = (data.managementUrl() != null && !data.managementUrl().isBlank())
                ? "\n  Manage booking : " + data.managementUrl()
                : "";

        log.info("""
                [FAKE EMAIL] Booking confirmation
                  To            : {} <{}>
                  Reference     : {}
                  Pick-up       : {} — {}
                  Return        : {} — {}
                  Total         : {} EUR{}
                """,
                data.customerName(), data.customerEmail(),
                data.bookingReference(),
                data.pickupLocation(), data.pickupDateTime().format(DISPLAY_FMT),
                data.dropoffLocation(), data.dropoffDateTime().format(DISPLAY_FMT),
                data.totalPrice(),
                manageLink);
    }

    /** Returns an unmodifiable snapshot of all emails sent since the last {@link #clearSentEmails()}. */
    public List<ConfirmationEmailData> getSentEmails() {
        synchronized (sentEmails) {
            return List.copyOf(sentEmails);
        }
    }

    /** Clears the captured email list. Call in {@code @BeforeEach} when sharing a Spring context. */
    public void clearSentEmails() {
        sentEmails.clear();
    }
}
