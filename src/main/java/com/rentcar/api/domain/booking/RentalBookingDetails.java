package com.rentcar.api.domain.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "rental_booking_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RentalBookingDetails {

    @Id
    @Column(name = "booking_id")
    private Long bookingId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Column(nullable = false)
    private int rentalDays;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal baseDailyPrice;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discountedDailyPrice;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercentage;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal rentalCharge;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal oneWayFee;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal premiumLocationFee;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal tax;

    @Builder.Default
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal addonCharge = BigDecimal.ZERO;

    @Column(nullable = false)
    private int includedKmSnapshot;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unlimitedKmPriceSnapshot;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MileageOption mileageOption = MileageOption.INCLUDED;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingOptionType bookingOptionType = BookingOptionType.BEST_PRICE;

    @Builder.Default
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal bookingOptionDailyFee = BigDecimal.ZERO;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CancellationPolicyType cancellationPolicyType = CancellationPolicyType.STRICT;

    @PrePersist
    @PreUpdate
    public void normalizeDefaults() {
        if (baseDailyPrice == null) baseDailyPrice = BigDecimal.ZERO;
        if (discountedDailyPrice == null) discountedDailyPrice = BigDecimal.ZERO;
        if (discountPercentage == null) discountPercentage = BigDecimal.ZERO;
        if (rentalCharge == null) rentalCharge = BigDecimal.ZERO;
        if (oneWayFee == null) oneWayFee = BigDecimal.ZERO;
        if (premiumLocationFee == null) premiumLocationFee = BigDecimal.ZERO;
        if (tax == null) tax = BigDecimal.ZERO;
        if (addonCharge == null) addonCharge = BigDecimal.ZERO;
        if (unlimitedKmPriceSnapshot == null) unlimitedKmPriceSnapshot = BigDecimal.ZERO;
        if (mileageOption == null) mileageOption = MileageOption.INCLUDED;
        if (bookingOptionType == null) bookingOptionType = BookingOptionType.BEST_PRICE;
        if (bookingOptionDailyFee == null) bookingOptionDailyFee = BigDecimal.ZERO;
        if (cancellationPolicyType == null) cancellationPolicyType = CancellationPolicyType.STRICT;
    }
}
