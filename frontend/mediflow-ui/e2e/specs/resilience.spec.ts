import { expect, signIn, test } from '../fixtures/test';

test.describe('loading, empty and error states', () => {
  test('shows a loading state until the doctor directory responds', async ({ page, mock }) => {
    await signIn(page, 'PATIENT');
    mock.delay('GET', '/api/v1/doctors', 1_500);

    await page.goto('/doctors');

    await expect(page.getByText('Loading…')).toBeVisible();
    await expect(page.getByRole('row', { name: /Dr\. Dana Doctor/ })).toBeVisible();
    await expect(page.getByText('Loading…')).toBeHidden();
  });

  test('shows the appointment empty state when there are no visits', async ({ page, mock }) => {
    await signIn(page, 'PATIENT');
    mock.emptyPaths.add('/api/v1/appointments');

    await page.goto('/appointments');

    await expect(page.getByText('No appointments match the current filters.')).toBeVisible();
  });

  test('shows a server error without breaking the shell', async ({ page, mock }) => {
    await signIn(page, 'PATIENT');
    mock.fail('GET', '/api/v1/invoices', 500, 'Billing is temporarily unavailable.');

    await page.goto('/billing');

    await expect(page.getByText('Billing is temporarily unavailable.')).toBeVisible();
    await expect(page.getByRole('navigation', { name: 'Primary' })).toBeVisible();
  });

  test('surfaces an appointment transition conflict and refreshes the list', async ({ page, mock }) => {
    await signIn(page, 'DOCTOR');
    mock.fail(
      'PATCH',
      '/api/v1/appointments/appt-0001/confirm',
      409,
      'The appointment was already confirmed by another user',
    );

    // A blocking alert is raised for a conflict; capture it without blocking the click.
    const messages: string[] = [];
    page.on('dialog', async (dialog) => {
      messages.push(dialog.message());
      await dialog.accept();
    });

    await page.goto('/appointments');
    await page
      .getByRole('row', { name: /Annual checkup/ })
      .getByRole('button', { name: 'Confirm' })
      .click();

    await expect.poll(() => messages.join(' | ')).toContain('The appointment may have changed');

    // The guard still works after the failed transition: the row is reloaded from the server.
    await expect(page.getByRole('row', { name: /Annual checkup/ })).toBeVisible();
  });

  test('keeps the payment modal open with the server error so the user can retry', async ({
    page,
    mock,
  }) => {
    await signIn(page, 'BILLING_STAFF');
    mock.fail(
      'POST',
      '/api/v1/invoices/inv-0001/payments',
      409,
      'This payment was already recorded',
    );

    await page.goto('/billing/inv-0001');
    await page.getByRole('button', { name: 'Record payment' }).click();
    const dialog = page.getByRole('dialog', { name: 'Record payment' });
    await dialog.getByRole('button', { name: 'Record payment' }).click();

    await expect(dialog.getByText('This payment was already recorded')).toBeVisible();
    await expect(dialog).toBeVisible();
    // Closing is still possible, and the retry button is enabled again.
    await dialog.getByRole('button', { name: 'Cancel' }).click();
    await expect(dialog).toBeHidden();
  });
});
