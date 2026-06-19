-- Persist the customer-facing language selected during web checkout.
-- Existing rows can remain null; application code falls back to English.

ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS language varchar(5);

ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS preferred_language varchar(5);
