# RentCar Launch Handoff - 2026-06-19

## Current Goal

Launch the RentCar platform within the next week as a revenue-generating MVP, initially planned for Render Hobby.

The product is not only a marketing website. It includes vehicle search, reservation creation, payment, cancellation/refund flow, email/contract handling, admin management, and a data model that can later support transfers, campaigns, customer accounts, reporting, and external integrations.

## Current Status

The platform is in launch-hardening mode.

- `./mvnw test` passes.
- Core customer booking flow has been tested manually.
- Stripe payment and refund lifecycle has been manually verified.
- Manage booking cancellation updates DB status and triggers refund.
- Frontend customer flow is visually close to launch quality on desktop and mobile.
- Remaining work should focus on production configuration, deployment, smoke testing, and operational readiness.

## Phase 1 MVP Scope

Phase 1 is the launchable, revenue-generating version.

Included:

- Website homepage
- Vehicle/service search
- Date and location selection
- Vehicle listing and filtering
- Vehicle detail / selection
- Add-ons step
- Customer information entry
- Payment step
- Reservation confirmation
- Cancellation request from manage booking
- PDF contract / booking document flow
- Email sending
- EN / ES / TR language support
- Basic admin panel
- Reservation listing
- Vehicle management
- Pricing updates

Explicitly postponed:

- External provider integration
- Advanced membership/customer account system
- Loyalty program
- Mobile application
- Detailed reporting dashboards
- Fully automated fleet/operation management
- Campaign/discount engine
- Advanced dynamic pricing and availability algorithms
- Multi-language content management panel
- Automatic document verification
- SMS and advanced automation integrations

## Completed So Far

### Backend

- Real Stripe payment lifecycle was completed beyond the initial stub state.
- Booking creation, payment confirmation, cancellation, and refund flow works end to end.
- Manage booking cancellation persists cancellation reason and sets status to `CANCELLED`.
- Stripe refund is visible after customer cancellation.
- Booking reference flow is working.
- Booking/pricing cleanup plan was accepted and model separation was implemented.
- Rental and transfer booking detail concerns were separated to reduce null/confusing fields in the core booking model.
- Pricing snapshots are retained for historical booking accuracy.
- Premium location fee logic is in place and tested.
- Mileage option handling is in place.
- Add-on snapshot handling exists so future add-on changes do not rewrite historical bookings.
- Optimistic locking/version behavior was reviewed and accepted as normal JPA lifecycle behavior.
- Car search availability excludes active confirmed bookings and non-expired pending bookings.
- Car search filters now align with backend-supported request fields:
  - `vehicleType`
  - `segment`
  - `transmission`
  - `fuelType`
  - `minSeats`
  - `minBags`
  - `minDriverAge`
  - `premium`
  - `guaranteedModel`
- Broken frontend filter params were fixed:
  - `seats` -> `minSeats`
  - `bags` -> `minBags`
  - `driverAge` -> `minDriverAge`
  - `STATION_WAGON` -> `WAGON`
- Unsupported `hotOffer` filter was removed from the customer filter UI until there is a real DB/campaign model behind it.
- Full Maven test suite now passes according to the latest local run.

### Frontend / UX

- New transparent Rent Car Paradise Deluxe logo was created and applied across customer pages.
- Header logo sizing was adjusted for mobile and desktop.
- Homepage mobile layout was cleaned up.
- Homepage search panel spacing was improved.
- Homepage location picker now opens in a clearer position and scrolls the page when needed.
- Homepage date display was simplified to short labels such as `20 Haz` / `20 Jun`.
- Google Places-style location search was added/aligned across index and cars search flows.
- Cars page search drawer was rebuilt to match the newer index search behavior.
- Cars page search drawer button sizing was adjusted for Turkish text.
- Manage booking page:
  - Branded confirmation modal replaced browser-native confirm dialog.
  - Minimal header restored.
  - Language support fixed for customer actions and cancellation modal.
  - Footer white gap fixed.
- Review page:
  - Mobile header aligned with listing page pattern.
  - Back link to add-ons added.
  - Change search link added.
- Add-ons page:
  - Mobile layout improved.
  - Continue button reduced from oversized mobile appearance.
  - Existing selected add-ons can be restored from URL state.
- Customer flow language support improved across EN / ES / TR.
- Static cache versioning was added on recently changed frontend assets to avoid stale browser files.

### Manual Flow Verified

Known successful flow example:

1. Booking created.
2. Stripe payment collected.
3. Customer opened manage booking.
4. Customer cancelled booking.
5. Cancellation reason saved.
6. DB status became `CANCELLED`.
7. Stripe refund appeared.

## Important Current Model Notes

Car card details come from DB through `/api/cars/search`, not from hardcoded HTML:

- seats
- bags
- doors
- minimum driver age
- transmission
- fuel type
- segment/display class
- daily and total price

Filter options are currently static in `partials/car-filters.html`. They map to backend enum/query fields, but they are not generated dynamically from DB yet.

Electric/hybrid/automatic filters are real enum-backed filters. If `ELECTRIC` returns 0 results, that is expected when there is no active electric rental car in the DB.

`Hot offers` is intentionally not active now because there is no real `hotOffer`/campaign field in the DB model.

## Launch Blockers / P0 Before Go-Live

1. Render deployment dry-run
   - Confirm build command.
   - Confirm start command.
   - Confirm Java version.
   - Confirm static files are served.
   - Confirm app boots with production env vars.

2. Production database setup
   - Create Render/Postgres DB.
   - Confirm schema creation/migration strategy.
   - Decide whether `ddl-auto` is safe for first launch.
   - Take a pre-launch backup or snapshot.
   - Seed/insert real cars, add-ons, prices, and locations.

3. Stripe production readiness
   - Add live secret key.
   - Add publishable key for frontend if needed.
   - Add webhook secret if webhook flow is enabled.
   - Confirm payment intent lifecycle on production config.
   - Confirm refund lifecycle.
   - Confirm currency and statement descriptor.

4. Email production readiness
   - Configure SMTP/provider env vars.
   - Confirm confirmation email sends.
   - Confirm manage-booking link uses production public base URL.
   - Confirm PDF/contract attachment behavior.

5. Admin access
   - Confirm admin URL.
   - Confirm admin credentials.
   - Rotate local/test password before launch.
   - Confirm admin can view bookings, cars, pricing, cancellation/refund status.

6. Production smoke test
   - Homepage search.
   - Cars list.
   - Filters.
   - Detail modal.
   - Add-ons.
   - Review/customer info.
   - Stripe payment.
   - Confirmation.
   - Manage booking lookup.
   - Cancellation/refund.
   - Admin reservation view.

7. Legal/business content
   - Terms and conditions.
   - Privacy policy.
   - Cancellation policy wording.
   - Contact/support email.
   - Company address and branding.

## P1 Shortly After Launch

- Add real campaign/hot-offer model if needed.
- Make filter options dynamic from available fleet metadata.
- Add reporting basics:
  - bookings by day
  - revenue
  - cancelled/refunded bookings
  - most booked cars
- Improve admin pricing UI.
- Add customer account / booking history.
- Add email automation improvements.
- Add operational screens for fleet/calendar management.
- Add better observability:
  - structured logs
  - error alerts
  - payment failure alerts
  - booking lifecycle audit visibility

## P2 Later Phases

- External provider integrations.
- Advanced pricing rules.
- Discount/campaign engine.
- Loyalty program.
- Mobile app.
- Multi-language content management.
- Automatic document verification.
- SMS/WhatsApp automation.
- Advanced fleet operation workflows.

## Recommended Next Action

Move to production readiness checklist:

1. Prepare Render environment variables.
2. Deploy to Render Hobby as a dry-run.
3. Connect production/staging Postgres.
4. Run one full smoke booking in Stripe test mode on Render.
5. Switch to live Stripe only after the Render smoke test is stable.

## Known Caution

Avoid adding new major features before launch. The safest path is to stabilize what exists, deploy, verify payment/cancellation/admin flows, then iterate.
