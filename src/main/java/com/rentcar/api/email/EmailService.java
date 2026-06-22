package com.rentcar.api.email;

/**
 * Abstraction for outbound transactional email.
 *
 * <p>{@link FakeEmailService} is used for dev/local profiles and logs rather than sending real
 * mail. {@link SmtpEmailService} is used for the production profile.
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

    /**
     * Sends (or simulates sending) a booking cancellation email.
     *
     * @param data all content required to render the cancellation email
     */
    void sendBookingCancellation(CancellationEmailData data);

    /**
     * Sends (or simulates sending) a refund-completed email.
     *
     * @param data all content required to render the refund-completed email
     */
    void sendRefundCompleted(RefundCompletedEmailData data);

    /**
     * Sends (or simulates sending) a no-show recorded email.
     *
     * @param data all content required to render the no-show email
     */
    void sendNoShowRecorded(NoShowEmailData data);

    /**
     * Sends (or simulates sending) a passwordless login code.
     *
     * @param data all content required to render the login code email
     */
    void sendLoginOtp(LoginOtpEmailData data);
}
