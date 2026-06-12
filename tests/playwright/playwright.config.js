const { devices } = require('@playwright/test');

module.exports = {
  testDir: '.',
  timeout: 30000,
  use: {
    baseURL: process.env.BASE_URL || 'http://localhost:8080',
    headless: true,
    viewport: { width: 1280, height: 800 }
  }
};
