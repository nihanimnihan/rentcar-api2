-- Booking detail split migration for PostgreSQL.
--
-- Run this once before deploying the code that maps RentalBookingDetails and
-- TransferBookingDetails while production keeps spring.jpa.hibernate.ddl-auto=validate.
-- Existing columns on bookings are intentionally kept as a legacy fallback.

CREATE TABLE IF NOT EXISTS rental_booking_details (
    booking_id BIGINT PRIMARY KEY REFERENCES bookings(id) ON DELETE CASCADE,
    rental_days INTEGER NOT NULL,
    base_daily_price NUMERIC(10, 2) NOT NULL,
    discounted_daily_price NUMERIC(10, 2) NOT NULL,
    discount_percentage NUMERIC(5, 2) NOT NULL,
    rental_charge NUMERIC(10, 2) NOT NULL,
    one_way_fee NUMERIC(10, 2) NOT NULL,
    premium_location_fee NUMERIC(10, 2) NOT NULL,
    tax NUMERIC(10, 2) NOT NULL,
    addon_charge NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    included_km_snapshot INTEGER NOT NULL,
    unlimited_km_price_snapshot NUMERIC(10, 2) NOT NULL,
    mileage_option VARCHAR(255) NOT NULL DEFAULT 'INCLUDED',
    booking_option_type VARCHAR(255) NOT NULL DEFAULT 'BEST_PRICE',
    booking_option_daily_fee NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    cancellation_policy_type VARCHAR(255) NOT NULL DEFAULT 'STRICT'
);

CREATE TABLE IF NOT EXISTS transfer_booking_details (
    booking_id BIGINT PRIMARY KEY REFERENCES bookings(id) ON DELETE CASCADE,
    duration_hours INTEGER NOT NULL,
    passengers INTEGER NOT NULL DEFAULT 1,
    hourly_price_snapshot NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    chauffeur_category_code VARCHAR(50) NOT NULL DEFAULT 'UNKNOWN',
    chauffeur_category_name VARCHAR(120) NOT NULL DEFAULT 'UNKNOWN',
    notes VARCHAR(1000)
);

INSERT INTO rental_booking_details (
    booking_id,
    rental_days,
    base_daily_price,
    discounted_daily_price,
    discount_percentage,
    rental_charge,
    one_way_fee,
    premium_location_fee,
    tax,
    addon_charge,
    included_km_snapshot,
    unlimited_km_price_snapshot,
    mileage_option,
    booking_option_type,
    booking_option_daily_fee,
    cancellation_policy_type
)
SELECT
    b.id,
    b.rental_days,
    b.base_daily_price,
    b.discounted_daily_price,
    b.discount_percentage,
    b.rental_charge,
    b.one_way_fee,
    b.premium_location_fee,
    b.tax,
    COALESCE(b.addon_charge, 0.00),
    b.included_km_snapshot,
    b.unlimited_km_price_snapshot,
    COALESCE(b.mileage_option, 'INCLUDED'),
    COALESCE(b.booking_option_type, 'BEST_PRICE'),
    COALESCE(b.booking_option_daily_fee, 0.00),
    COALESCE(b.cancellation_policy_type, 'STRICT')
FROM bookings b
WHERE b.source = 'WEB'
ON CONFLICT (booking_id) DO NOTHING;

INSERT INTO transfer_booking_details (
    booking_id,
    duration_hours,
    passengers,
    hourly_price_snapshot,
    chauffeur_category_code,
    chauffeur_category_name,
    notes
)
SELECT
    b.id,
    GREATEST(COALESCE(b.rental_days, 1), 1),
    GREATEST(COALESCE(b.passengers, 1), 1),
    COALESCE(
        c.hourly_price,
        CASE
            WHEN COALESCE(b.rental_days, 0) > 0 THEN ROUND(b.total_price / b.rental_days, 2)
            ELSE b.total_price
        END,
        0.00
    ),
    COALESCE(cc.code, 'UNKNOWN'),
    COALESCE(cc.name, cc.code, 'UNKNOWN'),
    b.notes
FROM bookings b
LEFT JOIN cars c ON c.id = b.car_id
LEFT JOIN chauffeur_categories cc ON cc.id = c.chauffeur_category_id
WHERE b.source = 'TRANSFER'
ON CONFLICT (booking_id) DO NOTHING;
