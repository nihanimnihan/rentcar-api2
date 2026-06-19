-- Manage-booking email links use opaque random tokens.
-- Only the SHA-256 token hash is stored on the booking row.

ALTER TABLE bookings
    ADD COLUMN manage_token_hash varchar(64),
    ADD COLUMN manage_token_expires_at timestamp with time zone,
    ADD COLUMN manage_token_revoked_at timestamp with time zone;

CREATE UNIQUE INDEX uk_bookings_manage_token_hash
    ON bookings (manage_token_hash)
    WHERE manage_token_hash IS NOT NULL;
