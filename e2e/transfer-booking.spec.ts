import { test, expect, type Page, type Request } from '@playwright/test';

/**
 * Airport Transfer Booking — frontend smoke test with mocked APIs.
 *
 * Strategy — navigate directly to airport-transfer-offers.html with known
 * query params, bypassing the custom date-picker widget entirely. This makes
 * pickupDateTime and durationHours fully deterministic without touching any
 * production frontend code.
 *
 * Flow:
 *   airport-transfer-offers.html?pickupDateTime=2030-06-15T10:00&durationHours=2&...
 *     → select 2 passengers via dropdown
 *     → Next on RIDE offer card
 *   airport-transfer-booking.html (params forwarded automatically)
 *     → fill customer form
 *     → Book Now
 *   POST /api/transfer/bookings  (intercepted — assert exact payload)
 *     → success panel visible
 *
 * All backend APIs are mocked; no PostgreSQL or running Spring Boot required.
 * To run against a real backend, replace route.fulfill() calls with
 * route.continue() — the payload and success-panel assertions remain identical.
 *
 * Prerequisite (real backend mode only):
 *   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
 */

// ── Deterministic fixture values ─────────────────────────────────────────────

const PICKUP_DATETIME = '2030-06-15T10:00';
const DURATION_HOURS  = 2;
const INCLUDED_KM     = 60;
const PASSENGER_COUNT = 2;
const CATEGORY_ID     = 1;

/** Query string used to open the offers page at a known, stable state. */
const OFFERS_QUERY = new URLSearchParams({
  pickupDateTime: PICKUP_DATETIME,
  durationHours:  String(DURATION_HOURS),
  includedKm:     String(INCLUDED_KM),
  pickupLocation: 'BCN Airport T1',
}).toString();

const MOCK_OFFER = {
  categoryId:      CATEGORY_ID,
  code:            'RIDE',
  name:            'Ride',
  description:     'Reliable ride at the best price.',
  seats:           3,
  bags:            2,
  electric:        false,
  imageUrl:        null,
  hourlyPriceFrom: 95.00,
  totalPrice:      190.00,
  available:       true,
};

const MOCK_BOOKING_RESPONSE = {
  id:               42,
  status:           'PENDING',
  customerName:     'Jane Smith',
  customerEmail:    'jane@example.com',
  pickupDateTime:   PICKUP_DATETIME,
  dropoffDateTime:  '2030-06-15T12:00',
  durationHours:    DURATION_HOURS,
  categoryCode:     'RIDE',
  categoryName:     'Ride',
  assignedCarBrand: 'BMW',
  assignedCarModel: 'X1',
  passengers:       PASSENGER_COUNT,
  hourlyPrice:      95.00,
  totalPrice:       190.00,
  notes:            'Window seat please',
};

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Mock /api/transfer/durations and /api/transfer/offers. */
async function mockOffersApi(page: Page): Promise<void> {
  await page.route('**/api/transfer/durations', route =>
    route.fulfill({
      status:      200,
      contentType: 'application/json',
      body:        JSON.stringify([{ id: 1, hours: DURATION_HOURS, includedKm: INCLUDED_KM }]),
    })
  );
  await page.route('**/api/transfer/offers**', route =>
    route.fulfill({
      status:      200,
      contentType: 'application/json',
      body:        JSON.stringify([MOCK_OFFER]),
    })
  );
}

/** Intercept POST /api/transfer/bookings, capture the request body, return mock success. */
async function interceptBooking(page: Page): Promise<{ captured: Request | null }> {
  const ref = { captured: null as Request | null };
  await page.route('**/api/transfer/bookings', route => {
    ref.captured = route.request();
    route.fulfill({
      status:      200,
      contentType: 'application/json',
      body:        JSON.stringify(MOCK_BOOKING_RESPONSE),
    });
  });
  return ref;
}

// ── Test ──────────────────────────────────────────────────────────────────────

test('airport transfer booking frontend smoke test with mocked APIs', async ({ page }) => {
  const consoleErrors: string[] = [];
  page.on('console', msg => {
    if (msg.type() === 'error') consoleErrors.push(msg.text());
  });

  await mockOffersApi(page);

  // ── Step 1: offers page — navigate with deterministic query params ──────────
  // Bypasses the custom date-picker widget; pickupDateTime and durationHours
  // are injected directly via URL, exactly as airport-transfer.html would do.
  await page.goto(`/airport-transfer-offers.html?${OFFERS_QUERY}`);

  // Wait for the mocked RIDE offer card to render
  const offerCard = page.locator('.transfer-offer-card').first();
  await expect(offerCard).toBeVisible({ timeout: 5000 });
  await expect(offerCard).toContainText('Ride');

  // Select 2 passengers via the passenger-count dropdown.
  // currentPassengers defaults to 1 inside the JS module; must be set via
  // the dropdown so the correct value is forwarded to the booking page.
  await page.click('#passengersBtn');
  await page.click(`#passengersDropdown li[data-value="${PASSENGER_COUNT}"]`);

  // Click Next — the JS copies all query params and appends categoryId + passengers
  await offerCard.locator('.transfer-next-btn').click();

  // ── Step 2: booking page ───────────────────────────────────────────────────
  await page.waitForURL('**/airport-transfer-booking.html**');

  // Verify summary sidebar is populated before filling the form
  await expect(page.locator('#atbSummary')).toBeVisible();

  // Fill required customer fields
  await page.fill('#atbFirstName',      'Jane');
  await page.fill('#atbLastName',       'Smith');
  await page.fill('#atbEmail',          'jane@example.com');
  await page.selectOption('#atbCountryCode', '+34');
  await page.fill('#atbPhone',          '600000099');
  await page.fill('#atbNotesToDriver',  'Window seat please');

  // Register the booking interceptor before clicking so the request is always captured
  const bookingReq = await interceptBooking(page);
  await page.click('#atbBookNow');

  // Wait for the interceptor to fire
  await page.waitForTimeout(500);

  // ── Step 3: assert exact request payload ──────────────────────────────────
  expect(bookingReq.captured, 'POST /api/transfer/bookings was not called').not.toBeNull();

  const payload = JSON.parse(bookingReq.captured!.postData() ?? '{}');

  // Core booking fields — exact values
  expect(payload.pickupDateTime,  'pickupDateTime').toBe(PICKUP_DATETIME);
  expect(payload.durationHours,   'durationHours').toBe(DURATION_HOURS);
  expect(payload.categoryId,      'categoryId').toBe(CATEGORY_ID);
  expect(payload.passengerCount,  'passengerCount — key must be "passengerCount" not "passengers"').toBe(PASSENGER_COUNT);

  // Customer fields
  expect(payload.customerName,    'customerName').toBe('Jane Smith');
  expect(payload.customerEmail,   'customerEmail').toBe('jane@example.com');
  expect(payload.customerPhone,   'customerPhone').toBeTruthy();
  expect(payload.notes,           'notes').toBe('Window seat please');
  // pickupLocation is used in the summary sidebar but not included in the POST payload

  // ── Step 4: success state ─────────────────────────────────────────────────
  await expect(page.locator('#atbSuccessPanel')).toBeVisible({ timeout: 3000 });
  await expect(page.locator('#atbFormSection')).toBeHidden();

  // ── Step 5: no console errors ─────────────────────────────────────────────
  const realErrors = consoleErrors.filter(e =>
    !e.includes('favicon') &&
    !e.includes('font') &&
    !e.includes('i18n')
  );
  expect(realErrors, `Unexpected console errors: ${realErrors.join(', ')}`).toHaveLength(0);
});
