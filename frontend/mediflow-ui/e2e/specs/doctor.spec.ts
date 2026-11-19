import { acceptDialogs, appointmentRow, expect, SEED_USERS, signIn, test } from '../fixtures/test';
import { SECOND_DOCTOR_ID } from '../fixtures/mock-api';

test.describe('doctor workflows', () => {
  test.beforeEach(async ({ page }) => {
    await signIn(page, 'DOCTOR');
  });

  test('shows the clinician dashboard with today\'s schedule', async ({ page }) => {
    await expect(page.getByText('Clinician workspace')).toBeVisible();
    await expect(page.getByRole('heading', { name: "Today's schedule" })).toBeVisible();
    await expect(page.getByText('Annual checkup').first()).toBeVisible();
    await expect(page.getByText('Follow-up').first()).toBeVisible();
    await expect(page.getByRole('link', { name: 'My availability' })).toBeVisible();
  });

  test('adds a working-hours window to its own schedule', async ({ page, mock }) => {
    await page.goto(`/doctors/${SEED_USERS.DOCTOR.id}`);
    await page.getByRole('button', { name: 'Add availability' }).click();

    const dialog = page.getByRole('dialog', { name: 'Add availability' });
    await dialog.getByLabel(/^Day of week/).selectOption('TUESDAY');
    await dialog.getByLabel(/^Start time/).fill('08:00');
    await dialog.getByLabel(/^End time/).fill('12:00');
    await dialog.getByRole('button', { name: 'Add slot' }).click();

    await expect(dialog).toBeHidden();
    await expect(page.getByText('TUESDAY')).toBeVisible();
    const request = mock.lastRequest('POST', `/api/v1/doctors/${SEED_USERS.DOCTOR.id}/availability`);
    expect(request?.body?.dayOfWeek).toBe('TUESDAY');
    expect(request?.body?.startTime).toBe('08:00:00');
    expect(request?.body?.endTime).toBe('12:00:00');
  });

  test('deactivates a working-hours window', async ({ page, mock }) => {
    acceptDialogs(page);
    await page.goto(`/doctors/${SEED_USERS.DOCTOR.id}`);

    await expect(page.getByText('MONDAY')).toBeVisible();
    await page.getByRole('button', { name: 'Deactivate' }).first().click();

    await expect(page.getByText('MONDAY')).toBeHidden();
    await expect(page.getByText('WEDNESDAY')).toBeVisible();
    expect(mock.requestsFor('DELETE', `/api/v1/doctors/${SEED_USERS.DOCTOR.id}/availability*`)).toHaveLength(1);
  });

  test('adds leave and cancels it again', async ({ page, mock }) => {
    acceptDialogs(page);
    await page.goto(`/doctors/${SEED_USERS.DOCTOR.id}`);

    await page.getByRole('button', { name: 'Add leave' }).click();
    const addDialog = page.getByRole('dialog', { name: 'Add leave' });
    await addDialog.getByLabel(/^Start date/).fill('2030-06-03');
    await addDialog.getByLabel(/^End date/).fill('2030-06-05');
    await addDialog.getByLabel(/^Reason/).fill('Vacation');
    await addDialog.getByRole('button', { name: 'Add leave', exact: true }).click();

    await expect(addDialog).toBeHidden();
    await expect(page.getByText('Vacation')).toBeVisible();
    const created = mock.lastRequest('POST', `/api/v1/doctors/${SEED_USERS.DOCTOR.id}/leaves`);
    expect(created?.body?.startDate).toBe('2030-06-03');
    expect(created?.body?.reason).toBe('Vacation');

    await page.getByRole('button', { name: 'Cancel leave' }).click();
    await expect(page.getByText('Vacation')).toBeHidden();
    expect(mock.requestsFor('DELETE', `/api/v1/doctors/${SEED_USERS.DOCTOR.id}/leaves*`)).toHaveLength(1);
  });

  test('drives an appointment through confirm, start and complete', async ({ page, mock }) => {
    acceptDialogs(page);
    await page.goto('/appointments');
    const row = appointmentRow(page, 'Annual checkup');

    await row.getByRole('button', { name: 'Confirm' }).click();
    await expect(appointmentRow(page, 'Annual checkup').getByText('CONFIRMED')).toBeVisible();

    await appointmentRow(page, 'Annual checkup').getByRole('button', { name: 'Start' }).click();
    await expect(appointmentRow(page, 'Annual checkup').getByText('IN PROGRESS')).toBeVisible();

    await appointmentRow(page, 'Annual checkup').getByRole('button', { name: 'Complete' }).click();
    await expect(appointmentRow(page, 'Annual checkup').getByText('COMPLETED')).toBeVisible();

    expect(mock.requestsFor('PATCH', '/api/v1/appointments/appt-0001/confirm')).toHaveLength(1);
    expect(mock.requestsFor('PATCH', '/api/v1/appointments/appt-0001/start')).toHaveLength(1);
    expect(mock.requestsFor('PATCH', '/api/v1/appointments/appt-0001/complete')).toHaveLength(1);
    await expect(appointmentRow(page, 'Annual checkup').getByRole('button', { name: 'Complete' })).toBeHidden();
  });

  test('creates a medical record', async ({ page, mock }) => {
    await page.goto('/medical-records');
    await page.getByRole('button', { name: 'New record' }).click();

    const dialog = page.getByRole('dialog', { name: 'New medical record' });
    await dialog.getByLabel(/^Chief complaint/).fill('Knee pain');
    await dialog.getByLabel(/^Diagnosis/).fill('Ligament sprain');
    await dialog.getByLabel(/^Notes/).fill('Rest and ice for five days.');
    await dialog.getByRole('button', { name: 'Create record' }).click();

    await expect(dialog).toBeHidden();
    await expect(page.getByText('Knee pain')).toBeVisible();
    const request = mock.lastRequest('POST', '/api/v1/medical-records');
    expect(request?.body?.chiefComplaint).toBe('Knee pain');
    expect(request?.body?.doctorId).toBe(SEED_USERS.DOCTOR.id);
  });

  test('updates an existing medical record', async ({ page, mock }) => {
    await page.goto('/medical-records');
    await page.getByRole('row', { name: /Chest tightness/ }).getByRole('button', { name: 'Edit' }).click();

    const dialog = page.getByRole('dialog', { name: 'Edit medical record' });
    await dialog.getByLabel(/^Chief complaint/).fill('Chest tightness resolved');
    await dialog.getByLabel(/^Notes/).fill('Symptoms resolved after treatment.');
    await dialog.getByRole('button', { name: 'Save changes' }).click();

    await expect(dialog).toBeHidden();
    await expect(page.getByText('Chest tightness resolved')).toBeVisible();
    const request = mock.lastRequest('PUT', '/api/v1/medical-records/rec-0001');
    expect(request?.body?.chiefComplaint).toBe('Chest tightness resolved');
  });

  test('respects ownership and role boundaries', async ({ page }) => {
    await page.goto(`/doctors/${SECOND_DOCTOR_ID}`);
    await expect(page.getByRole('heading', { name: 'Dr. Grace Hopper' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Add availability' })).toBeHidden();
    await expect(page.getByRole('button', { name: 'Add leave' })).toBeHidden();

    await page.goto('/billing');
    await expect(page.getByRole('heading', { name: /Keep revenue/ })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Create invoice' })).toBeHidden();
  });
});
