package com.rentcar.api.domain.booking;

import com.rentcar.api.domain.addon.BookingAddon;
import com.rentcar.api.domain.car.Car;
import com.rentcar.api.domain.customer.Customer;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.FutureOrPresent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "bookings",
        indexes = @Index(
                name = "idx_bookings_availability",
                columnList = "car_id, status, pickup_date_time, dropoff_date_time"
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "car_id", nullable = false)
    private Car car;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false)
    @FutureOrPresent
    private LocalDateTime pickupDateTime;

    @Column(nullable = false)
    private LocalDateTime dropoffDateTime;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private int rentalDays;

    // Price snapshot
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

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    // Sum of all add-on charges at booking time (0 if no add-ons selected)
    @Builder.Default
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal addonCharge = BigDecimal.ZERO;

    // Mileage snapshot — records the km allowance and unlimited-km price that
    // were applicable at booking time so historical bookings are not affected
    // by future config changes.
    @Column(nullable = false)
    private int includedKmSnapshot;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unlimitedKmPriceSnapshot;

    @Builder.Default
    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<BookingAddon> bookingAddons = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingSource source;

    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.addonCharge == null) {
            this.addonCharge = BigDecimal.ZERO;
        }
    }
}