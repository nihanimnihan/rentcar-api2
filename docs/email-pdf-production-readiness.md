# Email and PDF Production Readiness

## Transactional emails

Production email uses `SmtpEmailService` under the `prod` profile. Local/dev modes use
`FakeEmailService`, which logs messages and stores them in memory for tests without real SMTP.

Customer emails currently covered:

- Booking/payment confirmation after synchronous payment success.
- Booking/payment confirmation after Stripe `payment_intent.succeeded` webhook success.
- Booking cancellation after customer or admin cancellation.
- Refund completed after Stripe refund webhook status changes to `succeeded`.

Cancellation emails include the booking reference, cancellation reason when provided, refund
status, and the message that refunds may take a few business days to appear in the customer's
bank account.

Manage-booking links in customer emails use secure random tokens, stored only as SHA-256 hashes
on the booking row. Existing reference + surname lookup remains available as a fallback when a
token is invalid, expired, or superseded by a newer lifecycle email.

Known launch trade-off: RentCar keeps one active manage-booking token per booking. Sending a
new lifecycle email, such as cancellation or refund-completed, rotates the token and replaces
the previous email link. Customers who open an older email link see a friendly fallback message
and can still access the booking with reference + surname. Revisit multi-token support after
launch if support volume shows customers often use older lifecycle emails.

## Required production environment variables

Core public URL:

- `APP_PUBLIC_BASE_URL` - public application origin used for manage-booking links.
- `RENTCAR_MANAGE_BOOKING_TOKEN_TTL_DAYS` - optional, defaults to `30`.

Database migration required before production deploy:

- Apply `docs/database/2026-06-20-manage-booking-tokens.sql` so manage-booking
  email tokens can be stored as hashes with expiry and revocation metadata.

SMTP:

- `SMTP_HOST`
- `SMTP_PORT` - optional, defaults to `587`.
- `SMTP_USERNAME`
- `SMTP_PASSWORD`
- `SMTP_STARTTLS_ENABLE` - optional, defaults to `true`.
- `MAIL_FROM`
- `MAIL_FROM_NAME` - optional, defaults to `RentCar`.

Stripe lifecycle:

- `STRIPE_API_KEY`
- `STRIPE_PUBLISHABLE_KEY`
- `STRIPE_WEBHOOK_SECRET`

The Stripe webhook endpoint must receive refund events such as `refund.updated` and
`charge.refunded` for refund-completed email delivery.

## Local Mailhog SMTP testing

The default local profiles still use `FakeEmailService`. To send real SMTP messages to
Mailhog, add the `local-smtp` profile. It activates `SmtpEmailService` and points Spring
Mail at `localhost:1025` with no username or password.

Start Mailhog:

```bash
docker run --rm -p 1025:1025 -p 8025:8025 mailhog/mailhog
```

Start the app with local SMTP and fake payment processing:

```bash
SPRING_PROFILES_ACTIVE=dev,local-smtp ./mvnw spring-boot:run
```

Then open the Mailhog UI:

```text
http://localhost:8025
```

Alternative, if you want the local PostgreSQL + Stripe-local profile set:

```bash
SPRING_PROFILES_ACTIVE=local-postgres,stripe-local,local-smtp ./mvnw spring-boot:run
```

That alternative still sends mail through Mailhog, but payment flows require the existing
Stripe-local configuration to be usable for the scenario you are testing.

## PDF/booking contract attachment

No booking contract/PDF generator exists in the current codebase, so production emails do not
attach a PDF contract yet. This is intentionally postponed rather than faked; once a real PDF
generator is added, it should be attached from the SMTP sender and covered with a focused test.
