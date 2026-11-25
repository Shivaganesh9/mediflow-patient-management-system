import { expect, SEED_USERS, signIn, test } from '../fixtures/test';

test.describe('admin and staff workflows', () => {
  test('admin dashboard shows operational metrics', async ({ page, mock }) => {
    await signIn(page, 'ADMIN');

    await expect(page.getByText('Administration')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Welcome back, Avery.' })).toBeVisible();
    await expect(page.getByText('Active patients')).toBeVisible();
    await expect(page.getByText('Collected revenue')).toBeVisible();
    await expect(page.getByText('Outstanding')).toBeVisible();
    expect(mock.requestsFor('GET', '/api/v1/analytics/dashboard/summary').length).toBeGreaterThanOrEqual(1);
  });

  test('admin analytics renders every projection', async ({ page, mock }) => {
    await signIn(page, 'ADMIN');
    await page.goto('/analytics');

    await expect(page.getByRole('heading', { name: /See the health of your clinic/ })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Appointment status breakdown' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Daily appointment trend' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Monthly revenue' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Doctors by appointment volume' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Most active patients' })).toBeVisible();
    await expect(page.locator('.metric-card', { hasText: 'Appointments' }).first()).toContainText('12');

    expect(mock.requestsFor('GET', '/api/v1/analytics/dashboard/summary').length).toBeGreaterThanOrEqual(1);
    expect(mock.requestsFor('GET', '/api/v1/analytics/appointments').length).toBeGreaterThanOrEqual(1);
    expect(mock.requestsFor('GET', '/api/v1/analytics/revenue').length).toBeGreaterThanOrEqual(1);
    expect(mock.requestsFor('GET', '/api/v1/analytics/doctors').length).toBeGreaterThanOrEqual(1);
    expect(mock.requestsFor('GET', '/api/v1/analytics/patients').length).toBeGreaterThanOrEqual(1);
  });

  test('admin creates a patient and lands on the new profile', async ({ page, mock }) => {
    await signIn(page, 'ADMIN');
    await page.goto('/patients');
    await page.getByRole('button', { name: 'Add patient' }).click();

    const dialog = page.getByRole('dialog', { name: 'Add patient' });
    await dialog.getByLabel(/^First name/).fill('Cara');
    await dialog.getByLabel(/^Last name/).fill('Client');
    await dialog.getByLabel(/^Email/).fill('cara.client@mediflow.local');
    await dialog.getByLabel(/^Phone/).fill('555-0199');
    await dialog.getByRole('button', { name: 'Create patient' }).click();

    await expect(page.getByRole('heading', { name: 'Cara Client' })).toBeVisible();
    const request = mock.lastRequest('POST', '/api/v1/patients');
    expect(request?.body?.firstName).toBe('Cara');
    expect(request?.body?.email).toBe('cara.client@mediflow.local');
  });

  test('billing staff creates an invoice from the billing workspace', async ({ page, mock }) => {
    await signIn(page, 'BILLING_STAFF');

    await page.goto('/billing');
    await page.getByRole('button', { name: 'Create invoice' }).click();

    const dialog = page.getByRole('dialog', { name: 'Create invoice' });
    await dialog.getByLabel(/^Patient/).selectOption(SEED_USERS.PATIENT.id);
    await dialog.getByPlaceholder('Description *').fill('Cardiology consultation');
    await dialog.getByPlaceholder('Unit price').fill('150');
    await dialog.getByRole('button', { name: 'Create invoice' }).click();

    await expect(page.getByRole('heading', { name: 'INV-1003' })).toBeVisible();
    const request = mock.lastRequest('POST', '/api/v1/invoices');
    expect(request?.body?.patientId).toBe(SEED_USERS.PATIENT.id);
    expect(request?.body?.currency).toBe('USD');
    expect((request?.body?.items as Array<Record<string, unknown>>)[0]?.unitPrice).toBe(150);
  });

  test('billing staff retries a failed payment with the same idempotency key', async ({
    page,
    mock,
  }) => {
    await signIn(page, 'BILLING_STAFF');
    mock.failOnce(
      'POST',
      '/api/v1/invoices/inv-0001/payments',
      503,
      'Payment service is temporarily unavailable',
    );

    await page.goto('/billing/inv-0001');
    await expect(page.getByRole('heading', { name: 'INV-1001' })).toBeVisible();
    await page.getByRole('button', { name: 'Record payment' }).click();

    const dialog = page.getByRole('dialog', { name: 'Record payment' });
    await expect(dialog.getByLabel(/^Amount/)).toHaveValue('150');
    await dialog.getByRole('button', { name: 'Record payment' }).click();
    await expect(dialog.getByText('Payment service is temporarily unavailable')).toBeVisible();

    // Retrying reuses the same key, so the server can replay instead of charging twice.
    await dialog.getByRole('button', { name: 'Record payment' }).click();

    await expect(dialog).toBeHidden();
    await expect(page.getByText('PAID', { exact: true })).toBeVisible();
    const payments = mock.requestsFor('POST', '/api/v1/invoices/inv-0001/payments');
    expect(payments).toHaveLength(2);
    expect(payments[0].headers['idempotency-key']).toMatch(/^[A-Za-z0-9._:-]{8,80}$/);
    expect(payments[1].headers['idempotency-key']).toBe(payments[0].headers['idempotency-key']);
    expect(payments[1].body?.amount).toBe(150);
  });

  test('support staff cannot open clinical medical records', async ({ page }) => {
    await signIn(page, 'SUPPORT');

    await page.goto('/medical-records');

    await expect(page.getByRole('heading', { name: 'Access denied' })).toBeVisible();
    await expect(page.getByRole('navigation', { name: 'Primary' }).getByRole('link', { name: 'Medical Records' })).toBeHidden();
  });

  test('support staff see appointments read-only', async ({ page }) => {
    await signIn(page, 'SUPPORT');

    await page.goto('/appointments');

    await expect(page.getByRole('heading', { name: /Coordinate every visit/ })).toBeVisible();
    // SUPPORT may look appointments up but must not create, reschedule, confirm or cancel one.
    await expect(page.getByRole('button', { name: 'Schedule visit' })).toBeHidden();
    await expect(page.getByRole('button', { name: 'Confirm' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Reschedule' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Cancel' })).toHaveCount(0);
    // The audit trail stays available so support can answer "what happened?" questions.
    await expect(page.getByRole('button', { name: 'History' }).first()).toBeVisible();
  });

  test('billing staff cannot open analytics', async ({ page }) => {
    await signIn(page, 'BILLING_STAFF');

    await page.goto('/analytics');

    await expect(page.getByRole('heading', { name: 'Access denied' })).toBeVisible();
    await expect(page.getByRole('navigation', { name: 'Primary' }).getByRole('link', { name: 'Analytics' })).toBeHidden();
  });

  test('nurse can review patients but cannot add clinicians', async ({ page }) => {
    await signIn(page, 'NURSE');

    await expect(page.getByRole('link', { name: 'Patients' })).toBeVisible();
    await page.goto('/doctors');
    await expect(page.getByRole('heading', { name: /Support clinical staff/ })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Add doctor' })).toBeHidden();
  });
});
