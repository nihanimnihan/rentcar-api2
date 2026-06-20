-- Manual migration helper for passwordless email OTP authentication.
-- Production uses ddl-auto=validate; run this before deploying the matching application code.

CREATE TABLE IF NOT EXISTS email_otp_codes (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    profile_token_hash VARCHAR(128) NULL,
    profile_token_expires_at TIMESTAMPTZ NULL,
    profile_completed_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_email_otp_codes_email_active
    ON email_otp_codes (email, consumed_at, created_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS idx_email_otp_codes_profile_token_hash
    ON email_otp_codes (profile_token_hash)
    WHERE profile_token_hash IS NOT NULL;

ALTER TABLE app_users
    ADD COLUMN IF NOT EXISTS phone_country_code VARCHAR(16),
    ADD COLUMN IF NOT EXISTS phone_number VARCHAR(40);

-- Country is no longer required for registration/profile completion. Keep the
-- column for existing users and compatibility with older reads.
ALTER TABLE app_users
    ALTER COLUMN country DROP NOT NULL;
