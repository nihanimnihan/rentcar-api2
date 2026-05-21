import { test, expect, type Page } from '@playwright/test';

/**
 * Manage Booking page — frontend smoke tests with mocked APIs.
 *
 * Strategy: navigate directly to manage-booking.html, mock GET /api/bookings/manage
 * via page.route(). No Spring Boot instance needed.
 *
 * Tests:
 *  1. Not-found: backend message is rendered in the error alert (not raw i18n key)
 *  2. Success: booking details card is rendered, no raw key visible
 *  3. Network error: generic fallback shown cleanly (not raw key)
 */

const MANAGE_URL = '/manage-booking.html';

const FRIENDLY_NOT_FOUND_MSG =
  "We couldn't find a booking with these details. Please check your reference and last name.";

const MOCK_BOOKING = {
  id:               99,
  bookingReference: 'RC-260521-ABCD',
  status:           'CONFIRMED',
  totalPrice:       120.00,
  rentalDays:       2,
  pickupDateTime:   '2030-06-15T10:00',
  dropoffDateTime:  '2030-06-17T10:00',
  car: { brand: 'Toyota', model: 'Corolla' },
  customer: { fullName: 'Jane Doe' },
};

// ── Helper ────────────────────────────────────────────────────────────────────

async function fillAndSubmit(page: Page, ref: string, lastName: string): Promise<void> {
  await page.fill('#mfReference', ref);
  await page.fill('#mfLastName',  lastName);
  await page.click('#mfSubmitBtn');
}

// ── Tests ─────────────────────────────────────────────────────────────────────

test('not-found: displays backend friendly message, not raw i18n key', async ({ page }) => {
  await page.route('**/api/bookings/manage**', route =>
    route.fulfill({
      status:      404,
      contentType: 'application/json',
      body: JSON.stringify({
        error:   'Not found',
        message: FRIENDLY_NOT_FOUND_MSG,
      }),
    })
  );

  await page.goto(MANAGE_URL);
  await fillAndSubmit(page, 'RC-260521-XXXX', 'Smith');

  const errorEl = page.locator('#mfError');
  await expect(errorEl).toBeVisible();

  // Panel title should say "Booking not found"
  await expect(errorEl.locator('.rentcar-search-error-title')).toContainText('Booking not found');

  // Panel message should carry the backend message
  await expect(errorEl.locator('.rentcar-search-error-message')).toContainText(FRIENDLY_NOT_FOUND_MSG);

  // Must NOT show raw i18n keys
  const errorText = await errorEl.textContent() ?? '';
  expect(errorText).not.toMatch(/\bnotFound\b/);
  expect(errorText).not.toMatch(/\bmanage\./);
  expect(errorText).not.toMatch(/\bnotFoundTitle\b/);
});

test('not-found: result card is hidden when error is shown', async ({ page }) => {
  await page.route('**/api/bookings/manage**', route =>
    route.fulfill({
      status:      404,
      contentType: 'application/json',
      body: JSON.stringify({ error: 'Not found', message: FRIENDLY_NOT_FOUND_MSG }),
    })
  );

  await page.goto(MANAGE_URL);
  await fillAndSubmit(page, 'RC-000000-XXXX', 'Nobody');

  await expect(page.locator('#mfError')).toBeVisible();
  await expect(page.locator('#mfResultSection')).toBeHidden();
});

test('success: booking details rendered, no raw i18n keys visible', async ({ page }) => {
  await page.route('**/api/bookings/manage**', route =>
    route.fulfill({
      status:      200,
      contentType: 'application/json',
      body:        JSON.stringify(MOCK_BOOKING),
    })
  );

  await page.goto(MANAGE_URL);
  await fillAndSubmit(page, 'RC-260521-ABCD', 'Doe');

  const resultSection = page.locator('#mfResultSection');
  await expect(resultSection).toBeVisible();

  // Booking reference and car name should appear
  await expect(resultSection).toContainText('RC-260521-ABCD');
  await expect(resultSection).toContainText('Toyota');

  // No raw i18n dot-notation keys should appear anywhere on the page
  const bodyText = await page.locator('body').textContent() ?? '';
  expect(bodyText).not.toMatch(/\bmanage\.[a-zA-Z]+\b/);
  expect(bodyText).not.toMatch(/\bbookingDetails\b/);

  // Error panel should remain hidden
  await expect(page.locator('#mfError')).toBeHidden();
});

test('network error: shows clean fallback message, not raw key', async ({ page }) => {
  await page.route('**/api/bookings/manage**', route => route.abort('failed'));

  await page.goto(MANAGE_URL);
  await fillAndSubmit(page, 'RC-260521-XXXX', 'Doe');

  const errorEl = page.locator('#mfError');
  await expect(errorEl).toBeVisible();

  const errorText = await errorEl.textContent() ?? '';
  // Must be human-readable, not a raw key
  expect(errorText).not.toMatch(/\bnetworkError\b/);
  expect(errorText).not.toMatch(/\bmanage\./);
  expect(errorText.length).toBeGreaterThan(10);
});
