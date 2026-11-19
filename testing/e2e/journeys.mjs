/**
 * Cross-service E2E journeys.
 *
 * These journeys talk to the real services through the API gateway with real PostgreSQL, Redis and
 * Kafka running locally. They complement (without duplicating) the per-service Maven integration
 * tests: the point is the *seams* between services — gateway routing, user-token propagation, the
 * appointment -> doctor-service schedule contract, billing idempotency, Kafka fan-out and shared
 * token revocation.
 *
 * Assertions only rely on documented API contracts and seeded data (seed profile).
 */
import {
  assert,
  assertEqual,
  assertMatch,
  describeResponse,
  expectStatus,
  httpRequest,
  login,
  unwrap,
  waitFor,
} from './lib/http.mjs';
import { fakeServiceToken, userToken } from './lib/jwt.mjs';
import { SEED, USERS } from './lib/seed.mjs';

const pad = (value) => String(value).padStart(2, '0');

/** Returns the next occurrence of a weekday (UTC), always at least one day in the future. */
export function nextWeekdayUtc(dayIndex) {
  const now = new Date();
  const midnight = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));
  const delta = ((dayIndex - midnight.getUTCDay() + 7) % 7) || 7;
  return new Date(midnight.getTime() + delta * 86_400_000);
}

export function localDateTime(date, hour, minute = 0) {
  const y = date.getUTCFullYear();
  const m = pad(date.getUTCMonth() + 1);
  const d = pad(date.getUTCDate());
  return `${y}-${m}-${d}T${pad(hour)}:${pad(minute)}:00`;
}

export function isoDate(date) {
  return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}`;
}

function messageOf(response) {
  return String(response.json?.message ?? '');
}

export async function runJourneys({ gateway, secret, serviceUrls, log = console.log }) {
  const results = [];
  const monday = nextWeekdayUtc(1);
  const mondayDate = isoDate(monday);
  // Clinical notes are dated in the past: `encounterDate` is @PastOrPresent, while appointment
  // slots must be in the future. One day back also survives a run that straddles UTC midnight.
  const encounterDate = isoDate(new Date(Date.now() - 86_400_000));

  const canReach = (serviceName) => Boolean(serviceUrls[serviceName]);

  async function step(name, fn) {
    const started = Date.now();
    try {
      await fn();
      results.push({ name, ok: true, ms: Date.now() - started });
      log(`  PASS  ${name}`);
    } catch (error) {
      results.push({ name, ok: false, ms: Date.now() - started, error });
      log(`  FAIL  ${name}`);
      log(`        ${error.message}`);
    }
  }

  function skip(name, reason) {
    results.push({ name, ok: true, skipped: true });
    log(`  SKIP  ${name} (${reason})`);
  }

  await step('auth: gateway issues and validates user sessions', async () => {
    const patient = await login(gateway, USERS.patient.username, USERS.patient.password);
    const me = await httpRequest(gateway, 'GET', '/api/v1/auth/me', { token: patient.accessToken });
    expectStatus(me, 200, 'GET /auth/me as patient');
    const profile = unwrap(me, 'GET /auth/me as patient');
    assertEqual(profile.username, USERS.patient.username, 'profile username');
    assert(profile.roles.includes('PATIENT'), 'patient profile must carry the PATIENT role');

    const admin = await login(gateway, USERS.admin.username, USERS.admin.password);
    const adminMe = unwrap(
      await httpRequest(gateway, 'GET', '/api/v1/auth/me', { token: admin.accessToken }),
      'GET /auth/me as admin',
    );
    assert(adminMe.roles.includes('ADMIN'), 'admin profile must carry the ADMIN role');

    const badPassword = await httpRequest(gateway, 'POST', '/api/v1/auth/login', {
      body: { usernameOrEmail: USERS.patient.username, password: 'not-the-password' },
    });
    expectStatus(badPassword, 401, 'login with a wrong password');

    const anonymous = await httpRequest(gateway, 'GET', '/api/v1/appointments');
    expectStatus(anonymous, 401, 'appointments without a token');

    const tampered = await httpRequest(gateway, 'GET', '/api/v1/appointments', {
      token: `${patient.accessToken}tampered`,
    });
    expectStatus(tampered, 401, 'appointments with a tampered token');
  });

  // ---------------------------------------------------------------- 2. booking seam
  let booking = null;
  await step('booking: appointment-service enforces doctor-service working hours', async () => {
    const patient = await login(gateway, USERS.patient.username, USERS.patient.password);
    const slotStart = localDateTime(monday, 9, 30);
    const slotEnd = localDateTime(monday, 10, 0);

    const created = await httpRequest(gateway, 'POST', '/api/v1/appointments', {
      token: patient.accessToken,
      body: {
        patientId: USERS.patient.id,
        doctorId: USERS.doctor.id,
        startTime: slotStart,
        endTime: slotEnd,
        reason: 'E2E cross-service booking',
        appointmentType: 'IN_PERSON',
      },
    });
    expectStatus(created, 201, 'book inside working hours');
    booking = unwrap(created, 'book inside working hours');
    assertEqual(booking.status, 'SCHEDULED', 'new appointment status');
    assertEqual(booking.patientId, USERS.patient.id, 'booked patient');
    assertEqual(booking.doctorId, USERS.doctor.id, 'booked doctor');
    assertMatch(booking.startTime, /T09:30:00/, 'booking start time is the requested naive slot');

    // Out of hours: doctor-service rejects the slot, and its reason reaches the caller as a 409.
    const outOfHours = await httpRequest(gateway, 'POST', '/api/v1/appointments', {
      token: patient.accessToken,
      body: {
        patientId: USERS.patient.id,
        doctorId: USERS.doctor.id,
        startTime: localDateTime(monday, 3, 0),
        endTime: localDateTime(monday, 3, 30),
        reason: 'E2E outside working hours',
      },
    });
    expectStatus(outOfHours, 409, 'book outside working hours');
    assertMatch(messageOf(outOfHours), /working hours/i, 'out-of-hours message is actionable');

    // The same slot twice must not create a second appointment.
    const overlap = await httpRequest(gateway, 'POST', '/api/v1/appointments', {
      token: patient.accessToken,
      body: {
        patientId: USERS.patient.id,
        doctorId: USERS.doctor.id,
        startTime: slotStart,
        endTime: slotEnd,
        reason: 'E2E duplicate slot',
      },
    });
    expectStatus(overlap, 409, 'book an already used slot');

    const detail = await httpRequest(gateway, 'GET', `/api/v1/appointments/${booking.appointmentId}`, {
      token: patient.accessToken,
    });
    expectStatus(detail, 200, 'read the booked appointment');
    assertEqual(unwrap(detail, 'read the booked appointment').appointmentId, booking.appointmentId, 'appointment id');

    const history = await httpRequest(gateway, 'GET', `/api/v1/appointments/${booking.appointmentId}/history`, {
      token: patient.accessToken,
    });
    expectStatus(history, 200, 'read appointment history');
    const entries = unwrap(history, 'read appointment history');
    assert(entries.length >= 1, 'appointment history must record the creation');
    assertEqual(entries[0].action, 'CREATED', 'first history action');
  });

  // ---------------------------------------------------------------- 3. clinical lifecycle
  await step('clinical: lifecycle transitions and role boundaries are enforced end to end', async () => {
    const [patient, doctor, billing] = await Promise.all([
      login(gateway, USERS.patient.username, USERS.patient.password),
      login(gateway, USERS.doctor.username, USERS.doctor.password),
      login(gateway, USERS.billing.username, USERS.billing.password),
    ]);

    const created = await httpRequest(gateway, 'POST', '/api/v1/appointments', {
      token: patient.accessToken,
      body: {
        patientId: USERS.patient.id,
        doctorId: USERS.doctor.id,
        startTime: localDateTime(monday, 11, 0),
        endTime: localDateTime(monday, 11, 30),
        reason: 'E2E lifecycle',
      },
    });
    expectStatus(created, 201, 'create lifecycle appointment');
    const appointment = unwrap(created, 'create lifecycle appointment');

    const patientStart = await httpRequest(gateway, 'PATCH', `/api/v1/appointments/${appointment.appointmentId}/start`, {
      token: patient.accessToken,
    });
    expectStatus(patientStart, 403, 'patient starts a visit');

    const billingStart = await httpRequest(gateway, 'PATCH', `/api/v1/appointments/${appointment.appointmentId}/start`, {
      token: billing.accessToken,
    });
    expectStatus(billingStart, 403, 'billing staff starts a visit');

    const confirm = await httpRequest(gateway, 'PATCH', `/api/v1/appointments/${appointment.appointmentId}/confirm`, {
      token: doctor.accessToken,
    });
    expectStatus(confirm, 200, 'doctor confirms the visit');
    assertEqual(unwrap(confirm, 'doctor confirms the visit').status, 'CONFIRMED', 'status after confirm');

    const start = await httpRequest(gateway, 'PATCH', `/api/v1/appointments/${appointment.appointmentId}/start`, {
      token: doctor.accessToken,
    });
    expectStatus(start, 200, 'doctor starts the visit');
    assertEqual(unwrap(start, 'doctor starts the visit').status, 'IN_PROGRESS', 'status after start');

    const complete = await httpRequest(gateway, 'PATCH', `/api/v1/appointments/${appointment.appointmentId}/complete`, {
      token: doctor.accessToken,
    });
    expectStatus(complete, 200, 'doctor completes the visit');
    assertEqual(unwrap(complete, 'doctor completes the visit').status, 'COMPLETED', 'status after complete');

    const otherPatient = await httpRequest(gateway, 'GET', `/api/v1/appointments/${SEED.otherPatientAppointmentId}`, {
      token: patient.accessToken,
    });
    expectStatus(otherPatient, 403, 'patient reads another patient visit');

    const history = unwrap(
      await httpRequest(gateway, 'GET', `/api/v1/appointments/${appointment.appointmentId}/history`, {
        token: doctor.accessToken,
      }),
      'read lifecycle history',
    );
    const actions = history.map((entry) => entry.action);
    assert(actions.includes('CREATED'), 'history records CREATED');
    assert(actions.includes('CONFIRMED'), 'history records CONFIRMED');
    assert(actions.includes('STARTED'), 'history records STARTED');
    assert(actions.includes('COMPLETED'), 'history records COMPLETED');

    // Clinical documentation is owned by medical-record-service.
    const record = unwrap(
      await httpRequest(gateway, 'POST', '/api/v1/medical-records', {
        token: doctor.accessToken,
        body: {
          patientId: USERS.patient.id,
          doctorId: USERS.doctor.id,
          appointmentId: appointment.appointmentId,
          encounterDate,
          recordType: 'CONSULTATION',
          chiefComplaint: 'E2E chest tightness',
          diagnosis: 'Stable angina',
          notes: 'Initial note for the cross-service journey.',
        },
      }),
      'create medical record',
    );
    assertMatch(record.recordId, /[0-9a-f-]{36}/i, 'medical record id');

    const updated = await httpRequest(gateway, 'PUT', `/api/v1/medical-records/${record.recordId}`, {
      token: doctor.accessToken,
      body: {
        chiefComplaint: 'E2E chest tightness resolved',
        diagnosis: 'Stable angina',
        notes: 'Follow-up note for the cross-service journey.',
      },
    });
    expectStatus(updated, 200, 'update medical record');

    const recordHistory = unwrap(
      await httpRequest(gateway, 'GET', `/api/v1/medical-records/${record.recordId}/history`, {
        token: doctor.accessToken,
      }),
      'read medical record history',
    );
    assert(recordHistory.length >= 2, 'medical record history has create and update entries');
    const updateEntry = recordHistory.find((entry) => entry.action === 'UPDATED');
    assert(updateEntry, 'medical record history records UPDATED');
    assertMatch(updateEntry.changedFields, /chiefComplaint/i, 'audit lists changed field names');
    assert(
      !String(updateEntry.changedFields).includes('resolved'),
      'audit must never store clinical values, only field names',
    );

    // Patients can read their own records but never write them.
    const patientWrite = await httpRequest(gateway, 'POST', '/api/v1/medical-records', {
      token: patient.accessToken,
      body: {
        patientId: USERS.patient.id,
        encounterDate,
        recordType: 'CONSULTATION',
        chiefComplaint: 'Should be rejected',
        notes: 'Patients cannot create records.',
      },
    });
    expectStatus(patientWrite, 403, 'patient creates a medical record');
  });

  // ---------------------------------------------------------------- 4. internal endpoint security
  await step('internal: schedule API is not gateway-routed and rejects user credentials', async () => {
    const doctorService = serviceUrls['doctor-service'];
    assert(doctorService, 'doctor-service must be running for this journey');
    const query = `startTime=${encodeURIComponent(localDateTime(monday, 9, 30))}&endTime=${encodeURIComponent(localDateTime(monday, 10, 0))}`;
    const path = `/internal/doctors/${USERS.doctor.id}/schedule/validation?${query}`;

    const viaGateway = await httpRequest(gateway, 'GET', path);
    expectStatus(viaGateway, 404, 'internal endpoint through the gateway');

    const viaGatewayApiPrefix = await httpRequest(gateway, 'GET', `/api/v1${path}`);
    expectStatus(viaGatewayApiPrefix, 404, 'internal endpoint under the API prefix');

    const anonymous = await httpRequest(doctorService, 'GET', path);
    expectStatus(anonymous, 401, 'internal endpoint without a credential');

    const patient = await login(gateway, USERS.patient.username, USERS.patient.password);
    const patientCall = await httpRequest(doctorService, 'GET', path, { token: patient.accessToken });
    expectStatus(patientCall, 403, 'internal endpoint with a patient token');

    const adminToken = userToken(secret, {
      username: USERS.admin.username,
      userId: USERS.admin.id,
      roles: ['ADMIN'],
    });
    const adminCall = await httpRequest(doctorService, 'GET', path, { token: adminToken });
    expectStatus(adminCall, 403, 'internal endpoint with a raw-secret admin token');

    // Same claims as a service credential, but signed with the user-token key: key separation
    // means the derived service key verification fails, so the scope rule still rejects it.
    const forged = fakeServiceToken(secret);
    const forgedCall = await httpRequest(doctorService, 'GET', path, { token: forged });
    expectStatus(forgedCall, 403, 'internal endpoint with a forged service token');

    const malformed = await httpRequest(doctorService, 'GET', path, { token: 'not-a-jwt' });
    expectStatus(malformed, 401, 'internal endpoint with a malformed credential');

    for (const [label, response] of [
      ['anonymous', anonymous],
      ['patient', patientCall],
      ['admin', adminCall],
      ['forged', forgedCall],
    ]) {
      const text = response.text ?? '';
      assert(!text.includes(secret), `${label} rejection must not echo the signing secret`);
      assert(!/Exception|at com\.mediflow/.test(text), `${label} rejection must not leak a stack trace`);
    }
  });

  // ---------------------------------------------------------------- 5. billing idempotency
  await step('billing: invoice per appointment and idempotent payment retries', async () => {
    const [patient, doctor, billing] = await Promise.all([
      login(gateway, USERS.patient.username, USERS.patient.password),
      login(gateway, USERS.doctor.username, USERS.doctor.password),
      login(gateway, USERS.billing.username, USERS.billing.password),
    ]);

    const appointment = unwrap(
      await httpRequest(gateway, 'POST', '/api/v1/appointments', {
        token: patient.accessToken,
        body: {
          patientId: USERS.patient.id,
          doctorId: USERS.doctor.id,
          startTime: localDateTime(monday, 12, 0),
          endTime: localDateTime(monday, 12, 30),
          reason: 'E2E billing',
        },
      }),
      'create appointment for billing',
    );
    for (const action of ['confirm', 'start', 'complete']) {
      expectStatus(
        await httpRequest(gateway, 'PATCH', `/api/v1/appointments/${appointment.appointmentId}/${action}`, {
          token: doctor.accessToken,
        }),
        200,
        `transition ${action} before invoicing`,
      );
    }

    const invoiceBody = {
      patientId: USERS.patient.id,
      appointmentId: appointment.appointmentId,
      currency: 'USD',
      items: [{ itemType: 'CONSULTATION', description: 'Cardiology consultation', quantity: 1, unitPrice: 150 }],
    };
    const created = await httpRequest(gateway, 'POST', '/api/v1/invoices', {
      token: billing.accessToken,
      body: invoiceBody,
    });
    expectStatus(created, 201, 'create invoice');
    const invoice = unwrap(created, 'create invoice');
    assertEqual(Number(invoice.totalAmount), 150, 'invoice total');
    assertEqual(invoice.status, 'PENDING', 'invoice status after creation');

    const duplicate = await httpRequest(gateway, 'POST', '/api/v1/invoices', {
      token: billing.accessToken,
      body: invoiceBody,
    });
    expectStatus(duplicate, 409, 'second active invoice for the same appointment');

    const key = `e2e-${invoice.invoiceId}`;
    const paymentBody = { amount: 50, method: 'CARD', reference: 'e2e-first-attempt' };
    const firstPayment = await httpRequest(gateway, 'POST', `/api/v1/invoices/${invoice.invoiceId}/payments`, {
      token: billing.accessToken,
      headers: { 'Idempotency-Key': key },
      body: paymentBody,
    });
    expectStatus(firstPayment, 201, 'record payment');
    assert(
      firstPayment.headers.get('idempotent-replay') === null,
      'the first payment must not be flagged as a replay',
    );
    const afterFirst = unwrap(firstPayment, 'record payment');
    assertEqual(Number(afterFirst.paidAmount), 50, 'paid amount after the first payment');
    assertEqual(afterFirst.status, 'PARTIALLY_PAID', 'invoice status after a partial payment');

    // The retry the browser would send after a timeout.
    const replay = await httpRequest(gateway, 'POST', `/api/v1/invoices/${invoice.invoiceId}/payments`, {
      token: billing.accessToken,
      headers: { 'Idempotency-Key': key },
      body: paymentBody,
    });
    expectStatus(replay, 201, 'replay payment with the same key');
    assertEqual(replay.headers.get('idempotent-replay'), 'true', 'replay response header');
    assertEqual(Number(unwrap(replay, 'replay payment').paidAmount), 50, 'replay must not add money');

    const payments = unwrap(
      await httpRequest(gateway, 'GET', `/api/v1/invoices/${invoice.invoiceId}/payments`, {
        token: billing.accessToken,
      }),
      'list invoice payments',
    );
    assertEqual(payments.length, 1, 'exactly one payment exists after a retry');

    const conflicting = await httpRequest(gateway, 'POST', `/api/v1/invoices/${invoice.invoiceId}/payments`, {
      token: billing.accessToken,
      headers: { 'Idempotency-Key': key },
      body: { ...paymentBody, amount: 60 },
    });
    expectStatus(conflicting, 409, 'same key with a different payload');

    const patientPayment = await httpRequest(gateway, 'POST', `/api/v1/invoices/${invoice.invoiceId}/payments`, {
      token: patient.accessToken,
      body: { amount: 10, method: 'CARD' },
    });
    expectStatus(patientPayment, 403, 'patient records a payment');

    const paymentId = payments[0].paymentId;
    const refund = await httpRequest(gateway, 'POST', `/api/v1/payments/${paymentId}/refund`, {
      token: billing.accessToken,
    });
    expectStatus(refund, 200, 'refund the payment');
    const refunded = unwrap(refund, 'refund the payment');
    assertEqual(Number(refunded.paidAmount), 0, 'paid amount after refund');
    assertEqual(refunded.status, 'PENDING', 'invoice status after refund');

    const afterRefund = unwrap(
      await httpRequest(gateway, 'GET', `/api/v1/invoices/${invoice.invoiceId}/payments`, {
        token: billing.accessToken,
      }),
      'list payments after refund',
    );
    assertEqual(afterRefund[0].status, 'REFUNDED', 'payment status after refund');

    // Patients can read their own invoice, but not someone else's.
    expectStatus(
      await httpRequest(gateway, 'GET', `/api/v1/invoices/${SEED.pendingInvoiceId}`, { token: patient.accessToken }),
      403,
      "patient reads another patient's invoice",
    );
  });

  // ---------------------------------------------------------------- 6. Kafka fan-out
  if (canReach('notification-service') && canReach('analytics-service')) {
    await step('events: appointment events fan out to notification and analytics', async () => {
      const [patient, admin] = await Promise.all([
        login(gateway, USERS.patient.username, USERS.patient.password),
        login(gateway, USERS.admin.username, USERS.admin.password),
      ]);

      const unreadBefore = unwrap(
        await httpRequest(gateway, 'GET', '/api/v1/notifications/unread-count', { token: patient.accessToken }),
        'read unread count',
      );
      const analyticsBefore = unwrap(
        await httpRequest(gateway, 'GET', `/api/v1/analytics/appointments?from=${mondayDate}&to=${mondayDate}`, {
          token: admin.accessToken,
        }),
        'read analytics before booking',
      );
      const startedAt = new Date();

      const appointment = unwrap(
        await httpRequest(gateway, 'POST', '/api/v1/appointments', {
          token: patient.accessToken,
          body: {
            patientId: USERS.patient.id,
            doctorId: USERS.doctor.id,
            startTime: localDateTime(monday, 15, 0),
            endTime: localDateTime(monday, 15, 30),
            reason: 'E2E event fan-out',
          },
        }),
        'create appointment for event fan-out',
      );

      await waitFor('a notification for the new appointment', async () => {
        const page = unwrap(
          await httpRequest(gateway, 'GET', '/api/v1/notifications?page=0&size=20', { token: patient.accessToken }),
          'list notifications',
        );
        return page.content.some(
          (notification) =>
            notification.notificationType === 'APPOINTMENT_CREATED' &&
            notification.createdAt &&
            new Date(notification.createdAt) >= startedAt,
        );
      });

      await waitFor('the unread notification count to grow', async () => {
        const unread = unwrap(
          await httpRequest(gateway, 'GET', '/api/v1/notifications/unread-count', { token: patient.accessToken }),
          'read unread count',
        );
        return unread > unreadBefore;
      });

      await waitFor('the analytics appointment fact to be projected', async () => {
        const stats = unwrap(
          await httpRequest(gateway, 'GET', `/api/v1/analytics/appointments?from=${mondayDate}&to=${mondayDate}`, {
            token: admin.accessToken,
          }),
          'read analytics after booking',
        );
        return stats.totalAppointments > analyticsBefore.totalAppointments;
      });

      const summary = unwrap(
        await httpRequest(gateway, 'GET', '/api/v1/analytics/dashboard/summary', { token: admin.accessToken }),
        'read dashboard summary',
      );
      assert(summary.appointments >= 1, 'dashboard summary reports appointments');
      assert(appointment.appointmentId, 'event fan-out appointment id');
    });
  } else {
    skip(
      'events: appointment events fan out to notification and analytics',
      'start the full profile (`--profile=full`) to run the Kafka consumers',
    );
  }

  // ---------------------------------------------------------------- 7. shared revocation
  await step('sessions: a logout in auth-service is enforced by every other service', async () => {
    const patient = await login(gateway, USERS.patient.username, USERS.patient.password);

    expectStatus(
      await httpRequest(gateway, 'GET', '/api/v1/appointments?page=0&size=1', { token: patient.accessToken }),
      200,
      'appointments before logout',
    );

    const logout = await httpRequest(gateway, 'POST', '/api/v1/auth/logout', { token: patient.accessToken });
    expectStatus(logout, [200, 204], 'logout');

    await waitFor('the revoked token to be rejected by appointment-service', async () => {
      const response = await httpRequest(gateway, 'GET', '/api/v1/appointments?page=0&size=1', {
        token: patient.accessToken,
      });
      return response.status === 401;
    });
  });

  // ---------------------------------------------------------------- 8. readiness
  await step('platform: every started service reports a healthy readiness state', async () => {
    for (const [serviceName, url] of Object.entries(serviceUrls)) {
      const health = await httpRequest(url, 'GET', '/actuator/health', { timeoutMs: 10_000 });
      expectStatus(health, 200, `${serviceName} health`);
      assertEqual(health.json?.status, 'UP', `${serviceName} health status`);
    }
  });

  const failed = results.filter((result) => !result.ok);
  const passed = results.filter((result) => result.ok && !result.skipped);
  const skipped = results.filter((result) => result.skipped);
  log('');
  log(`Journeys: ${passed.length} passed, ${failed.length} failed, ${skipped.length} skipped`);
  return { passed: passed.length, failed: failed.length, skipped: skipped.length, results };
}
