package com.rentcar.api.email;

/**
 * Abstraction for outbound transactional email.
 *
 * <p>The only concrete implementation today is {@link FakeEmailService} (dev/local-postgres
 * profiles), which logs rather than sending real mail. A real SMTP/SES/SendGrid implementation
 * can be added later without touching any call site.
 *
 * <p>Implementations MUST NOT throw exceptions for transient failures — they should log a
 * warning instead. The booking confirmation flow wraps calls in a try-catch as a second
 * safety net, but well-behaved implementations handle their own errors gracefully.
 */
public interface EmailService {

    /**
     * Sends (or simulates sending) a booking confirmation email.
     *
     * @param data all content required to render the confirmation email
     */
    void sendBookingConfirmation(ConfirmationEmailData data);
}
