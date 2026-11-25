import { expect, seedStoredSession, signIn, test } from '../fixtures/test';

/**
 * Correlation ids are an observability aid, not a credential. These tests pin the browser side of
 * the contract: one safe id per logical request, attached to public and authenticated calls, reused
 * by a refresh + retry, and read back from the server so an error can quote it.
 */
const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function correlationIdOf(request: { headers: Record<string, string> } | undefined): string | undefined {
  return request?.headers['x-correlation-id'];
}

test.describe('request correlation', () => {
  test('attaches a safe id to sign-in and authenticated requests', async ({ page, mock }) => {
    await signIn(page, 'PATIENT');

    const loginId = correlationIdOf(mock.lastRequest('POST', '/api/v1/auth/login'));
    expect(loginId).toMatch(UUID_V4);

    const meId = correlationIdOf(mock.lastRequest('GET', '/api/v1/auth/me'));
    expect(meId).toMatch(UUID_V4);
    // Each logical request gets its own id; ids are not a global browser constant.
    expect(meId).not.toBe(loginId);
  });

  test('attaches an id to public registration', async ({ page, mock }) => {
    await page.goto('/login');

    // Registration has no UI surface yet; exercise the shared client so the public-endpoint path
    // is still covered. The Vite dev server serves the same module the app uses.
    await page.evaluate(async () => {
      const modulePath = '/src/api/client.ts';
      const client = (await import(/* @vite-ignore */ modulePath)) as {
        api: (path: string, options: Record<string, unknown>) => Promise<unknown>;
      };
      await client.api('/api/v1/auth/register', {
        method: 'POST',
        auth: false,
        body: {
          username: 'correlation.patient',
          email: 'correlation.patient@example.com',
          password: 'Correlation1!',
          fullName: 'Correlation Patient',
        },
      });
    });

    const registerId = correlationIdOf(mock.lastRequest('POST', '/api/v1/auth/register'));
    expect(registerId).toMatch(UUID_V4);
  });

  test('reuses one id across refresh and the retried request', async ({ page, mock }) => {
    const session = mock.issueSession('PATIENT');
    await seedStoredSession(page, {
      accessToken: session.accessToken,
      refreshToken: session.refreshToken,
      // Still valid, so only the 401 retry path refreshes (not the proactive check).
      expiresAt: Date.now() + 10 * 60_000,
    });
    mock.failOnce('GET', '/api/v1/auth/me', 401, 'Access token rejected');

    await page.goto('/');

    await expect(page.getByRole('heading', { name: 'Welcome back, Ada.' })).toBeVisible();

    // React StrictMode issues the bootstrap /me twice; the heading can appear from the call that
    // did not fail while the 401'd call is still refreshing. Wait for its retry to be recorded.
    await expect
      .poll(() => {
        const refreshIndex = mock.requests.findIndex(
          (request) => request.method === 'POST' && request.path === '/api/v1/auth/refresh',
        );
        return refreshIndex >= 0 && mock.requests
          .slice(refreshIndex + 1)
          .some((request) => request.method === 'GET' && request.path === '/api/v1/auth/me');
      })
      .toBe(true);

    // Walk the ordered request log: the 401'd call, the refresh and the retry must carry one id.
    const log = mock.requests.map((request) => ({
      method: request.method,
      path: request.path,
      id: correlationIdOf(request),
    }));
    const refreshIndex = log.findIndex(
      (request) => request.method === 'POST' && request.path === '/api/v1/auth/refresh',
    );
    expect(refreshIndex).toBeGreaterThan(0);

    const refreshId = log[refreshIndex].id;
    expect(refreshId).toMatch(UUID_V4);

    const meBefore = log
      .slice(0, refreshIndex)
      .filter((request) => request.path === '/api/v1/auth/me')
      .map((request) => request.id);
    const meAfter = log
      .slice(refreshIndex + 1)
      .filter((request) => request.path === '/api/v1/auth/me')
      .map((request) => request.id);

    expect(meBefore.length).toBeGreaterThanOrEqual(1);
    expect(meAfter.length).toBeGreaterThanOrEqual(1);
    // The rejected request and its retry both reused the refresh id of the same logical call.
    expect(meBefore).toContain(refreshId);
    expect(meAfter).toContain(refreshId);
  });

  test('shows the server-returned id as a reference on server faults', async ({ page, mock }) => {
    await signIn(page, 'PATIENT');
    // Model a gateway that replaces the caller's value; the client must trust the response.
    mock.correlationIdOverride = 'gateway-issued-0001';
    mock.fail('GET', '/api/v1/invoices', 500, 'Billing is temporarily unavailable.');

    await page.goto('/billing');

    await expect(
      page.getByText('Billing is temporarily unavailable. (reference: gateway-issued-0001)'),
    ).toBeVisible();

    const sentId = correlationIdOf(mock.lastRequest('GET', '/api/v1/invoices'));
    expect(sentId).toMatch(UUID_V4);
    expect(sentId).not.toBe('gateway-issued-0001');
  });

  test('attaches an id to sign-out', async ({ page, mock }) => {
    await signIn(page, 'PATIENT');

    await page.getByRole('button', { name: 'Sign out' }).click();

    await expect(page).toHaveURL(/\/login$/);
    const logoutId = correlationIdOf(mock.lastRequest('POST', '/api/v1/auth/logout'));
    expect(logoutId).toMatch(UUID_V4);
  });
});
