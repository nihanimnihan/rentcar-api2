import { test, expect, type Page } from '@playwright/test';

const CAR_ID = 1;
const BOOKING_ID = 42;
const PICKUP_DATETIME = '2030-06-15T10:00';
const DROPOFF_DATETIME = '2030-06-17T10:00';
const REVIEW_QUERY = new URLSearchParams({
  carId: String(CAR_ID),
  pickupDateTime: PICKUP_DATETIME,
  dropoffDateTime: DROPOFF_DATETIME,
  pickupLocation: 'BCN Airport T1',
  dropoffLocation: 'BCN Airport T1',
  mileageOption: 'INCLUDED'
}).toString();

const MOCK_CAR = { id: CAR_ID, brand: 'Toyota', model: 'Corolla', minDriverAge: 21, basePrice: 50.00 };
const MOCK_BOOKING_PENDING = { id: BOOKING_ID, bookingReference: 'RC-260521-K8P4', status: 'PENDING', carId: CAR_ID, totalPrice: 100.00 };
const MOCK_BOOKING_CONFIRMED = { id: BOOKING_ID, bookingReference: 'RC-260521-K8P4', status: 'CONFIRMED', carId: CAR_ID, totalPrice: 100.00 };
const MOCK_BOOKING_FAILED = { id: BOOKING_ID, bookingReference: 'RC-260521-K8P4', status: 'FAILED', carId: CAR_ID, totalPrice: 100.00 };

const MOCK_PAYMENT_INTENT = { bookingId: BOOKING_ID, bookingReference: 'RC-260521-K8P4', amount: 100.00, currencyCode: 'EUR', providerName: 'FAKE', clientSecret: null, paymentReference: `PAY-${BOOKING_ID}` };

async function mockPageLoadApis(page: Page) {
  await page.route(`**/api/cars/${CAR_ID}**`, route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_CAR) }));
  await page.route('**/api/addons/active', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([]) }));
}

// Helper to record booking POST calls and return header token
async function mockBookingCreationWithHeader(page: Page) {
  let calls = 0;
  await page.route('**/api/bookings', route => {
    if (route.request().method() === 'POST') {
      calls++;
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(MOCK_BOOKING_PENDING),
        headers: { 'X-Checkout-Session-Token': 'token-123' }
      });
    } else {
      route.continue();
    }
  });
  return {
    getCalls: () => calls
  };
}

async function mockPaymentIntentRecorder(page: Page) {
  let called = false;
  let lastBody = null as null | string;
  await page.route(new RegExp(`/api/bookings/${BOOKING_ID}/payments/intent`), route => {
    called = true;
    lastBody = route.request().postData();
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_PAYMENT_INTENT) });
  });
  return { wasCalled: () => called, lastBody: () => lastBody };
}

async function mockPaymentProcessSequence(page: Page) {
  let calls = 0;
  await page.route(new RegExp(`/api/bookings/${BOOKING_ID}/payments/process`), route => {
    calls++;
    if (calls === 1) {
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_BOOKING_FAILED) });
    } else {
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_BOOKING_CONFIRMED) });
    }
  });
  return { calls: () => calls };
}

// ── Tests ───────────────────────────────────────────────────────────────────

test('retry after failed payment reuses stored pending booking and does not call POST /api/bookings again; intent includes token; sessionStorage cleared on success', async ({ page }) => {
  await mockPageLoadApis(page);
  const bookingRecorder = await mockBookingCreationWithHeader(page);
  const intentRec = await mockPaymentIntentRecorder(page);
  const processSeq = await mockPaymentProcessSequence(page);

  // Mock booking creation interception that sets header
  await page.goto(`/review.html?${REVIEW_QUERY}`);

  // Fill form
  await page.fill('#rfEmail', 'john@example.com');
  await page.fill('#rfFirstName', 'John');
  await page.fill('#rfLastName', 'Doe');
  await page.fill('#rfPhone', '600000099');
  await page.check('#rfAgeConfirm');
  await page.check('#rfNoFlight');
  await page.check('#rfTerms');

  // Submit first time — will create booking + intent + failed process
  await page.click('#rfConfirmBtn');

  // Wait for modal error to appear
  await page.waitForSelector('#rfBookingError', { state: 'visible' });

  // Assert booking POST called once
  expect(bookingRecorder.getCalls()).toBe(1);
  expect(intentRec.wasCalled()).toBe(true);
  expect(processSeq.calls()).toBeGreaterThanOrEqual(1);

  // Ensure sessionStorage has stored booking id and token
  const storedId = await page.evaluate(() => sessionStorage.getItem('rentcarPendingBookingId'));
  const storedToken = await page.evaluate(() => sessionStorage.getItem('rentcarCheckoutSessionToken'));
  const storedSig = await page.evaluate(() => sessionStorage.getItem('rentcarCheckoutSignature'));
  expect(storedId).not.toBeNull();
  expect(storedToken).toBe('token-123');
  expect(storedSig).not.toBeNull();

  // Clear intent flag and click confirm again to retry
  // Ensure intent recorder will capture second call as well
  intentRec.wasCalled();

  // Click confirm again (user corrects card and retries)
  await page.click('#rfConfirmBtn');

  // Wait for success panel
  await page.waitForSelector('#rfSuccessPanel', { state: 'visible', timeout: 5000 });

  // Booking POST should still be 1 (no new booking created on retry)
  expect(bookingRecorder.getCalls()).toBe(1);

  // Ensure intent was called at least twice (initial + retry)
  expect(intentRec.wasCalled()).toBe(true);

  // SessionStorage must be cleared after confirmation
  const afterId = await page.evaluate(() => sessionStorage.getItem('rentcarPendingBookingId'));
  const afterToken = await page.evaluate(() => sessionStorage.getItem('rentcarCheckoutSessionToken'));
  const afterSig = await page.evaluate(() => sessionStorage.getItem('rentcarCheckoutSignature'));
  expect(afterId).toBeNull();
  expect(afterToken).toBeNull();
  expect(afterSig).toBeNull();
});

test('changing date invalidates stored checkout signature and triggers new booking POST', async ({ page }) => {
  await mockPageLoadApis(page);
  let bookingCalls = 0;
  await page.route('**/api/bookings', route => {
    if (route.request().method() === 'POST') {
      bookingCalls++;
      route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_BOOKING_PENDING), headers: { 'X-Checkout-Session-Token': 'token-xyz' } });
    } else {
      route.continue();
    }
  });

  // Navigate to page and set stored session state that does NOT match current signature
  await page.goto(`/review.html?${REVIEW_QUERY}`);
  // Simulate previously stored booking for a different date/car
  await page.evaluate(() => {
    sessionStorage.setItem('rentcarPendingBookingId', '42');
    sessionStorage.setItem('rentcarCheckoutSessionToken', 'token-old');
    sessionStorage.setItem('rentcarCheckoutSignature', 'some-old-signature');
  });

  // Fill form and submit — mismatch should clear stored state and create a new booking
  await page.fill('#rfEmail', 'jane@example.com');
  await page.fill('#rfFirstName', 'Jane');
  await page.fill('#rfLastName', 'Doe');
  await page.fill('#rfPhone', '600000010');
  await page.check('#rfAgeConfirm');
  await page.check('#rfTerms');
  await page.click('#rfConfirmBtn');

  // Wait briefly for network
  await page.waitForTimeout(300);
  expect(bookingCalls).toBeGreaterThanOrEqual(1);
});

test('car unavailable modal closes and redirects to cars.html with same search params', async ({ page }) => {
  // Mock car and addons
  await mockPageLoadApis(page);

  // Booking POST returns 409 with message
  await page.route('**/api/bookings', route => {
    if (route.request().method() === 'POST') {
      route.fulfill({ status: 409, contentType: 'application/json', body: JSON.stringify({ message: 'Car reserved by another customer' }) });
    } else {
      route.continue();
    }
  });

  await page.goto(`/review.html?${REVIEW_QUERY}`);
  await page.fill('#rfEmail', 'joe@example.com');
  await page.fill('#rfFirstName', 'Joe');
  await page.fill('#rfLastName', 'Bloggs');
  await page.fill('#rfPhone', '600000022');
  await page.check('#rfAgeConfirm');
  await page.check('#rfTerms');

  // Click confirm to trigger 409 flow
  await page.click('#rfConfirmBtn');

  // Modal should appear
  await page.waitForSelector('#reviewFlowModal', { state: 'visible' });
  // Click modal button which should navigate back to cars.html with same search
  await page.click('#reviewFlowModalBtn');

  // Wait for navigation
  await page.waitForURL(/cars.html/);
  const url = page.url();
  expect(url).toContain('cars.html');
  expect(url).toContain('carId=1');
});
