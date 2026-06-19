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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "bookings",
        indexes = {
                @Index(
                        name = "idx_bookings_availability",
                        columnList = "car_id, status, pickup_date_time, dropoff_date_time"
                ),
                @Index(
                        name = "idx_bookings_manage_token_hash",
                        columnList = "manage_token_hash"
                )
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_bookings_reference",
                columnNames = "booking_reference"
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

    /** Human-friendly reference shown to customers, e.g. RC-260521-K8P4. */
    @Column(name = "booking_reference", nullable = false, length = 15)
    private String bookingReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "car_id", nullable = false)
    private Car car;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false)
    private LocalDateTime pickupDateTime;

    /** Customer-entered pickup location name (e.g. "BCN Airport T1"). Nullable for legacy rows. */
    @Column(length = 255)
    private String pickupLocation;

    @Column(nullable = false)
    private LocalDateTime dropoffDateTime;

    /** Customer-entered dropoff location name. Nullable for legacy rows. */
    @Column(length = 255)
    private String dropoffLocation;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

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

    // Selected mileage option — INCLUDED (default) or UNLIMITED.
    // When UNLIMITED the unlimited-km surcharge is already baked into totalPrice.
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MileageOption mileageOption = MileageOption.INCLUDED;

    @Builder.Default
    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<BookingAddon> bookingAddons = new ArrayList<>();

    @OneToOne(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private RentalBookingDetails rentalDetails;

    @OneToOne(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private TransferBookingDetails transferDetails;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "checkout_session_token", length = 255)
    private String checkoutSessionToken;

    @Column(name = "manage_token_hash", length = 64)
    private String manageTokenHash;

    @Column(name = "manage_token_expires_at")
    private Instant manageTokenExpiresAt;

    @Column(name = "manage_token_revoked_at")
    private Instant manageTokenRevokedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingSource source;

    // ── Booking option / cancellation policy ────────────────────────────────────
    //
    // bookingOptionType: BEST_PRICE (default) or STAY_FLEXIBLE (future).
    //   STAY_FLEXIBLE will later add a per-day flexibility fee and grant free
    //   cancellation/rebooking up to the pickup time.
    //
    // bookingOptionDailyFee: extra daily fee for STAY_FLEXIBLE; persisted as 0.00
    //   for BEST_PRICE rental bookings so "no fee" is not confused with "unknown".
    //   TODO: compute and persist the real fee when STAY_FLEXIBLE is activated.
    //
    // cancellationPolicyType: STRICT for BEST_PRICE, FREE_CANCELLATION for STAY_FLEXIBLE.
    //   Nullable for transfer bookings and legacy rows that pre-date this field.
    //   TODO: enforce policy in cancelBooking() when STAY_FLEXIBLE is active.

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "booking_option_type")
    private BookingOptionType bookingOptionType = BookingOptionType.BEST_PRICE;

    /** Per-day flexibility fee. Zero for BEST_PRICE rental bookings; populated when STAY_FLEXIBLE is activated. */
    @Column(name = "booking_option_daily_fee", precision = 10, scale = 2)
    private BigDecimal bookingOptionDailyFee;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_policy_type")
    private CancellationPolicyType cancellationPolicyType;

    // Transfer-booking extras — null for regular rental bookings.
    @Column
    private Integer passengers;

    @Column(length = 1000)
    private String notes;

    // ── Booking audit metadata ────────────────────────────────────────────────
    //
    // Tracks who/what created and (optionally) cancelled the booking.
    // Nullable on all fields so transfer bookings and legacy rows are unaffected.
    //
    // createdByType / createdChannel: set at booking creation time.
    //   For the standard WEB checkout: CUSTOMER_ANONYMOUS + WEB.
    //   Null for transfer bookings (created via TransferBookingService).
    //
    // cancelledByType / cancelledChannel / cancelledAt / cancellationReason:
    //   Set only when the booking is cancelled.
    //   cancelledByType=CUSTOMER_ANONYMOUS + cancelledChannel=WEB for manage-booking flow.
    //   cancelledByType=ADMIN + cancelledChannel=ADMIN_PANEL for admin path.

    @Enumerated(EnumType.STRING)
    @Column(name = "created_by_type")
    private BookingActorType createdByType;

    @Enumerated(EnumType.STRING)
    @Column(name = "created_channel")
    private BookingChannel createdChannel;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancelled_by_type")
    private BookingActorType cancelledByType;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancelled_channel")
    private BookingChannel cancelledChannel;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 512)
    private String cancellationReason;

    @Version
    @Column(nullable = false)
    private Long version;

    public void attachRentalDetails(RentalBookingDetails details) {
        this.rentalDetails = details;
        if (details != null) {
            details.setBooking(this);
        }
    }

    public void attachTransferDetails(TransferBookingDetails details) {
        this.transferDetails = details;
        if (details != null) {
            details.setBooking(this);
        }
    }

    public int getRentalDays() {
        if (rentalDetails != null) {
            return rentalDetails.getRentalDays();
        }
        if (transferDetails != null) {
            return transferDetails.getDurationHours();
        }
        return rentalDays;
    }

    public BigDecimal getBaseDailyPrice() {
        return rentalDetails != null ? rentalDetails.getBaseDailyPrice() : baseDailyPrice;
    }

    public BigDecimal getDiscountedDailyPrice() {
        return rentalDetails != null ? rentalDetails.getDiscountedDailyPrice() : discountedDailyPrice;
    }

    public BigDecimal getDiscountPercentage() {
        return rentalDetails != null ? rentalDetails.getDiscountPercentage() : discountPercentage;
    }

    public BigDecimal getRentalCharge() {
        return rentalDetails != null ? rentalDetails.getRentalCharge() : rentalCharge;
    }

    public BigDecimal getOneWayFee() {
        return rentalDetails != null ? rentalDetails.getOneWayFee() : oneWayFee;
    }

    public BigDecimal getPremiumLocationFee() {
        return rentalDetails != null ? rentalDetails.getPremiumLocationFee() : premiumLocationFee;
    }

    public BigDecimal getTax() {
        return rentalDetails != null ? rentalDetails.getTax() : tax;
    }

    public BigDecimal getAddonCharge() {
        return rentalDetails != null ? rentalDetails.getAddonCharge() : addonCharge;
    }

    public int getIncludedKmSnapshot() {
        return rentalDetails != null ? rentalDetails.getIncludedKmSnapshot() : includedKmSnapshot;
    }

    public BigDecimal getUnlimitedKmPriceSnapshot() {
        return rentalDetails != null ? rentalDetails.getUnlimitedKmPriceSnapshot() : unlimitedKmPriceSnapshot;
    }

    public MileageOption getMileageOption() {
        return rentalDetails != null ? rentalDetails.getMileageOption() : mileageOption;
    }

    public BookingOptionType getBookingOptionType() {
        if (rentalDetails != null) {
            return rentalDetails.getBookingOptionType();
        }
        return source == BookingSource.TRANSFER ? null : bookingOptionType;
    }

    public BigDecimal getBookingOptionDailyFee() {
        if (rentalDetails != null) {
            return rentalDetails.getBookingOptionDailyFee();
        }
        return source == BookingSource.TRANSFER ? null : bookingOptionDailyFee;
    }

    public CancellationPolicyType getCancellationPolicyType() {
        if (rentalDetails != null) {
            return rentalDetails.getCancellationPolicyType();
        }
        return source == BookingSource.TRANSFER ? null : cancellationPolicyType;
    }

    public Integer getPassengers() {
        if (transferDetails != null) {
            return transferDetails.getPassengers();
        }
        return passengers;
    }

    public String getNotes() {
        return transferDetails != null ? transferDetails.getNotes() : notes;
    }

    @PrePersist
    public void prePersist() {
        // Instant.now() is intentional: JPA lifecycle callbacks cannot receive Spring beans,
        // so AppClock cannot be injected here. The JVM is pinned to UTC in RentcarApiApplication,
        // making Instant.now() deterministic across all environments.
        this.createdAt = Instant.now();
        if (this.addonCharge == null) {
            this.addonCharge = BigDecimal.ZERO;
        }
        if (this.source == BookingSource.WEB && this.bookingOptionType == BookingOptionType.BEST_PRICE) {
            if (this.bookingOptionDailyFee == null) {
                this.bookingOptionDailyFee = BigDecimal.ZERO;
            }
            if (this.cancellationPolicyType == null) {
                this.cancellationPolicyType = CancellationPolicyType.STRICT;
            }
        }
    }
}
