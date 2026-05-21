import { test, expect } from '@playwright/test';

/**
 * cars.html date-validation — frontend smoke tests (no backend needed).
 *
 * Strategy: navigate to cars.html with crafted query strings, mock
 * GET /api/cars/search via page.route() to detect unexpected API calls.
 *
 * Tests:
 *  1. Reversed date range  → error panel visible, API NOT called
 *  2. Past pickup date     → error panel visible, API NOT called
 *  3. Missing dates        → error panel visible, API NOT called
 *  4. Valid future dates   → NO error panel, API IS called
 */

// ── Helpers ───────────────────────────────────────────────────────────────────

/** ISO datetime string N days from now at 10:00 */
function futureDate(daysFromNow: number): string {
  const d = new Date();
  d.setDate(d.getDate() + daysFromNow);
  d.setHours(10, 0, 0, 0);
  return d.toISOString().slice(0, 16); // "YYYY-MM-DDTHH:mm"
}

/** ISO datetime string N days in the past at 10:00 */
function pastDate(daysAgo: number): string {
  const d = new Date();
  d.setDate(d.getDate() - daysAgo);
  d.setHours(10, 0, 0, 0);
  return d.toISOString().slice(0, 16);
}

// ── Tests ─────────────────────────────────────────────────────────────────────

test('reversed date range: shows warning alert, does not call cars API', async ({ page }) => {
  let apiCalled = false;
  await page.route('**/api/cars/search**', route => {
    apiCalled = true;
    route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
  });

  const pickup  = futureDate(5);
  const dropoff = futureDate(2); // before pickup → invalid range
  await page.goto(`/cars.html?pickupDateTime=${pickup}&dropoffDateTime=${dropoff}&pickupLocation=BCN`);

  // Error panel must be visible
  await expect(page.locator('#carsDateError')).toBeVisible();
  await expect(page.locator('#carsDateError .rc-alert--warning')).toBeVisible();
  const text = await page.locator('#carsDateError').textContent() ?? '';
  expect(text.length).toBeGreaterThan(10);
  // Must NOT show a raw i18n key
  expect(text).not.toMatch(/\berror\.[a-zA-Z]+\b/);

  expect(apiCalled).toBe(false);
});

test('past pickup date: shows warning alert, does not call cars API', async ({ page }) => {
  let apiCalled = false;
  await page.route('**/api/cars/search**', route => {
    apiCalled = true;
    route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
  });

  const pickup  = pastDate(3);
  const dropoff = futureDate(2);
  await page.goto(`/cars.html?pickupDateTime=${pickup}&dropoffDateTime=${dropoff}&pickupLocation=BCN`);

  await expect(page.locator('#carsDateError')).toBeVisible();
  await expect(page.locator('#carsDateError .rc-alert--warning')).toBeVisible();

  expect(apiCalled).toBe(false);
});

test('missing dates: shows warning alert, does not call cars API', async ({ page }) => {
  let apiCalled = false;
  await page.route('**/api/cars/search**', route => {
    apiCalled = true;
    route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
  });

  await page.goto('/cars.html?pickupLocation=BCN'); // no date params

  await expect(page.locator('#carsDateError')).toBeVisible();
  await expect(page.locator('#carsDateError .rc-alert--warning')).toBeVisible();

  expect(apiCalled).toBe(false);
});

test('valid future dates: no error panel, calls cars API', async ({ page }) => {
  let apiCalled = false;
  await page.route('**/api/cars/search**', route => {
    apiCalled = true;
    route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
  });

  const pickup  = futureDate(3);
  const dropoff = futureDate(6);
  await page.goto(`/cars.html?pickupDateTime=${pickup}&dropoffDateTime=${dropoff}&pickupLocation=BCN`);

  // Wait briefly for the async API call to happen
  await page.waitForTimeout(500);

  await expect(page.locator('#carsDateError')).toBeHidden();
  expect(apiCalled).toBe(true);
});
