-- Production helper for support request phone fields.
-- Columns stay nullable so existing support_requests rows continue to validate.
-- New public API submissions require both fields through request validation.
ALTER TABLE support_requests
    ADD COLUMN IF NOT EXISTS phone_country_code VARCHAR(8);

ALTER TABLE support_requests
    ADD COLUMN IF NOT EXISTS phone_number VARCHAR(24);
