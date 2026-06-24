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
import jakarta.persistence.Lob;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "vehicle_handovers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleHandover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @Column(name = "handover_at")
    private Instant handoverAt;

    @Column(name = "km_out")
    private Integer kmOut;

    @Enumerated(EnumType.STRING)
    @Column(name = "fuel_level_out")
    private FuelLevel fuelLevelOut;

    @Enumerated(EnumType.STRING)
    @Column(name = "battery_level_out")
    private BatteryLevel batteryLevelOut;

    @Lob
    @Column(name = "customer_signature_data", columnDefinition = "TEXT")
    private String customerSignatureData;

    @Column(name = "customer_signature_at")
    private Instant customerSignatureAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deposit_id")
    private BookingDeposit deposit;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
