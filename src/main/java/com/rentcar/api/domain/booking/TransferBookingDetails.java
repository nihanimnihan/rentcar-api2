package com.rentcar.api.domain.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
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

@Entity
@Table(name = "transfer_booking_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferBookingDetails {

    @Id
    @Column(name = "booking_id")
    private Long bookingId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Column(nullable = false)
    private int durationHours;

    @Column(nullable = false)
    private int passengers;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal hourlyPriceSnapshot;

    @Column(nullable = false, length = 50)
    private String chauffeurCategoryCode;

    @Column(nullable = false, length = 120)
    private String chauffeurCategoryName;

    @Column(length = 1000)
    private String notes;

    @PrePersist
    @PreUpdate
    public void normalizeDefaults() {
        if (durationHours < 1) durationHours = 1;
        if (passengers < 1) passengers = 1;
        if (hourlyPriceSnapshot == null) hourlyPriceSnapshot = BigDecimal.ZERO;
        if (chauffeurCategoryCode == null || chauffeurCategoryCode.isBlank()) {
            chauffeurCategoryCode = "UNKNOWN";
        }
        if (chauffeurCategoryName == null || chauffeurCategoryName.isBlank()) {
            chauffeurCategoryName = chauffeurCategoryCode;
        }
    }
}
