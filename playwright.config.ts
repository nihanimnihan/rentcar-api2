import { defineConfig } from '@playwright/test';

/**
 * Playwright configuration for RentCar frontend smoke tests.
 *
 * Prerequisites:
 *   The Spring Boot application must be running before executing e2e tests.
 *   Start it with: ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
 *   Default dev port: 8091 (configured in application-dev.yaml)
 *
 * Run tests:
 *   npm run e2e            — headless (CI)
 *   npm run e2e:headed     — with browser visible (debugging)
 *   npm run e2e:report     — open last HTML report
 */
export default defineConfig({
  testDir: './e2e',

  // One retry on CI to tolerate minor flakiness; none locally for fast feedback.
  retries: process.env.CI ? 1 : 0,

  use: {
    baseURL: 'http://localhost:8091',
    headless: true,
    // Capture screenshot and trace only on failure to keep artifacts small.
    screenshot: 'only-on-failure',
    trace: 'on-first-retry',
  },

  // Only Chromium for MVP — add firefox/webkit when the suite grows.
  projects: [
    { name: 'chromium', use: { browserName: 'chromium' } },
  ],

  // HTML report written to playwright-report/ (gitignored).
  reporter: [['html', { open: 'never' }], ['list']],
});
