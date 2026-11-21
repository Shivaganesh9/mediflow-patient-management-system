import { defineConfig, devices } from '@playwright/test';

/**
 * Browser E2E configuration.
 *
 * Two projects share one config:
 *  - `chromium` exercises the real React application with a Playwright route double
 *    (see e2e/fixtures/mock-api.ts). It is deterministic and needs no backend.
 *  - `demo` opens the frontend-only demo build (`vite --mode demo`) and clicks through the seeded
 *    synthetic data exactly as a developer would. It proves demo mode itself works, not the
 *    backend: real persistence is covered by the Maven integration tests and testing/e2e.
 *
 * Each project gets its own dev server and port so a developer's own `npm run dev` on 3000 never
 * collides with either suite.
 */
const PORT = Number(process.env.E2E_WEB_PORT ?? 3100);
const DEMO_PORT = Number(process.env.E2E_DEMO_PORT ?? 3101);
const API_BASE = process.env.E2E_API_BASE_URL ?? `http://127.0.0.1:${PORT}`;
const HOST = `http://127.0.0.1:${PORT}`;
const DEMO_HOST = `http://127.0.0.1:${DEMO_PORT}`;

export default defineConfig({
  testDir: './e2e/specs',
  outputDir: './e2e/test-results',
  timeout: 30_000,
  expect: { timeout: 7_000 },
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 2 : undefined,
  reporter: process.env.CI
    ? [['list'], ['html', { outputFolder: 'e2e/report', open: 'never' }]]
    : [['list']],
  use: {
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
  },
  projects: [
    {
      name: 'chromium',
      testDir: './e2e/specs',
      use: { ...devices['Desktop Chrome'], baseURL: HOST },
    },
    {
      name: 'demo',
      testDir: './e2e/demo',
      use: { ...devices['Desktop Chrome'], baseURL: DEMO_HOST },
    },
  ],
  webServer: [
    {
      command: `npm run dev -- --host 127.0.0.1 --port ${PORT} --strictPort`,
      url: HOST,
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
      env: { VITE_API_BASE_URL: API_BASE },
    },
    {
      command: `npm run demo -- --host 127.0.0.1 --port ${DEMO_PORT} --strictPort`,
      url: DEMO_HOST,
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
    },
  ],
});
