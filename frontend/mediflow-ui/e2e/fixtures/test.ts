import { expect, Page, test as base } from '@playwright/test';
import { MockApi, Role, SEED_USERS } from './mock-api';

export { expect, SEED_USERS };
export type { Role };

interface MockFixtures {
  /** Fresh, per-test API double. Installed before the test body runs. */
  mock: MockApi;
  /**
   * Auto fixture that only depends on {@link MockApi}, so the API double is installed before any
   * `beforeEach` hook runs. Without it a hook that signs in would talk to the real dev server,
   * because Playwright sets up fixtures lazily (only when a hook or test asks for them).
   */
  mockInstalled: void;
}

export const test = base.extend<MockFixtures>({
  mock: async ({ page }, use) => {
    const mock = new MockApi();
    await mock.install(page);
    await use(mock);
  },
  mockInstalled: [
    async ({ mock }, use) => {
      void mock;
      await use();
    },
    { auto: true },
  ],
});

/** Sign in through the real login form and wait for the role dashboard. */
export async function signIn(page: Page, role: Role): Promise<void> {
  const user = SEED_USERS[role];
  await page.goto('/login');
  await page.getByLabel('Username or email').fill(user.username);
  await page.getByLabel('Password').fill(user.password);
  await page.getByRole('button', { name: /^Sign in$/ }).click();
  await expect(page.getByRole('heading', { name: /Welcome back/ })).toBeVisible();
}

/** Seed localStorage with an already-known session before the app boots. */
export async function seedStoredSession(
  page: Page,
  session: { accessToken: string; refreshToken: string; expiresAt?: number },
): Promise<void> {
  await page.addInitScript(
    (value) => {
      localStorage.setItem('mediflow.accessToken', value.accessToken);
      localStorage.setItem('mediflow.refreshToken', value.refreshToken);
      if (value.expiresAt) {
        localStorage.setItem('mediflow.accessExpiresAt', String(value.expiresAt));
      }
    },
    session,
  );
}

/** The table row for an appointment, matched by its reason. */
export function appointmentRow(page: Page, reason: string) {
  return page.getByRole('row', { name: new RegExp(reason) });
}

/** Auto-accept window.confirm prompts used by appointment/invoice transitions. */
export function acceptDialogs(page: Page): void {
  page.on('dialog', (dialog) => void dialog.accept());
}

const DAY_INDEX: Record<string, number> = {
  SUNDAY: 0,
  MONDAY: 1,
  TUESDAY: 2,
  WEDNESDAY: 3,
  THURSDAY: 4,
  FRIDAY: 5,
  SATURDAY: 6,
};

/**
 * Returns the next occurrence of a weekday as `YYYY-MM-DD`. Tests that assert working-hours hints
 * must use a date whose weekday is deterministic, never "today".
 */
export function nextWeekday(day: keyof typeof DAY_INDEX): string {
  const now = new Date();
  const delta = ((DAY_INDEX[day] - now.getDay() + 7) % 7) || 7;
  const date = new Date(now.getFullYear(), now.getMonth(), now.getDate() + delta);
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const dayOfMonth = `${date.getDate()}`.padStart(2, '0');
  return `${date.getFullYear()}-${month}-${dayOfMonth}`;
}
