const { chromium } = require('playwright');
(async () => {
  const BASE = process.env.BASE_URL || 'http://localhost:8082';
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext();
  const page = await context.newPage();
  page.on('console', msg => console.log('PAGE_LOG>', msg.text()));
  await page.goto(`${BASE}/signup.html`);
  console.log('Opened signup');

  // Listen for navigation after postMessage
  const [popup] = await Promise.all([
    page.waitForEvent('popup', { timeout: 3000 }).catch(() => null),
    page.click('#googleBtn')
  ]);

  if (!popup) {
    console.log('No popup opened — fallback or blocked');
    await browser.close();
    process.exit(1);
  }

  console.log('Popup opened, will navigate to popup-callback simulation after short delay');
  // Give the parent time to add the message listener
  await page.waitForTimeout(300);
  // Simulate the OAuth provider flow finishing by posting a message from the popup to the opener
  await popup.waitForLoadState('domcontentloaded').catch(() => {});
  await popup.evaluate(() => {
    try {
      const payload = { type: 'oauth', profileComplete: true, returnTo: '/index.html' };
      window.opener.postMessage(payload, '*');
    } catch (e) { /* ignore */ }
  });
  // Wait a moment to allow postMessage handling
  await page.waitForTimeout(500);
  // Check if parent navigated away or not
  const url = page.url();
  console.log('Parent page url after popup callback:', url);
  await browser.close();
})();
