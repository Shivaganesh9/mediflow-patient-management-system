import { expect, Page, test } from '@playwright/test';

/**
 * Demo-mode smoke tests.
 *
 * These run against `vite --mode demo` (its own project/server in playwright.config.ts) and prove
 * that the frontend-only demo works end to end: the banner, one-click role sign-in, the appointment
 * lifecycle, a server-side conflict and payment-idempotency replay. They deliberately do not touch
 * `e2e/fixtures/mock-api.ts` — there is no page.route double here, only the app's own demo adapter.
 */

const banner = (page: Page) => page.getByRole('region', { name: 'Demo mode' });

async function enterDemo(page: Page, role: 'Patient' | 'Doctor' | 'Admin') {
  await page.goto('/');
  await expect(banner(page)).toBeVisible();
  await banner(page).getByRole('button', { name: role }).click();
}

/** Local `datetime-local` value, e.g. 2026-11-23T10:00. */
function localInput(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(
    date.getHours(),
  )}:${pad(date.getMinutes())}`;
}

test.describe('frontend-only demo mode', () => {
  test('labels the demo and signs in with one click', async ({ page }) => {
    await enterDemo(page, 'Patient');

    await expect(page.getByRole('heading', { name: 'Welcome back, Ada.' })).toBeVisible();
    await expect(banner(page)).toContainText('Simulated data');
    await expect(banner(page)).toContainText('Ada Patient');

    // Seeded appointments are visible without any backend.
    await page.getByRole('link', { name: 'Appointments' }).click();
    await expect(page.getByRole('row', { name: /Annual checkup/ })).toBeVisible();
  });

  test('resets the demo world from the banner', async ({ page }) => {
    await enterDemo(page, 'Patient');
    await expect(banner(page)).toContainText('Ada Patient');

    await banner(page).getByRole('button', { name: 'Reset demo data' }).click();

    await expect(page.getByRole('heading', { name: 'Sign in to your workspace' })).toBeVisible();
    await expect(banner(page)).not.toContainText('Ada Patient');
  });

  test('runs the appointment lifecycle as a clinician', async ({ page }) => {
    page.on('dialog', (dialog) => void dialog.accept());
    await enterDemo(page, 'Doctor');
    await expect(page.getByRole('heading', { name: 'Welcome back, Dana.' })).toBeVisible();

    await page.getByRole('link', { name: 'Appointments' }).click();
    const row = page.getByRole('row', { name: /Annual checkup/ });
    await expect(row.getByText('SCHEDULED')).toBeVisible();

    await row.getByRole('button', { name: 'Confirm', exact: true }).click();
    await expect(row.getByText('CONFIRMED')).toBeVisible();

    await row.getByRole('button', { name: 'Start', exact: true }).click();
    await expect(row.getByText('IN PROGRESS')).toBeVisible();

    await row.getByRole('button', { name: 'Complete', exact: true }).click();
    await expect(row.getByText('COMPLETED')).toBeVisible();
  });

  test('rejects a booking outside the doctor working hours', async ({ page }) => {
    await enterDemo(page, 'Patient');
    await page.getByRole('link', { name: 'Appointments' }).click();
    await page.getByRole('button', { name: 'Schedule visit' }).click();

    const modal = page.getByRole('dialog', { name: 'Schedule visit' });
    await expect(modal).toBeVisible();

    // Dana Doctor works Mondays and Wednesdays only; the next Sunday is never bookable.
    const today = new Date();
    const daysUntilSunday = (7 - today.getDay()) % 7 || 7;
    const sunday = new Date(today.getFullYear(), today.getMonth(), today.getDate() + daysUntilSunday);
    const start = new Date(sunday.getFullYear(), sunday.getMonth(), sunday.getDate(), 10, 0);
    const end = new Date(sunday.getFullYear(), sunday.getMonth(), sunday.getDate(), 10, 30);

    await modal.getByLabel(/Doctor/).selectOption({ index: 1 });
    await modal.getByLabel(/^Start/).fill(localInput(start));
    await modal.getByLabel(/^End/).fill(localInput(end));
    await modal.getByLabel(/^Reason/).fill('Demo conflict check');
    await modal.getByRole('button', { name: 'Schedule' }).click();

    await expect(modal.getByText(/no working hours configured for SUNDAY/i)).toBeVisible();
    await expect(modal.getByText(/Please choose a different time slot/)).toBeVisible();
  });

  test('replays a payment after a timeout without charging twice', async ({ page }) => {
    await enterDemo(page, 'Admin');
    await page.getByRole('navigation', { name: 'Primary' }).getByRole('link', { name: 'Billing' }).click();
    await page.getByRole('cell', { name: 'INV-1001' }).click();

    await page.getByRole('button', { name: 'Record payment' }).click();
    const modal = page.getByRole('dialog', { name: 'Record payment' });
    await expect(modal).toBeVisible();

    // First attempt: the demo records the payment but drops the response (503).
    await modal.getByRole('button', { name: 'Record payment' }).click();
    await expect(modal.getByText(/timed out/i)).toBeVisible();

    // Retrying keeps the same Idempotency-Key, so the server replays instead of charging again.
    await modal.getByRole('button', { name: 'Record payment' }).click();
    await expect(modal).toBeHidden();
    await expect(banner(page).getByText(/Payment replayed/)).toBeVisible();
    await expect(page.getByText('PAID', { exact: true }).first()).toBeVisible();
  });
});
