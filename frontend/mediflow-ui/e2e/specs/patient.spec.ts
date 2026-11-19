import {
  acceptDialogs,
  appointmentRow,
  expect,
  nextWeekday,
  SEED_USERS,
  signIn,
  test,
} from '../fixtures/test';

test.describe('patient workflows', () => {
  test.beforeEach(async ({ page }) => {
    await signIn(page, 'PATIENT');
  });

  test('searches doctors and inspects details, availability and leave', async ({ page }) => {
    await page.getByRole('navigation', { name: 'Primary' }).getByRole('link', { name: 'Doctors' }).click();

    await page.getByPlaceholder(/Search by name or specialty/).fill('Cardio');

    const dana = page.getByRole('row', { name: /Dr\. Dana Doctor/ });
    await expect(dana).toBeVisible();
    await expect(page.getByRole('row', { name: /Grace Hopper/ })).toBeHidden();

    await dana.click();
    await expect(page.getByRole('heading', { name: 'Dr. Dana Doctor' })).toBeVisible();
    await expect(page.getByText('MONDAY')).toBeVisible();
    await expect(page.getByText('09:00', { exact: false }).first()).toBeVisible();
    // A patient is read-only on another clinician's schedule.
    await expect(page.getByRole('button', { name: 'Add availability' })).toBeHidden();

    await page.goto('/doctors');
    await page.getByRole('row', { name: /Dr\. Grace Hopper/ }).click();
    await expect(page.getByText('Conference')).toBeVisible();
  });

  test('books an appointment inside the advertised working hours', async ({ page, mock }) => {
    const monday = nextWeekday('MONDAY');
    await page.getByRole('navigation', { name: 'Primary' }).getByRole('link', { name: 'Appointments' }).click();
    await page.getByRole('button', { name: 'Schedule visit' }).click();

    const dialog = page.getByRole('dialog', { name: 'Schedule visit' });
    await dialog.getByLabel(/^Doctor/).selectOption(SEED_USERS.DOCTOR.id);
    await dialog.getByLabel(/^Start/).fill(`${monday}T10:00`);
    await dialog.getByLabel(/^End/).fill(`${monday}T10:30`);
    await dialog.getByLabel(/^Reason/).fill('Routine physical');

    await expect(dialog.getByText(/within the doctor.s working hours/i)).toBeVisible();

    await dialog.getByRole('button', { name: 'Schedule', exact: true }).click();

    await expect(dialog).toBeHidden();
    await expect(appointmentRow(page, 'Routine physical')).toBeVisible();

    const request = mock.lastRequest('POST', '/api/v1/appointments');
    expect(request?.body?.patientId).toBe(SEED_USERS.PATIENT.id);
    expect(request?.body?.doctorId).toBe(SEED_USERS.DOCTOR.id);
    expect(request?.body?.startTime).toBe(`${monday}T10:00:00`);
    expect(request?.body?.endTime).toBe(`${monday}T10:30:00`);
  });

  test('rejects a booking outside working hours with an actionable message', async ({
    page,
    mock,
  }) => {
    const monday = nextWeekday('MONDAY');
    mock.appointmentConflict = 'The requested slot is outside the doctor’s working hours';

    await page.goto('/appointments');
    await page.getByRole('button', { name: 'Schedule visit' }).click();
    const dialog = page.getByRole('dialog', { name: 'Schedule visit' });
    await dialog.getByLabel(/^Doctor/).selectOption(SEED_USERS.DOCTOR.id);
    await dialog.getByLabel(/^Start/).fill(`${monday}T18:00`);
    await dialog.getByLabel(/^End/).fill(`${monday}T18:30`);
    await dialog.getByLabel(/^Reason/).fill('Late consult');
    await dialog.getByRole('button', { name: 'Schedule', exact: true }).click();

    await expect(dialog.getByText(/Please choose a different time slot/)).toBeVisible();
    await expect(dialog).toBeVisible();
  });

  test('views appointment history', async ({ page, mock }) => {
    await page.getByRole('navigation', { name: 'Primary' }).getByRole('link', { name: 'Appointments' }).click();
    const row = appointmentRow(page, 'Annual checkup');
    await row.getByRole('button', { name: 'History' }).click();

    const dialog = page.getByRole('dialog', { name: /History/ });
    await expect(dialog).toBeVisible();
    await expect(dialog.getByText('CREATED')).toBeVisible();
    await expect(dialog.getByText('SCHEDULED')).toBeVisible();
    expect(mock.requestsFor('GET', `/api/v1/appointments/appt-0001/history`).length).toBeGreaterThanOrEqual(1);
  });

  test('reschedules an appointment and sends the new slot', async ({ page, mock }) => {
    const wednesday = nextWeekday('WEDNESDAY');
    await page.goto('/appointments');
    const row = appointmentRow(page, 'Annual checkup');
    await row.getByRole('button', { name: 'Reschedule' }).click();

    const dialog = page.getByRole('dialog', { name: 'Reschedule appointment' });
    await dialog.getByLabel(/^Start/).fill(`${wednesday}T13:00`);
    await dialog.getByLabel(/^End/).fill(`${wednesday}T13:30`);
    await dialog.getByRole('button', { name: 'Reschedule', exact: true }).click();

    await expect(dialog).toBeHidden();
    const request = mock.lastRequest('PUT', '/api/v1/appointments/appt-0001');
    expect(request?.body?.startTime).toBe(`${wednesday}T13:00:00`);
    expect(request?.body?.endTime).toBe(`${wednesday}T13:30:00`);
  });

  test('cancels an appointment after confirmation and reflects the new status', async ({
    page,
    mock,
  }) => {
    acceptDialogs(page);
    await page.goto('/appointments');
    const row = appointmentRow(page, 'Annual checkup');
    await row.getByRole('button', { name: 'Cancel', exact: true }).click();

    await expect(appointmentRow(page, 'Annual checkup').getByText('CANCELLED')).toBeVisible();
    expect(mock.requestsFor('PATCH', '/api/v1/appointments/appt-0001/cancel')).toHaveLength(1);
  });

  test('reads medical records and their field-level audit trail', async ({ page, mock }) => {
    await page.getByRole('navigation', { name: 'Primary' }).getByRole('link', { name: 'Medical Records' }).click();

    await expect(page.getByText('Chest tightness')).toBeVisible();
    // Patients can read records but never create or edit them.
    await expect(page.getByRole('button', { name: 'New record' })).toBeHidden();

    await page.getByRole('button', { name: 'History' }).click();
    const dialog = page.getByRole('dialog', { name: /Audit history/ });
    await expect(dialog.getByText('CREATED')).toBeVisible();
    await expect(dialog.getByText('chiefComplaint, notes')).toBeVisible();
    expect(mock.requestsFor('GET', '/api/v1/medical-records/rec-0001/history').length).toBeGreaterThanOrEqual(1);
  });

  test('reviews invoices and payment history without payment controls', async ({ page }) => {
    await page.getByRole('navigation', { name: 'Primary' }).getByRole('link', { name: 'Billing' }).click();

    await expect(page.getByRole('row', { name: /INV-1001/ })).toBeVisible();
    await page.getByRole('row', { name: /INV-1001/ }).click();

    await expect(page.getByRole('heading', { name: 'INV-1001' })).toBeVisible();
    await expect(page.getByText('No payments recorded yet.')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Record payment' })).toBeHidden();

    await page.goto('/billing');
    await page.getByRole('row', { name: /INV-1002/ }).click();
    await expect(page.getByText('CARD', { exact: false })).toBeVisible();
    await expect(page.getByText('COMPLETED')).toBeVisible();
    await expect(page.getByText('Record payment')).toBeHidden();
  });

  test('sees unread notifications and marks one as read', async ({ page, mock }) => {
    await page.getByRole('navigation', { name: 'Primary' }).getByRole('link', { name: 'Notifications' }).click();

    await expect(page.getByText('2 unread')).toBeVisible();
    await expect(page.getByText('Appointment booked')).toBeVisible();

    await page.getByRole('button', { name: 'Mark read' }).first().click();

    await expect(page.getByText('1 unread')).toBeVisible();
    expect(mock.requestsFor('PATCH', '/api/v1/notifications/ntf-0001/read')).toHaveLength(1);
  });

  test('cannot reach admin-only analytics', async ({ page }) => {
    await page.goto('/analytics');

    await expect(page.getByRole('heading', { name: 'Access denied' })).toBeVisible();
    await expect(page.getByText('Your role does not permit access to this page.')).toBeVisible();
  });

  test('does not expose staff and analytics navigation', async ({ page }) => {
    const navigation = page.getByRole('navigation', { name: 'Primary' });
    await expect(navigation.getByRole('link', { name: 'Analytics' })).toBeHidden();
    await expect(navigation.getByRole('link', { name: 'Patients' })).toBeHidden();
  });
});
