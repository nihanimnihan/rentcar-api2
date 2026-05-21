import { test, expect, type Page } from '@playwright/test';

/**
 * Rental car review + payment — frontend smoke tests with mocked APIs.
 *
 * Strategy — navigate directly to review.html with deterministic query params.
 * All backend APIs are mocked via page.route(); no Spring Boot instance needed.
 *
 * Flow tested:
 *   review.html?carId=1&...
 *     → fill customer form
 *     → click "Pay and Book"
 *   POST /api/bookings          (intercepted)
 *   POST /api/bookings/42/payments/process  (intercepted)
 *     → success panel visible (happy path)
 *     OR error message visible (failure paths)
 */

// ── Fixture values ────────────────────────────────────────────────────────────

const CAR_ID           = 1;
const BOOKING_ID       = 42;
const PICKUP_DATETIME  = '2030-06-15T10:00';
const DROPOFF_DATETIME = '2030-06-17T10:00';

const REVIEW_QUERY = new URLSearchParams({
  carId:            String(CAR_ID),
  pickupDateTime:   PICKUP_DATETIME,
  dropoffDateTime:  DROPOFF_DATETIME,
  pickupLocation:   'BCN Airport T1',
  dropoffLocation:  'BCN Airport T1',
  mileageOption:    'INCLUDED',
}).toString();

const MOCK_CAR = {
  id:           CAR_ID,
  brand:        'Toyota',
  model:        'Corolla',
  year:         2023,
  category:     'ECONOMY',
  minDriverAge: 21,
  basePrice:    50.00,
  imageUrl:     null,
};

const MOCK_BOOKING_PENDING   = { id: BOOKING_ID, bookingReference: 'RC-260521-K8P4', status: 'PENDING',   carId: CAR_ID, totalPrice: 100.00 };
const MOCK_BOOKING_CONFIRMED = { id: BOOKING_ID, bookingReference: 'RC-260521-K8P4', status: 'CONFIRMED', carId: CAR_ID, totalPrice: 100.00 };
const MOCK_BOOKING_FAILED    = { id: BOOKING_ID, bookingReference: 'RC-260521-K8P4', status: 'FAILED',    carId: CAR_ID, totalPrice: 100.00 };

// ── Helpers ───────────────────────────────────────────────────────────────────

async function mockPageLoadApis(page: Page): Promise<void> {
  await page.route(`**/api/cars/${CAR_ID}**`, route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_CAR) })
  );
  await page.route('**/api/addons/active', route =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([]) })
  );
}

async function mockBookingCreation(page: Page): Promise<void> {
  await page.route('**/api/bookings', route => {
    if (route.request().method() === 'POST') {
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_BOOKING_PENDING) });
    } else {
      route.continue();
    }
  });
}

async function mockPaymentProcess(page: Page, response: object, status = 200): Promise<void> {
  await page.route(`**/api/bookings/${BOOKING_ID}/payments/process`, route =>
    route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(response) })
  );
}

/** Fill all required form fields and click the confirm button. */
async function fillAndSubmit(page: Page): Promise<void> {
  await page.fill('#rfEmail',     'john@example.com');
  await page.fill('#rfFirstName', 'John');
  await page.fill('#rfLastName',  'Doe');
  await page.fill('#rfPhone',     '600000099');
  await page.check('#rfAgeConfirm');
  await page.check('#rfNoFlight');
  await page.check('#rfTerms');
  await page.click('#rfConfirmBtn');
}

// ── Tests ─────────────────────────────────────────────────────────────────────

test('happy path: successful payment shows confirmation screen with booking reference', async ({ page }) => {
  const consoleErrors: string[] = [];
  page.on('console', msg => { if (msg.type() === 'error') consoleErrors.push(msg.text()); });

  await mockPageLoadApis(page);
  await mockBookingCreation(page);
  await mockPaymentProcess(page, MOCK_BOOKING_CONFIRMED);

  await page.goto(`/review.html?${REVIEW_QUERY}`);
  await expect(page.locator('#rfConfirmBtn')).toBeVisible({ timeout: 5000 });

  await fillAndSubmit(page);

  const successPanel = page.locator('#rfSuccessPanel');
  await expect(successPanel).toBeVisible({ timeout: 5000 });
  await expect(successPanel).toContainText('RC-260521-K8P4');
  await expect(successPanel).toContainText('CONFIRMED');

  // Form replaced by success screen — confirm button is gone
  await expect(page.locator('#rfConfirmBtn')).not.toBeVisible();

  const realErrors = consoleErrors.filter(e => !e.includes('favicon') && !e.includes('font'));
  expect(realErrors, `Unexpected console errors: ${realErrors.join(', ')}`).toHaveLength(0);
});

test('payment failure: provider declines → error message shown, button re-enabled, no crash', async ({ page }) => {
  const consoleErrors: string[] = [];
  page.on('console', msg => { if (msg.type() === 'error') consoleErrors.push(msg.text()); });

  await mockPageLoadApis(page);
  await mockBookingCreation(page);
  await mockPaymentProcess(page, MOCK_BOOKING_FAILED);

  await page.goto(`/review.html?${REVIEW_QUERY}`);
  await expect(page.locator('#rfConfirmBtn')).toBeVisible({ timeout: 5000 });

  await fillAndSubmit(page);

  await expect(page.locator('#rfBookingError')).toBeVisible({ timeout: 5000 });
  await expect(page.locator('#rfBookingError')).not.toBeEmpty();
  await expect(page.locator('#rfSuccessPanel')).not.toBeVisible();
  await expect(page.locator('#rfConfirmBtn')).toBeEnabled();

  const realErrors = consoleErrors.filter(e => !e.includes('favicon') && !e.includes('font'));
  expect(realErrors, `Unexpected console errors: ${realErrors.join(', ')}`).toHaveLength(0);
});

test('payment network error: 500 from payment endpoint → controlled error, no white screen', async ({ page }) => {
  const consoleErrors: string[] = [];
  page.on('console', msg => { if (msg.type() === 'error') consoleErrors.push(msg.text()); });

  await mockPageLoadApis(page);
  await mockBookingCreation(page);
  await mockPaymentProcess(page, { message: 'Internal server error' }, 500);

  await page.goto(`/review.html?${REVIEW_QUERY}`);
  await expect(page.locator('#rfConfirmBtn')).toBeVisible({ timeout: 5000 });

  await fillAndSubmit(page);

  await expect(page.locator('#rfBookingError')).toBeVisible({ timeout: 5000 });
  await expect(page.locator('#rfConfirmBtn')).toBeEnabled();
  await expect(page.locator('#rfSuccessPanel')).not.toBeVisible();

  // The 500 response naturally emits a "Failed to load resource" browser console error —
  // that is expected here. Filter it out; any other JS errors would indicate a crash.
  const realErrors = consoleErrors.filter(e =>
    !e.includes('favicon') &&
    !e.includes('font') &&
    !e.includes('500') &&
    !e.includes('Failed to load resource')
  );
  expect(realErrors, `Unexpected console errors: ${realErrors.join(', ')}`).toHaveLength(0);
});
