package com.rentcar.api.domain.payment;

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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column
    private String providerReference;

    /**
     * Public-facing payment reference (e.g. {@code PAY-3F4A8B2C}).
     *
     * <p>Generated once on persist; used in API responses instead of exposing
     * the internal numeric {@link #id}. Unique per payment record.
     */
    @Column(unique = true, updatable = false)
    private String paymentReference;

    @Column
    private Instant paidAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @PrePersist
    public void prePersist() {
        // Instant.now() is intentional: JPA lifecycle callbacks cannot receive Spring beans,
        // so AppClock cannot be injected here. The JVM is pinned to UTC in RentcarApiApplication,
        // making Instant.now() deterministic across all environments.
        this.createdAt = Instant.now();
        if (this.status == null) {
            this.status = PaymentStatus.PENDING;
        }
        if (this.paymentReference == null) {
            // PAY- prefix + first 8 hex chars of a random UUID, e.g. PAY-3F4A8B2C.
            // UUID provides sufficient entropy for uniqueness across concurrent payments.
            this.paymentReference = "PAY-" + UUID.randomUUID()
                    .toString().replace("-", "").substring(0, 8).toUpperCase();
        }
    }
}