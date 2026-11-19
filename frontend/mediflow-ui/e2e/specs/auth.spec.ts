import { expect, seedStoredSession, signIn, test } from '../fixtures/test';

test.describe('authentication and sessions', () => {
  test('patient signs in and lands on the patient portal', async ({ page, mock }) => {
    await signIn(page, 'PATIENT');

    await expect(page.getByText('Patient portal')).toBeVisible();
    await expect(page.getByRole('link', { name: 'Book a visit' })).toBeVisible();
    await expect(page.getByText('Ada Patient', { exact: false })).toBeVisible();
    expect(mock.requestsFor('POST', '/api/v1/auth/login').length).toBeGreaterThanOrEqual(1);
  });

  test('invalid credentials show an actionable error and keep the user on the login page', async ({
    page,
  }) => {
    await page.goto('/login');
    await page.getByLabel('Username or email').fill('patient');
    await page.getByLabel('Password').fill('not-the-password');
    await page.getByRole('button', { name: /^Sign in$/ }).click();

    await expect(page.getByRole('alert')).toContainText('Invalid username or password');
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByRole('heading', { name: /Welcome back/ })).toBeHidden();
  });

  test('signing out clears the session and explains why on the login page', async ({ page }) => {
    await signIn(page, 'PATIENT');

    await page.getByRole('button', { name: 'Sign out' }).click();

    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByRole('status')).toContainText('You have been signed out.');
    const storedAccessToken = await page.evaluate(() => localStorage.getItem('mediflow.accessToken'));
    expect(storedAccessToken).toBeNull();
  });

  test('an expired access token is refreshed transparently without a new sign-in', async ({
    page,
    mock,
  }) => {
    const session = mock.issueSession('PATIENT');
    await seedStoredSession(page, {
      accessToken: session.accessToken,
      refreshToken: session.refreshToken,
      expiresAt: Date.now() - 60_000,
    });

    await page.goto('/');

    await expect(page.getByRole('heading', { name: 'Welcome back, Ada.' })).toBeVisible();
    expect(mock.requestsFor('POST', '/api/v1/auth/refresh').length).toBeGreaterThanOrEqual(1);
    expect(mock.requestsFor('GET', '/api/v1/auth/me').length).toBeGreaterThanOrEqual(1);
    const refreshed = await page.evaluate(() => localStorage.getItem('mediflow.accessToken'));
    expect(refreshed).not.toBe(session.accessToken);
  });

  test('a revoked refresh token ends the session with an expiry notice', async ({ page, mock }) => {
    const session = mock.issueRevokedSession('PATIENT');
    await seedStoredSession(page, {
      accessToken: session.accessToken,
      refreshToken: session.refreshToken,
      expiresAt: Date.now() - 60_000,
    });

    await page.goto('/');

    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByRole('status')).toContainText('Your session has expired.');
    expect(mock.requestsFor('POST', '/api/v1/auth/refresh').length).toBeGreaterThanOrEqual(1);
  });

  test('an anonymous deep link is redirected to the login page', async ({ page, mock }) => {
    await page.goto('/appointments');

    await expect(page).toHaveURL(/\/login$/);
    expect(mock.requestsFor('GET', '/api/v1/appointments')).toHaveLength(0);
  });
});
