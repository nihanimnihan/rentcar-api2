package com.rentcar.api.domain.payment;

/**
 * Lifecycle states for a {@link Payment} record.
 *
 * <pre>
 * State machine (happy path + failure/refund paths):
 *
 *   PENDING ──► PAID ──────────────────────► REFUND_PENDING ──► REFUNDED
 *     │                                           ▲
 *     │                                           │ (real Stripe: set here until webhook confirms)
 *     └──► FAILED                                 │ (FakePaymentProvider: skips to REFUNDED directly)
 *
 *   PENDING ──► CANCELLED  (booking cancelled before any charge)
 *   FAILED  ──► CANCELLED  (booking cancelled after failed charge attempt)
 * </pre>
 *
 * <p><b>Async-readiness notes:</b>
 * <ul>
 *   <li>{@link #REFUND_PENDING} is reserved for when a real payment provider (e.g. Stripe)
 *       initiates a refund asynchronously and confirmation arrives via webhook.
 *       The {@link FakePaymentProvider} transitions directly to {@link #REFUNDED}.</li>
 *   <li>A future {@code PaymentWebhookHandler} will consume Stripe events and advance
 *       {@code REFUND_PENDING → REFUNDED} without any synchronous waiting.</li>
 *   <li>Similarly, a real Stripe charge confirmation webhook can advance
 *       {@code PENDING → PAID} asynchronously instead of relying on the synchronous charge call.</li>
 * </ul>
 */
public enum PaymentStatus {

    /** Payment record created; provider charge not yet attempted. */
    PENDING,

    /** Charge succeeded — money collected. */
    PAID,

    /** Charge attempt failed — no money collected. */
    FAILED,

    /**
     * Refund has been requested from the provider but not yet confirmed.
     *
     * <p>Used in real async flows (e.g. Stripe): set this immediately when the refund is
     * initiated, then advance to {@link #REFUNDED} when the provider webhook confirms.
     * The {@link FakePaymentProvider} skips this state and goes directly to {@link #REFUNDED}.
     *
     * <p>TODO: implement webhook handler to transition REFUND_PENDING → REFUNDED.
     */
    REFUND_PENDING,

    /** Refund confirmed — money returned to the customer. */
    REFUNDED,

    /**
     * Payment voided without any charge (booking was PENDING or FAILED at cancellation time).
     * No financial transaction occurred.
     */
    CANCELLED
}
