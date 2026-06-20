# Admin Authentication

## URLs

- Admin login page: `/admin-login.html`
- Admin dashboard: `/admin/bookings.html`
- Admin vehicle management: `/admin/cars.html`
- Admin add-on management: `/admin/addons.html`
- Admin APIs: `/api/admin/**`

## Local Login

Run the app locally, then open:

`http://localhost:8091/admin-login.html`

Default local credentials come from `src/main/resources/application.yaml` unless overridden:

- Username: `admin`
- Password: `change-me`

If you explicitly run with the main `dev` profile, `src/main/resources/application-dev.yaml` overrides the password to `local-admin-password`. These are local/test credentials only.

## Render Login

Open:

`https://<your-render-service-host>/admin-login.html`

Use the values configured in Render environment variables:

- `ADMIN_USERNAME`
- `ADMIN_PASSWORD`

`ADMIN_PASSWORD` must be a strong production-only secret. Do not use `change-me` or `admin`.

## Production Environment Variables

The `prod` profile requires:

- `ADMIN_USERNAME`
- `ADMIN_PASSWORD`

Admin credentials are loaded into an in-memory Spring Security admin user at startup. The browser admin area uses form login and a session cookie. HTTP Basic remains enabled for authenticated API access and existing admin integration tests.

Customer OAuth remains separate and continues to use the Google OAuth variables:

- `GOOGLE_CLIENT_ID`
- `GOOGLE_CLIENT_SECRET`
