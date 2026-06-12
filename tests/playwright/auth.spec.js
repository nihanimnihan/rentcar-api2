const { test, expect } = require('@playwright/test');
const BASE = process.env.BASE_URL || 'http://localhost:8080';

test.describe('Google OAuth button', () => {
  test('default returnTo', async ({ page }) => {
    await page.goto(`${BASE}/signup.html`);
    const reqPromise = page.waitForRequest(req => req.url().includes('/oauth2/authorize') && req.method() === 'GET');
    await page.click('#googleBtn');
    const req = await reqPromise;
    const url = new URL(req.url());
    expect(url.pathname).toBe('/oauth2/authorize');
    expect(url.searchParams.get('provider')).toBe('google');
    expect(url.searchParams.get('returnTo')).toBe('/index.html');
  });

  test('preserves query params in returnTo', async ({ page }) => {
    const encodedReturn = encodeURIComponent('/cars.html?pickupLocation=BCN');
    await page.goto(`${BASE}/signup.html?returnTo=${encodedReturn}`);
    const reqPromise = page.waitForRequest(req => req.url().includes('/oauth2/authorize'));
    await page.click('#googleBtn');
    const req = await reqPromise;
    const url = new URL(req.url());
    expect(url.searchParams.get('returnTo')).toBe('/cars.html?pickupLocation=BCN');
  });

  test('works when optional elements missing', async ({ page }) => {
    // Remove optional elements before DOMContentLoaded so auth.js sees them as missing
    await page.addInitScript(() => {
      document.addEventListener('DOMContentLoaded', () => {
        ['firstName','lastName','country','continueEmailBtn','verifyCodeBtn','saveProfileBtn'].forEach(id => {
          const el = document.getElementById(id);
          if (el) el.remove();
        });
      });
    });

    await page.goto(`${BASE}/signup.html`);
    const reqPromise = page.waitForRequest(req => req.url().includes('/oauth2/authorize'));
    await page.click('#googleBtn');
    const req = await reqPromise;
    expect(new URL(req.url()).searchParams.get('provider')).toBe('google');
  });

  test('unsafe returnTo falls back to /index.html', async ({ page }) => {
    const unsafe = encodeURIComponent('//evil.com');
    await page.goto(`${BASE}/signup.html?returnTo=${unsafe}`);
    const reqPromise = page.waitForRequest(req => req.url().includes('/oauth2/authorize'));
    await page.click('#googleBtn');
    const req = await reqPromise;
    expect(new URL(req.url()).searchParams.get('returnTo')).toBe('/index.html');

    const u2 = encodeURIComponent('javascript:alert(1)');
    await page.goto(`${BASE}/signup.html?returnTo=${u2}`);
    const req2P = page.waitForRequest(req => req.url().includes('/oauth2/authorize'));
    await page.click('#googleBtn');
    const req2 = await req2P;
    expect(new URL(req2.url()).searchParams.get('returnTo')).toBe('/index.html');
  });
});
