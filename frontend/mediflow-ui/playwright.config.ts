import { defineConfig, devices } from '@playwright/test';

/**
 * Browser E2E configuration.
 *
 * The tests exercise the real React application but stub the HTTP API with Playwright route
 * handlers (see e2e/fixtures/mock-api.ts). That keeps the browser suite deterministic and fast,
 * while the real service semantics (PostgreSQL constraints, Kafka, idempotency) are covered by the
 * Maven integration tests and by testing/e2e (the cross-service journey suite).
 *
 * The dev server is started on a dedicated port so it never collides with a developer's own
 * `npm run dev` on 3000. The API base URL is the same origin, so mocked requests need no CORS
 * preflight handling.
 */
const PORT = Number(process.env.E2E_WEB_PORT ?? 3100);
const API_BASE = process.env.E2E_API_BASE_URL ?? `http://127.0.0.1:${PORT}`;
const HOST = `http://127.0.0.1:${PORT}`;

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
    baseURL: HOST,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: `npm run dev -- --host 127.0.0.1 --port ${PORT} --strictPort`,
    url: HOST,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
    env: { VITE_API_BASE_URL: API_BASE },
  },
});
