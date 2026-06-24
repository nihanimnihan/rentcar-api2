package com.rentcar.api.domain.handover;

import com.rentcar.api.domain.booking.Booking;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "booking_deposits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingDeposit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingDepositMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingDepositStatus status;

    @Column(name = "stripe_payment_intent_id")
    private String stripePaymentIntentId;

    @Column(name = "stripe_checkout_session_id")
    private String stripeCheckoutSessionId;

    @Column(name = "stripe_payment_link_url", length = 1000)
    private String stripePaymentLinkUrl;

    @Column(name = "stripe_payment_reference")
    private String stripePaymentReference;

    @Column(name = "admin_note", length = 1000)
    private String adminNote;

    @Column(name = "collected_at")
    private Instant collectedAt;

    @Column(name = "refund_deadline_at")
    private Instant refundDeadlineAt;

    @Builder.Default
    @Column(name = "refunded_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(name = "remaining_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal remainingAmount;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) status = BookingDepositStatus.NOT_COLLECTED;
        if (currency == null) currency = "EUR";
        if (refundedAmount == null) refundedAmount = BigDecimal.ZERO;
        if (remainingAmount == null) remainingAmount = amount;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
