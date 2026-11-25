/**
 * Deterministic in-browser API double shared by the Playwright suite and the demo mode.
 *
 * Every consumer gets a fresh instance, so browser tests never share state and the demo can be
 * reset by reloading the page. The double models the response envelopes and page shapes of the
 * real services closely enough to drive real UI code paths, including:
 *   - JWT login/refresh/me/logout and token revocation,
 *   - role-scoped directories (doctors, patients),
 *   - appointment creation, reschedule, cancellation and status transitions + history,
 *   - medical record create/update/history,
 *   - invoice and payment idempotency,
 *   - notifications and unread counts,
 *   - analytics summaries.
 *
 * The class is transport-neutral: it never touches the browser. Callers hand it a {@link Responder}
 * (Playwright's `Route` satisfies the shape) or use {@link MockApi.dispatch} for a plain response,
 * which is what the demo-mode `fetch` interceptor does.
 *
 * It also records every request so a test can assert what the UI actually sent (for example the
 * `Idempotency-Key` header or the start/end time of a booking).
 */

/** The parts of a request the double needs; Playwright's `Request` satisfies this shape. */
export interface MockRequest {
  method(): string;
  url(): string;
  headers(): Record<string, string>;
  postData(): string | null;
}

/** The parts of a response the double produces; a subset of Playwright's `fulfill` options. */
export interface MockFulfilOptions {
  status: number;
  headers?: Record<string, string>;
  body?: string;
}

/** Structural subset of Playwright's `Route`; a real Playwright route can be passed directly. */
export interface Responder {
  request(): MockRequest;
  fulfill(options: MockFulfilOptions): Promise<void>;
}

/** A captured response, ready to be serialised back through any transport. */
export interface MockResponse {
  status: number;
  headers: Record<string, string>;
  body?: string;
}

/** Demo-only behaviours; the Playwright suite constructs the double with no options. */
export interface MockApiOptions {
  /** Artificial latency (ms) applied to every request so loading states are observable. */
  latencyMs?: number;
  /**
   * Accept `access.<username>.<n>` / `refresh.<username>.<n>` tokens minted by an earlier page
   * load. The demo needs this: its token registry is in memory, but the browser keeps the tokens in
   * `localStorage` across a reload.
   */
  acceptSeededTokens?: boolean;
  /** Mirror doctor-schedule rules (working hours, approved leave, double booking) on writes. */
  enforceSchedule?: boolean;
  /**
   * Record the first payment and then answer 503, like a request that committed but whose response
   * was lost. Retrying with the same `Idempotency-Key` demonstrates the replay path.
   */
  simulatePaymentTimeoutOnce?: boolean;
}

export type Role = 'PATIENT' | 'DOCTOR' | 'ADMIN' | 'NURSE' | 'BILLING_STAFF' | 'SUPPORT';

export interface User {
  id: string;
  username: string;
  email: string;
  fullName: string;
  role: Role;
  password: string;
}

/** Same deterministic accounts/IDs as backend common-library DevSeedData. */
export const SEED_USERS: Record<Role, User> = {
  ADMIN: {
    id: '00000000-0000-0000-0000-000000000001',
    username: 'admin',
    email: 'admin@mediflow.local',
    fullName: 'Avery Admin',
    role: 'ADMIN',
    password: 'Admin@123',
  },
  DOCTOR: {
    id: '00000000-0000-0000-0000-000000000002',
    username: 'doctor',
    email: 'doctor@mediflow.local',
    fullName: 'Dana Doctor',
    role: 'DOCTOR',
    password: 'Doctor@123',
  },
  PATIENT: {
    id: '00000000-0000-0000-0000-000000000003',
    username: 'patient',
    email: 'patient@mediflow.local',
    fullName: 'Ada Patient',
    role: 'PATIENT',
    password: 'Patient@123',
  },
  NURSE: {
    id: '00000000-0000-0000-0000-000000000006',
    username: 'nurse',
    email: 'nurse@mediflow.local',
    fullName: 'Nina Nurse',
    role: 'NURSE',
    password: 'Nurse@123',
  },
  BILLING_STAFF: {
    id: '00000000-0000-0000-0000-000000000007',
    username: 'billing',
    email: 'billing@mediflow.local',
    fullName: 'Blake Billing',
    role: 'BILLING_STAFF',
    password: 'Billing@123',
  },
  SUPPORT: {
    id: '00000000-0000-0000-0000-000000000008',
    username: 'support',
    email: 'support@mediflow.local',
    fullName: 'Sam Support',
    role: 'SUPPORT',
    password: 'Support@123',
  },
};

export const SECOND_DOCTOR_ID = '00000000-0000-0000-0000-000000000004';
export const SECOND_PATIENT_ID = '00000000-0000-0000-0000-000000000005';

interface Doctor {
  doctorId: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  specialty: string;
  yearsOfExperience: number;
  department: string;
  qualification?: string;
  consultationFee: number;
  createdAt: string;
  updatedAt: string;
  version: number;
}

interface Availability {
  id: string;
  availabilityId: string;
  doctorId: string;
  dayOfWeek: string;
  startTime: string;
  endTime: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

interface Leave {
  id: string;
  leaveId: string;
  doctorId: string;
  startDate: string;
  endDate: string;
  reason?: string;
  createdAt: string;
  updatedAt: string;
}

interface Patient {
  patientId: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  dateOfBirth?: string;
  address?: string;
  medicalHistory?: string;
  insuranceProvider?: string;
  insuranceMemberId?: string;
  createdAt: string;
  updatedAt: string;
  version: number;
}

type AppointmentStatus = 'SCHEDULED' | 'CONFIRMED' | 'IN_PROGRESS' | 'CANCELLED' | 'COMPLETED' | 'NO_SHOW';

interface Appointment {
  appointmentId: string;
  patientId: string;
  doctorId: string;
  startTime: string;
  endTime: string;
  status: AppointmentStatus;
  notes?: string;
  reason: string;
  appointmentType?: string;
  createdAt: string;
  updatedAt: string;
  createdBy?: string;
  updatedBy?: string;
  version: number;
}

interface HistoryEntry {
  historyId: string;
  appointmentId: string;
  action: string;
  fromStatus?: AppointmentStatus;
  toStatus: AppointmentStatus;
  actor?: string;
  details?: string;
  occurredAt: string;
}

interface MedicalRecord {
  id: string;
  recordId: string;
  patientId: string;
  appointmentId?: string;
  doctorId?: string;
  encounterDate: string;
  recordType: string;
  chiefComplaint: string;
  diagnosis?: string;
  treatmentPlan?: string;
  notes: string;
  version: number;
  createdAt: string;
  updatedAt: string;
  createdBy?: string;
  updatedBy?: string;
}

interface RecordHistory {
  historyId: string;
  recordId: string;
  action: 'CREATED' | 'UPDATED';
  actor?: string;
  changedFields?: string;
  occurredAt: string;
}

interface Payment {
  paymentId: string;
  invoiceId: string;
  amount: number;
  method: string;
  reference?: string;
  status: 'COMPLETED' | 'REFUNDED';
  paidAt: string;
}

interface Invoice {
  invoiceId: string;
  invoiceNumber: string;
  patientId: string;
  appointmentId?: string;
  status: 'PENDING' | 'PARTIALLY_PAID' | 'PAID' | 'CANCELLED';
  currency: string;
  subtotal: number;
  taxRate: number;
  taxAmount: number;
  discount: number;
  totalAmount: number;
  paidAmount: number;
  remainingAmount: number;
  dueDate?: string;
  issuedAt: string;
  paidAt?: string;
  cancelledAt?: string;
  notes?: string;
  version: number;
  items: Array<{
    itemId: string;
    itemType: string;
    description: string;
    quantity: number;
    unitPrice: number;
    amount: number;
  }>;
  payments: Payment[];
  /** Idempotency keys already used for this invoice (server-side replay guard). */
  usedKeys: Set<string>;
}

interface Notification {
  id: string;
  notificationId: string;
  recipientId: string;
  notificationType: string;
  sourceEventId: string;
  channel: string;
  subject: string;
  message: string;
  readAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface RecordedRequest {
  method: string;
  path: string;
  query: URLSearchParams;
  headers: Record<string, string>;
  body: Record<string, unknown> | null;
}

interface Failure {
  method: string;
  path: string;
  status: number;
  message: string;
  /** When omitted the failure applies to every matching request. */
  remaining?: number;
}

interface Delay {
  method: string;
  path: string;
  ms: number;
}

const API_PREFIX = '/api/v1';
const PUBLIC_PATHS = new Set([
  '/api/v1/auth/login',
  '/api/v1/auth/register',
  '/api/v1/auth/refresh',
  '/api/v1/auth/password-reset/request',
  '/api/v1/auth/password-reset/confirm',
]);

function isoNow(): string {
  return new Date().toISOString();
}

function pad(value: number): string {
  return `${value}`.padStart(2, '0');
}

/** Naive local date-time, matching the wire format the services use for clinic times. */
export function localDateTime(date: Date, hour: number, minute = 0): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(hour)}:${pad(minute)}:00`;
}

function parseBody(raw: string | null): Record<string, unknown> | null {
  if (!raw) return null;
  try {
    return JSON.parse(raw) as Record<string, unknown>;
  } catch {
    return null;
  }
}

function pageOf<T>(items: T[], url: URL) {
  const size = Number(url.searchParams.get('size') ?? 20);
  const number = Number(url.searchParams.get('page') ?? 0);
  const start = number * size;
  const content = items.slice(start, start + size);
  return {
    content,
    totalPages: Math.ceil(items.length / size),
    totalElements: items.length,
    last: start + size >= items.length,
    first: number === 0,
    numberOfElements: content.length,
    size,
    number,
    empty: content.length === 0,
  };
}

function matches(pattern: string, pathname: string): boolean {
  return pattern.endsWith('*') ? pathname.startsWith(pattern.slice(0, -1)) : pathname === pattern;
}

export class MockApi {
  readonly requests: RecordedRequest[] = [];
  readonly revokedRefreshTokens = new Set<string>();
  /** List paths that should answer with an empty page (used by empty-state tests). */
  readonly emptyPaths = new Set<string>();
  /** When set, appointment creation answers 409 with this message. */
  appointmentConflict: string | null = null;
  /**
   * When set, every response echoes this value instead of the request's `X-Correlation-ID`.
   * Mirrors a gateway that regenerates an unsafe caller-supplied id and lets tests prove the
   * client reads the server-returned value.
   */
  correlationIdOverride: string | null = null;
  private currentCorrelationId: string | null = null;

  private readonly users = new Map<string, User>();
  private readonly accessTokens = new Map<string, string>();
  private readonly refreshTokens = new Map<string, string>();
  private readonly failures: Failure[] = [];
  private readonly delays: Delay[] = [];
  private readonly doctors: Doctor[] = [];
  private readonly availability: Availability[] = [];
  private readonly leaves: Leave[] = [];
  private readonly patients: Patient[] = [];
  private readonly appointments: Appointment[] = [];
  private readonly appointmentHistory = new Map<string, HistoryEntry[]>();
  private readonly records: MedicalRecord[] = [];
  private readonly recordHistory = new Map<string, RecordHistory[]>();
  private readonly invoices: Invoice[] = [];
  private readonly notifications: Notification[] = [];
  private tokenSequence = 0;
  private entitySequence = 0;
  private paymentTimeoutPending: boolean;

  constructor(private readonly options: MockApiOptions = {}) {
    this.paymentTimeoutPending = options.simulatePaymentTimeoutOnce ?? false;
    Object.values(SEED_USERS).forEach((user) => this.users.set(user.username, { ...user }));

    const now = new Date();
    const created = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 7);
    const createdAt = localDateTime(created, 8);
    const today = now;

    this.doctors.push(
      {
        doctorId: SEED_USERS.DOCTOR.id,
        firstName: 'Dana',
        lastName: 'Doctor',
        email: 'doctor@mediflow.local',
        phone: '555-0102',
        specialty: 'Cardiology',
        yearsOfExperience: 12,
        department: 'CARDIOLOGY',
        qualification: 'MD, FACC',
        consultationFee: 150,
        createdAt,
        updatedAt: createdAt,
        version: 0,
      },
      {
        doctorId: SECOND_DOCTOR_ID,
        firstName: 'Grace',
        lastName: 'Hopper',
        email: 'grace.hopper@mediflow.local',
        phone: '555-0104',
        specialty: 'Neurology',
        yearsOfExperience: 9,
        department: 'NEUROLOGY',
        consultationFee: 180,
        createdAt,
        updatedAt: createdAt,
        version: 0,
      },
    );

    this.availability.push(
      this.newAvailability(SEED_USERS.DOCTOR.id, 'MONDAY', '09:00:00', '17:00:00'),
      this.newAvailability(SEED_USERS.DOCTOR.id, 'WEDNESDAY', '09:00:00', '17:00:00'),
      this.newAvailability(SECOND_DOCTOR_ID, 'FRIDAY', '10:00:00', '16:00:00'),
    );

    const leaveStart = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 20);
    const leaveEnd = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 22);
    this.leaves.push({
      id: 'leave-0001',
      leaveId: 'leave-0001',
      doctorId: SECOND_DOCTOR_ID,
      startDate: `${leaveStart.getFullYear()}-${pad(leaveStart.getMonth() + 1)}-${pad(leaveStart.getDate())}`,
      endDate: `${leaveEnd.getFullYear()}-${pad(leaveEnd.getMonth() + 1)}-${pad(leaveEnd.getDate())}`,
      reason: 'Conference',
      createdAt,
      updatedAt: createdAt,
    });

    this.patients.push(
      {
        patientId: SEED_USERS.PATIENT.id,
        firstName: 'Ada',
        lastName: 'Patient',
        email: 'patient@mediflow.local',
        phone: '555-0103',
        dateOfBirth: '1990-04-12',
        address: '12 Care Street',
        insuranceProvider: 'MediCare Plus',
        insuranceMemberId: 'MCP-7781',
        createdAt,
        updatedAt: createdAt,
        version: 0,
      },
      {
        patientId: SECOND_PATIENT_ID,
        firstName: 'Bob',
        lastName: 'Patient',
        email: 'bob.patient@mediflow.local',
        phone: '555-0105',
        insuranceProvider: 'HealthFirst',
        createdAt,
        updatedAt: createdAt,
        version: 0,
      },
    );

    this.appointments.push(
      {
        appointmentId: 'appt-0001',
        patientId: SEED_USERS.PATIENT.id,
        doctorId: SEED_USERS.DOCTOR.id,
        startTime: localDateTime(today, 10),
        endTime: localDateTime(today, 10, 30),
        status: 'SCHEDULED',
        reason: 'Annual checkup',
        appointmentType: 'IN_PERSON',
        createdAt,
        updatedAt: createdAt,
        version: 0,
      },
      {
        appointmentId: 'appt-0002',
        patientId: SEED_USERS.PATIENT.id,
        doctorId: SEED_USERS.DOCTOR.id,
        startTime: localDateTime(today, 11),
        endTime: localDateTime(today, 11, 30),
        status: 'COMPLETED',
        reason: 'Follow-up',
        createdAt,
        updatedAt: createdAt,
        version: 0,
      },
      {
        appointmentId: 'appt-0003',
        patientId: SEED_USERS.PATIENT.id,
        doctorId: SECOND_DOCTOR_ID,
        startTime: localDateTime(new Date(today.getTime() - 86_400_000), 15),
        endTime: localDateTime(new Date(today.getTime() - 86_400_000), 15, 30),
        status: 'CANCELLED',
        reason: 'Migraine review',
        createdAt,
        updatedAt: createdAt,
        version: 0,
      },
    );

    // Audit trail for the seeded visits; the service writes these in the booking transaction.
    this.appointmentHistory.set('appt-0001', [
      {
        historyId: 'appt-hist-0001',
        appointmentId: 'appt-0001',
        action: 'CREATED',
        toStatus: 'SCHEDULED',
        actor: 'patient',
        occurredAt: createdAt,
      },
    ]);
    this.appointmentHistory.set('appt-0002', [
      {
        historyId: 'appt-hist-0002',
        appointmentId: 'appt-0002',
        action: 'CREATED',
        toStatus: 'SCHEDULED',
        actor: 'patient',
        occurredAt: createdAt,
      },
      {
        historyId: 'appt-hist-0003',
        appointmentId: 'appt-0002',
        action: 'COMPLETED',
        fromStatus: 'IN_PROGRESS',
        toStatus: 'COMPLETED',
        actor: 'doctor',
        occurredAt: createdAt,
      },
    ]);

    this.records.push({
      id: 'rec-0001',
      recordId: 'rec-0001',
      patientId: SEED_USERS.PATIENT.id,
      appointmentId: 'appt-0002',
      doctorId: SEED_USERS.DOCTOR.id,
      encounterDate: `${today.getFullYear()}-${pad(today.getMonth() + 1)}-${pad(today.getDate())}`,
      recordType: 'CONSULTATION',
      chiefComplaint: 'Chest tightness',
      diagnosis: 'Stable angina',
      treatmentPlan: 'Cardiology follow-up in four weeks',
      notes: 'ECG normal. Advised lifestyle changes.',
      version: 0,
      createdAt,
      updatedAt: createdAt,
      createdBy: 'doctor',
    });
    this.recordHistory.set('rec-0001', [
      {
        historyId: 'record-hist-0001',
        recordId: 'rec-0001',
        action: 'CREATED',
        actor: 'doctor',
        changedFields: 'chiefComplaint, notes',
        occurredAt: createdAt,
      },
    ]);

    this.invoices.push(
      this.newInvoice('inv-0001', 'INV-1001', SEED_USERS.PATIENT.id, 'appt-0002', 'PENDING', 150),
      this.newInvoice('inv-0002', 'INV-1002', SEED_USERS.PATIENT.id, undefined, 'PAID', 80),
    );
    const paidInvoice = this.invoices[1];
    paidInvoice.payments.push({
      paymentId: 'pay-0001',
      invoiceId: paidInvoice.invoiceId,
      amount: 80,
      method: 'CARD',
      reference: 'receipt-1',
      status: 'COMPLETED',
      paidAt: createdAt,
    });
    paidInvoice.paidAmount = 80;
    paidInvoice.remainingAmount = 0;

    this.notifications.push(
      {
        id: 'ntf-0001',
        notificationId: 'ntf-0001',
        recipientId: SEED_USERS.PATIENT.id,
        notificationType: 'APPOINTMENT_CREATED',
        sourceEventId: 'evt-0001',
        channel: 'IN_APP',
        subject: 'Appointment booked',
        message: 'Your annual checkup is confirmed.',
        createdAt,
        updatedAt: createdAt,
      },
      {
        id: 'ntf-0002',
        notificationId: 'ntf-0002',
        recipientId: SEED_USERS.PATIENT.id,
        notificationType: 'INVOICE_GENERATED',
        sourceEventId: 'evt-0002',
        channel: 'IN_APP',
        subject: 'Invoice generated',
        message: 'Invoice INV-1001 is ready.',
        createdAt,
        updatedAt: createdAt,
      },
      {
        id: 'ntf-0003',
        notificationId: 'ntf-0003',
        recipientId: SEED_USERS.DOCTOR.id,
        notificationType: 'APPOINTMENT_CREATED',
        sourceEventId: 'evt-0003',
        channel: 'IN_APP',
        subject: 'New booking',
        message: 'Ada Patient booked an annual checkup.',
        createdAt,
        updatedAt: createdAt,
      },
    );
  }

  // ---------------------------------------------------------------- setup helpers

  /**
   * Runs one request through the double and captures its response. This is the entry point used by
   * the demo `fetch` interceptor; the Playwright adapter calls it from a route handler too.
   */
  async dispatch(request: MockRequest): Promise<MockResponse> {
    const captured: { response?: MockResponse } = {};
    await this.handle({
      request: () => request,
      fulfill: async (options) => {
        captured.response = {
          status: options.status,
          headers: options.headers ?? {},
          body: options.body,
        };
      },
    });
    return (
      captured.response ?? {
        status: 500,
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({
          success: false,
          message: 'The mock API produced no response',
          timestamp: isoNow(),
        }),
      }
    );
  }

  /** Make a request fail once its path matches (prefix match when the pattern ends with *). */
  fail(method: string, path: string, status: number, message: string): void {
    this.failures.unshift({ method, path, status, message });
  }

  /** Like {@link fail}, but only the next matching request fails (used for retry paths). */
  failOnce(method: string, path: string, status: number, message: string): void {
    this.failures.unshift({ method, path, status, message, remaining: 1 });
  }

  /** Delay matching requests so loading states can be observed. */
  delay(method: string, path: string, ms: number): void {
    this.delays.push({ method, path, ms });
  }

  /**
   * Registers a session for the given role without going through the login form. Used to model a
   * browser that reloads with an expired access token but a still-valid refresh token.
   */
  issueSession(role: Role): { accessToken: string; refreshToken: string; userId: string } {
    const user = this.userFor(role);
    const tokens = this.mintTokens(user);
    return { accessToken: tokens.accessToken, refreshToken: tokens.refreshToken, userId: user.id };
  }

  /** Pre-register a refresh token that the mock will treat as revoked (expired session). */
  issueRevokedRefreshToken(role: Role): string {
    const user = this.userFor(role);
    const tokens = this.mintTokens(user);
    this.revokedRefreshTokens.add(tokens.refreshToken);
    return tokens.refreshToken;
  }

  /**
   * Models a browser holding tokens whose access token has expired and whose refresh token the
   * server has revoked, i.e. a session that can only end with the expiry notice.
   */
  issueRevokedSession(role: Role): { accessToken: string; refreshToken: string } {
    const user = this.userFor(role);
    const tokens = this.mintTokens(user);
    this.revokedRefreshTokens.add(tokens.refreshToken);
    return tokens;
  }

  requestsFor(method: string, path: string): RecordedRequest[] {
    return this.requests.filter((request) => request.method === method && matches(path, request.path));
  }

  lastRequest(method: string, path: string): RecordedRequest | undefined {
    return this.requestsFor(method, path).at(-1);
  }

  // ---------------------------------------------------------------- request handling

  private async handle(route: Responder): Promise<void> {
    const request = route.request();
    const url = new URL(request.url());
    const method = request.method();
    const path = url.pathname;
    const body = parseBody(request.postData());
    const headers = request.headers();
    // Echo the gateway contract: the accepted/regenerated id is returned on the response.
    this.currentCorrelationId = this.correlationIdOverride
      ?? headersValue(headers, 'x-correlation-id')
      ?? null;

    this.requests.push({ method, path, query: url.searchParams, headers, body });

    if (method === 'OPTIONS') {
      await route.fulfill({ status: 204 });
      return;
    }

    const latencyMs = this.options.latencyMs ?? 0;
    if (latencyMs > 0) await new Promise((resolve) => setTimeout(resolve, latencyMs));

    const delay = this.delays.find((entry) => entry.method === method && matches(entry.path, path));
    if (delay) await new Promise((resolve) => setTimeout(resolve, delay.ms));

    const failure = this.failures.find((entry) => entry.method === method && matches(entry.path, path));
    if (failure) {
      if (failure.remaining !== undefined) {
        failure.remaining -= 1;
        if (failure.remaining <= 0) this.failures.splice(this.failures.indexOf(failure), 1);
      }
      await this.failWith(route, failure.status, failure.message);
      return;
    }

    if (PUBLIC_PATHS.has(path)) {
      await this.handlePublic(route, method, path, body);
      return;
    }

    const user = this.authenticatedUser(headers);
    if (!user) {
      await this.failWith(route, 401, 'Authentication required');
      return;
    }

    if (method === 'GET' && this.emptyPaths.has(path)) {
      await this.ok(route, pageOf([], url));
      return;
    }

    await this.handleProtected(route, method, path, url, body, user);
  }

  private async handlePublic(
    route: Responder,
    method: string,
    path: string,
    body: Record<string, unknown> | null,
  ): Promise<void> {
    if (method === 'POST' && path === '/api/v1/auth/login') {
      const usernameOrEmail = String(body?.usernameOrEmail ?? '').trim().toLowerCase();
      const password = String(body?.password ?? '');
      const user = [...this.users.values()].find(
        (candidate) =>
          (candidate.username.toLowerCase() === usernameOrEmail ||
            candidate.email.toLowerCase() === usernameOrEmail) &&
          candidate.password === password,
      );
      if (!user) {
        await this.failWith(route, 401, 'Invalid username or password');
        return;
      }
      const tokens = this.mintTokens(user);
      await this.ok(route, this.authResponse(user, tokens));
      return;
    }

    if (method === 'POST' && path === '/api/v1/auth/register') {
      const username = String(body?.username ?? '').trim();
      const email = String(body?.email ?? '').trim();
      if (!username || !email || !body?.password) {
        await this.failWith(route, 400, 'username, email and password are required');
        return;
      }
      if ([...this.users.values()].some((candidate) => candidate.username === username)) {
        await this.failWith(route, 409, 'Username is already registered');
        return;
      }
      const user: User = {
        id: this.nextId('user'),
        username,
        email,
        fullName: String(body.fullName ?? username),
        role: 'PATIENT',
        password: String(body.password),
      };
      this.users.set(username, user);
      const tokens = this.mintTokens(user);
      await this.ok(route, this.authResponse(user, tokens), 201);
      return;
    }

    if (method === 'POST' && path === '/api/v1/auth/refresh') {
      const refreshToken = String(body?.refreshToken ?? '');
      const username =
        this.refreshTokens.get(refreshToken) ?? this.seededTokenUsername(refreshToken, 'refresh');
      const user = username ? this.users.get(username) : undefined;
      if (!user || this.revokedRefreshTokens.has(refreshToken)) {
        await this.failWith(route, 401, 'Refresh token is invalid or expired');
        return;
      }
      // Rotation: the presented refresh token cannot be replayed.
      this.refreshTokens.delete(refreshToken);
      const tokens = this.mintTokens(user);
      await this.ok(route, {
        accessToken: tokens.accessToken,
        refreshToken: tokens.refreshToken,
        tokenType: 'Bearer',
        expiresIn: 3600,
      });
      return;
    }

    if (method === 'POST' && path === '/api/v1/auth/password-reset/request') {
      await this.ok(route, { message: 'If the email exists, a reset link has been sent.' });
      return;
    }

    if (method === 'POST' && path === '/api/v1/auth/password-reset/confirm') {
      await this.ok(route, null);
      return;
    }

    await this.failWith(route, 404, `No mock handler for ${method} ${path}`);
  }

  private async handleProtected(
    route: Responder,
    method: string,
    path: string,
    url: URL,
    body: Record<string, unknown> | null,
    user: User,
  ): Promise<void> {
    if (path === '/api/v1/auth/me' && method === 'GET') {
      await this.ok(route, this.profile(user));
      return;
    }

    if (path === '/api/v1/auth/logout' && method === 'POST') {
      this.accessTokens.forEach((username, token) => {
        if (username === user.username) this.accessTokens.delete(token);
      });
      await this.ok(route, null);
      return;
    }

    if (path === '/api/v1/auth/change-password' && method === 'POST') {
      const newPassword = String(body?.newPassword ?? '');
      const stored = this.users.get(user.username);
      if (stored && newPassword) stored.password = newPassword;
      await this.ok(route, null);
      return;
    }

    if (path === '/api/v1/notifications/unread-count' && method === 'GET') {
      const count = this.notifications.filter(
        (notification) => notification.recipientId === user.id && !notification.readAt,
      ).length;
      await this.ok(route, count);
      return;
    }

    if (path === '/api/v1/notifications' && method === 'GET') {
      const own = this.notifications.filter((notification) => notification.recipientId === user.id);
      await this.ok(route, pageOf(own, url));
      return;
    }

    const markRead = path.match(/^\/api\/v1\/notifications\/([^/]+)\/read$/);
    if (markRead && method === 'PATCH') {
      const notification = this.notifications.find((item) => item.notificationId === markRead[1]);
      if (!notification) {
        await this.failWith(route, 404, 'Notification not found');
        return;
      }
      notification.readAt = isoNow();
      notification.updatedAt = notification.readAt;
      await this.ok(route, notification);
      return;
    }

    const doctorAvailability = path.match(/^\/api\/v1\/doctors\/([^/]+)\/availability(?:\/([^/]+))?$/);
    if (doctorAvailability) {
      await this.handleAvailability(route, method, doctorAvailability[1], doctorAvailability[2], body);
      return;
    }

    const doctorLeaves = path.match(/^\/api\/v1\/doctors\/([^/]+)\/leaves(?:\/([^/]+))?$/);
    if (doctorLeaves) {
      await this.handleLeaves(route, method, doctorLeaves[1], doctorLeaves[2], body);
      return;
    }

    if (path === '/api/v1/doctors' && method === 'GET') {
      const search = (url.searchParams.get('search') ?? '').toLowerCase();
      const department = url.searchParams.get('department') ?? '';
      const filtered = this.doctors.filter((doctor) => {
        const haystack = `${doctor.firstName} ${doctor.lastName} ${doctor.specialty}`.toLowerCase();
        return (!search || haystack.includes(search)) && (!department || doctor.department === department);
      });
      await this.ok(route, pageOf(filtered, url));
      return;
    }

    if (path === '/api/v1/doctors' && method === 'POST') {
      // The form submits an empty string when no auth user is linked; treat that as absent.
      const doctorId = body?.userId ? String(body.userId) : this.nextId('doctor');
      const doctor: Doctor = {
        doctorId,
        firstName: String(body?.firstName ?? ''),
        lastName: String(body?.lastName ?? ''),
        email: String(body?.email ?? ''),
        phone: String(body?.phone ?? ''),
        specialty: String(body?.specialty ?? ''),
        yearsOfExperience: Number(body?.yearsOfExperience ?? 0),
        department: String(body?.department ?? 'GENERAL'),
        qualification: body?.qualification ? String(body.qualification) : undefined,
        consultationFee: Number(body?.consultationFee ?? 0),
        createdAt: isoNow(),
        updatedAt: isoNow(),
        version: 0,
      };
      this.doctors.push(doctor);
      await this.ok(route, doctor, 201);
      return;
    }

    const doctorById = path.match(/^\/api\/v1\/doctors\/([^/]+)$/);
    if (doctorById) {
      const doctor = this.doctors.find((item) => item.doctorId === doctorById[1]);
      if (!doctor) {
        await this.failWith(route, 404, 'Doctor not found');
        return;
      }
      if (method === 'GET') {
        await this.ok(route, doctor);
        return;
      }
      if (method === 'PUT') {
        Object.assign(doctor, {
          firstName: String(body?.firstName ?? doctor.firstName),
          lastName: String(body?.lastName ?? doctor.lastName),
          email: String(body?.email ?? doctor.email),
          phone: String(body?.phone ?? doctor.phone),
          specialty: String(body?.specialty ?? doctor.specialty),
          department: String(body?.department ?? doctor.department),
          yearsOfExperience: Number(body?.yearsOfExperience ?? doctor.yearsOfExperience),
          consultationFee: Number(body?.consultationFee ?? doctor.consultationFee),
          updatedAt: isoNow(),
          version: doctor.version + 1,
        });
        await this.ok(route, doctor);
        return;
      }
      if (method === 'DELETE') {
        const index = this.doctors.indexOf(doctor);
        this.doctors.splice(index, 1);
        await route.fulfill({ status: 204 });
        return;
      }
    }

    if (path === '/api/v1/patients' && method === 'GET') {
      const search = (url.searchParams.get('search') ?? '').toLowerCase();
      const filtered = this.patients.filter((patient) =>
        `${patient.firstName} ${patient.lastName} ${patient.email} ${patient.phone}`
          .toLowerCase()
          .includes(search),
      );
      await this.ok(route, pageOf(filtered, url));
      return;
    }

    if (path === '/api/v1/patients' && method === 'POST') {
      const patientId = body?.userId ? String(body.userId) : this.nextId('patient');
      const patient: Patient = {
        patientId,
        firstName: String(body?.firstName ?? ''),
        lastName: String(body?.lastName ?? ''),
        email: String(body?.email ?? ''),
        phone: String(body?.phone ?? ''),
        dateOfBirth: body?.dateOfBirth ? String(body.dateOfBirth) : undefined,
        address: body?.address ? String(body.address) : undefined,
        medicalHistory: body?.medicalHistory ? String(body.medicalHistory) : undefined,
        insuranceProvider: body?.insuranceProvider ? String(body.insuranceProvider) : undefined,
        insuranceMemberId: body?.insuranceMemberId ? String(body.insuranceMemberId) : undefined,
        createdAt: isoNow(),
        updatedAt: isoNow(),
        version: 0,
      };
      this.patients.push(patient);
      await this.ok(route, patient, 201);
      return;
    }

    const patientById = path.match(/^\/api\/v1\/patients\/([^/]+)$/);
    if (patientById) {
      const patient = this.patients.find((item) => item.patientId === patientById[1]);
      if (!patient) {
        await this.failWith(route, 404, 'Patient not found');
        return;
      }
      if (method === 'GET') {
        await this.ok(route, patient);
        return;
      }
      if (method === 'PUT') {
        Object.assign(patient, {
          firstName: String(body?.firstName ?? patient.firstName),
          lastName: String(body?.lastName ?? patient.lastName),
          email: String(body?.email ?? patient.email),
          phone: String(body?.phone ?? patient.phone),
          updatedAt: isoNow(),
          version: patient.version + 1,
        });
        await this.ok(route, patient);
        return;
      }
      if (method === 'DELETE') {
        this.patients.splice(this.patients.indexOf(patient), 1);
        await route.fulfill({ status: 204 });
        return;
      }
    }

    const appointmentHistoryRoute = path.match(/^\/api\/v1\/appointments\/([^/]+)\/history$/);
    if (appointmentHistoryRoute && method === 'GET') {
      await this.ok(route, this.appointmentHistory.get(appointmentHistoryRoute[1]) ?? []);
      return;
    }

    const appointmentTransition = path.match(
      /^\/api\/v1\/appointments\/([^/]+)\/(confirm|start|complete|no-show|cancel)$/,
    );
    if (appointmentTransition && method === 'PATCH') {
      const appointment = this.appointments.find((item) => item.appointmentId === appointmentTransition[1]);
      if (!appointment) {
        await this.failWith(route, 404, 'Appointment not found');
        return;
      }
      const action = appointmentTransition[2];
      const nextStatus: Record<string, AppointmentStatus> = {
        confirm: 'CONFIRMED',
        start: 'IN_PROGRESS',
        complete: 'COMPLETED',
        'no-show': 'NO_SHOW',
        cancel: 'CANCELLED',
      };
      const from = appointment.status;
      appointment.status = nextStatus[action];
      appointment.updatedAt = isoNow();
      appointment.version += 1;
      this.addAppointmentHistory(appointment, action.toUpperCase().replace('-', '_'), from, user.username);
      await this.ok(route, appointment);
      return;
    }

    if (path === '/api/v1/appointments' && method === 'GET') {
      const patientId = url.searchParams.get('patientId') ?? '';
      const doctorId = url.searchParams.get('doctorId') ?? '';
      const status = url.searchParams.get('status') ?? '';
      const fromTime = url.searchParams.get('fromTime') ?? '';
      const toTime = url.searchParams.get('toTime') ?? '';
      const filtered = this.appointments
        .filter(
          (appointment) =>
            (!patientId || appointment.patientId === patientId) &&
            (!doctorId || appointment.doctorId === doctorId) &&
            (!status || appointment.status === status) &&
            (!fromTime || appointment.startTime >= fromTime) &&
            (!toTime || appointment.startTime < toTime),
        )
        .sort((a, b) => a.startTime.localeCompare(b.startTime));
      await this.ok(route, pageOf(filtered, url));
      return;
    }

    if (path === '/api/v1/appointments' && method === 'POST') {
      if (this.appointmentConflict) {
        await this.failWith(route, 409, this.appointmentConflict);
        return;
      }
      const patientId = String(body?.patientId ?? '');
      const doctorId = String(body?.doctorId ?? '');
      const startTime = String(body?.startTime ?? '');
      const endTime = String(body?.endTime ?? '');
      const conflict = this.options.enforceSchedule
        ? this.scheduleConflict(patientId, doctorId, startTime, endTime)
        : null;
      if (conflict) {
        await this.failWith(route, 409, conflict);
        return;
      }
      const appointment: Appointment = {
        appointmentId: this.nextId('appt'),
        patientId,
        doctorId,
        startTime,
        endTime,
        status: 'SCHEDULED',
        reason: String(body?.reason ?? ''),
        notes: body?.notes ? String(body.notes) : undefined,
        appointmentType: body?.appointmentType ? String(body.appointmentType) : undefined,
        createdAt: isoNow(),
        updatedAt: isoNow(),
        createdBy: user.username,
        version: 0,
      };
      this.appointments.push(appointment);
      this.addAppointmentHistory(appointment, 'CREATED', undefined, user.username);
      await this.ok(route, appointment, 201);
      return;
    }

    const appointmentById = path.match(/^\/api\/v1\/appointments\/([^/]+)$/);
    if (appointmentById) {
      const appointment = this.appointments.find((item) => item.appointmentId === appointmentById[1]);
      if (!appointment) {
        await this.failWith(route, 404, 'Appointment not found');
        return;
      }
      if (method === 'GET') {
        await this.ok(route, appointment);
        return;
      }
      if (method === 'PUT') {
        const startTime = String(body?.startTime ?? appointment.startTime);
        const endTime = String(body?.endTime ?? appointment.endTime);
        const conflict = this.options.enforceSchedule
          ? this.scheduleConflict(appointment.patientId, appointment.doctorId, startTime, endTime, appointment.appointmentId)
          : null;
        if (conflict) {
          await this.failWith(route, 409, conflict);
          return;
        }
        const from = appointment.status;
        appointment.startTime = startTime;
        appointment.endTime = endTime;
        if (body?.reason) appointment.reason = String(body.reason);
        if (body?.notes !== undefined) appointment.notes = String(body.notes);
        if (body?.appointmentType !== undefined) appointment.appointmentType = String(body.appointmentType);
        appointment.updatedAt = isoNow();
        appointment.version += 1;
        this.addAppointmentHistory(appointment, 'RESCHEDULED', from, user.username);
        await this.ok(route, appointment);
        return;
      }
    }

    const recordHistoryRoute = path.match(/^\/api\/v1\/medical-records\/([^/]+)\/history$/);
    if (recordHistoryRoute && method === 'GET') {
      await this.ok(route, this.recordHistory.get(recordHistoryRoute[1]) ?? []);
      return;
    }

    if (path === '/api/v1/medical-records' && method === 'GET') {
      const patientId = url.searchParams.get('patientId') ?? '';
      const doctorId = url.searchParams.get('doctorId') ?? '';
      const recordType = url.searchParams.get('recordType') ?? '';
      const filtered = this.records.filter(
        (record) =>
          (!patientId || record.patientId === patientId) &&
          (!doctorId || record.doctorId === doctorId) &&
          (!recordType || record.recordType === recordType),
      );
      await this.ok(route, pageOf(filtered, url));
      return;
    }

    if (path === '/api/v1/medical-records' && method === 'POST') {
      const recordId = this.nextId('rec');
      const record: MedicalRecord = {
        id: recordId,
        recordId,
        patientId: String(body?.patientId ?? ''),
        appointmentId: body?.appointmentId ? String(body.appointmentId) : undefined,
        doctorId: body?.doctorId ? String(body.doctorId) : undefined,
        encounterDate: String(body?.encounterDate ?? ''),
        recordType: String(body?.recordType ?? 'CONSULTATION'),
        chiefComplaint: String(body?.chiefComplaint ?? ''),
        diagnosis: body?.diagnosis ? String(body.diagnosis) : undefined,
        treatmentPlan: body?.treatmentPlan ? String(body.treatmentPlan) : undefined,
        notes: String(body?.notes ?? ''),
        version: 0,
        createdAt: isoNow(),
        updatedAt: isoNow(),
        createdBy: user.username,
      };
      this.records.push(record);
      this.recordHistory.set(record.recordId, [
        {
          historyId: this.nextId('record-hist'),
          recordId: record.recordId,
          action: 'CREATED',
          actor: user.username,
          changedFields: 'chiefComplaint, notes',
          occurredAt: isoNow(),
        },
      ]);
      await this.ok(route, record, 201);
      return;
    }

    const recordById = path.match(/^\/api\/v1\/medical-records\/([^/]+)$/);
    if (recordById) {
      const record = this.records.find((item) => item.recordId === recordById[1]);
      if (!record) {
        await this.failWith(route, 404, 'Medical record not found');
        return;
      }
      if (method === 'GET') {
        await this.ok(route, record);
        return;
      }
      if (method === 'PUT') {
        const changed: string[] = [];
        if (body?.chiefComplaint !== undefined) {
          record.chiefComplaint = String(body.chiefComplaint);
          changed.push('chiefComplaint');
        }
        if (body?.diagnosis !== undefined) {
          record.diagnosis = String(body.diagnosis);
          changed.push('diagnosis');
        }
        if (body?.treatmentPlan !== undefined) {
          record.treatmentPlan = String(body.treatmentPlan);
          changed.push('treatmentPlan');
        }
        if (body?.notes !== undefined) {
          record.notes = String(body.notes);
          changed.push('notes');
        }
        record.updatedAt = isoNow();
        record.version += 1;
        record.updatedBy = user.username;
        this.recordHistory.get(record.recordId)?.push({
          historyId: this.nextId('record-hist'),
          recordId: record.recordId,
          action: 'UPDATED',
          actor: user.username,
          changedFields: changed.join(', '),
          occurredAt: isoNow(),
        });
        await this.ok(route, record);
        return;
      }
    }

    if (path === '/api/v1/invoices' && method === 'GET') {
      const patientId = url.searchParams.get('patientId') ?? '';
      const status = url.searchParams.get('status') ?? '';
      const appointmentId = url.searchParams.get('appointmentId') ?? '';
      const filtered = this.invoices.filter(
        (invoice) =>
          (!patientId || invoice.patientId === patientId) &&
          (!status || invoice.status === status) &&
          (!appointmentId || invoice.appointmentId === appointmentId),
      );
      await this.ok(route, pageOf(filtered, url));
      return;
    }

    if (path === '/api/v1/invoices' && method === 'POST') {
      const items = Array.isArray(body?.items) ? (body.items as Array<Record<string, unknown>>) : [];
      const subtotal = items.reduce(
        (sum, item) => sum + Number(item.quantity ?? 0) * Number(item.unitPrice ?? 0),
        0,
      );
      const taxRate = Number(body?.taxRate ?? 0);
      const discount = Number(body?.discount ?? 0);
      const taxAmount = (subtotal * taxRate) / 100;
      const invoice: Invoice = {
        invoiceId: this.nextId('inv'),
        invoiceNumber: `INV-${1000 + this.invoices.length + 1}`,
        patientId: String(body?.patientId ?? ''),
        appointmentId: body?.appointmentId ? String(body.appointmentId) : undefined,
        status: 'PENDING',
        currency: String(body?.currency ?? 'USD'),
        subtotal,
        taxRate,
        taxAmount,
        discount,
        totalAmount: subtotal + taxAmount - discount,
        paidAmount: 0,
        remainingAmount: subtotal + taxAmount - discount,
        dueDate: body?.dueDate ? String(body.dueDate) : undefined,
        issuedAt: isoNow(),
        notes: body?.notes ? String(body.notes) : undefined,
        version: 0,
        items: items.map((item, index) => ({
          itemId: `item-${index}`,
          itemType: String(item.itemType ?? 'OTHER'),
          description: String(item.description ?? ''),
          quantity: Number(item.quantity ?? 1),
          unitPrice: Number(item.unitPrice ?? 0),
          amount: Number(item.quantity ?? 1) * Number(item.unitPrice ?? 0),
        })),
        payments: [],
        usedKeys: new Set<string>(),
      };
      this.invoices.push(invoice);
      await this.ok(route, invoice, 201);
      return;
    }

    const invoicePayments = path.match(/^\/api\/v1\/invoices\/([^/]+)\/payments$/);
    if (invoicePayments && method === 'GET') {
      const invoice = this.invoices.find((item) => item.invoiceId === invoicePayments[1]);
      await this.ok(route, invoice?.payments ?? []);
      return;
    }

    if (invoicePayments && method === 'POST') {
      const invoice = this.invoices.find((item) => item.invoiceId === invoicePayments[1]);
      if (!invoice) {
        await this.failWith(route, 404, 'Invoice not found');
        return;
      }
      const key = headersValue(this.requests.at(-1)?.headers, 'idempotency-key');
      let replay = false;
      if (key) {
        if (invoice.usedKeys.has(key)) {
          replay = true;
        } else {
          invoice.usedKeys.add(key);
        }
      }
      if (!replay) {
        const amount = Number(body?.amount ?? 0);
        invoice.payments.push({
          paymentId: this.nextId('pay'),
          invoiceId: invoice.invoiceId,
          amount,
          method: String(body?.method ?? 'CARD'),
          reference: body?.reference ? String(body.reference) : undefined,
          status: 'COMPLETED',
          paidAt: isoNow(),
        });
        invoice.paidAmount += amount;
        invoice.remainingAmount = Math.max(0, invoice.totalAmount - invoice.paidAmount);
        invoice.status =
          invoice.remainingAmount === 0 ? 'PAID' : invoice.paidAmount > 0 ? 'PARTIALLY_PAID' : 'PENDING';
        if (invoice.remainingAmount === 0) invoice.paidAt = isoNow();
        invoice.version += 1;
      }
      if (!replay && this.paymentTimeoutPending) {
        // The payment is committed but the response is lost. The retry must reuse the key.
        this.paymentTimeoutPending = false;
        await this.failWith(
          route,
          503,
          'The payment was recorded but the response timed out. Retry with the same Idempotency-Key to see the replay.',
        );
        return;
      }
      await this.ok(route, invoice, 200, replay ? { 'Idempotent-Replay': 'true' } : undefined);
      return;
    }

    const invoiceCancel = path.match(/^\/api\/v1\/invoices\/([^/]+)\/cancel$/);
    if (invoiceCancel && method === 'POST') {
      const invoice = this.invoices.find((item) => item.invoiceId === invoiceCancel[1]);
      if (!invoice) {
        await this.failWith(route, 404, 'Invoice not found');
        return;
      }
      invoice.status = 'CANCELLED';
      invoice.cancelledAt = isoNow();
      invoice.version += 1;
      await this.ok(route, invoice);
      return;
    }

    const invoiceById = path.match(/^\/api\/v1\/invoices\/([^/]+)$/);
    if (invoiceById) {
      const invoice = this.invoices.find((item) => item.invoiceId === invoiceById[1]);
      if (!invoice) {
        await this.failWith(route, 404, 'Invoice not found');
        return;
      }
      if (method === 'GET') {
        await this.ok(route, invoice);
        return;
      }
      if (method === 'PUT') {
        invoice.notes = body?.notes !== undefined ? String(body.notes) : invoice.notes;
        invoice.version += 1;
        await this.ok(route, invoice);
        return;
      }
    }

    const refund = path.match(/^\/api\/v1\/payments\/([^/]+)\/refund$/);
    if (refund && method === 'POST') {
      const invoice = this.invoices.find((item) =>
        item.payments.some((payment) => payment.paymentId === refund[1]),
      );
      const payment = invoice?.payments.find((item) => item.paymentId === refund[1]);
      if (!invoice || !payment) {
        await this.failWith(route, 404, 'Payment not found');
        return;
      }
      payment.status = 'REFUNDED';
      invoice.paidAmount = Math.max(0, invoice.paidAmount - payment.amount);
      invoice.remainingAmount = invoice.totalAmount - invoice.paidAmount;
      invoice.status = invoice.paidAmount > 0 ? 'PARTIALLY_PAID' : 'PENDING';
      await this.ok(route, invoice);
      return;
    }

    if (path.startsWith('/api/v1/analytics/') && method === 'GET') {
      await this.ok(route, this.analytics(path));
      return;
    }

    await this.failWith(route, 404, `No mock handler for ${method} ${path}`);
  }

  // ---------------------------------------------------------------- domain helpers

  private async handleAvailability(
    route: Responder,
    method: string,
    doctorId: string,
    availabilityId: string | undefined,
    body: Record<string, unknown> | null,
  ): Promise<void> {
    if (method === 'GET') {
      await this.ok(
        route,
        this.availability.filter((slot) => slot.doctorId === doctorId && slot.active),
      );
      return;
    }
    if (method === 'POST') {
      const slot = this.newAvailability(
        doctorId,
        String(body?.dayOfWeek ?? 'MONDAY'),
        String(body?.startTime ?? '09:00:00'),
        String(body?.endTime ?? '17:00:00'),
      );
      this.availability.push(slot);
      await this.ok(route, slot, 201);
      return;
    }
    const slot = this.availability.find((item) => item.availabilityId === availabilityId);
    if (!slot) {
      await this.failWith(route, 404, 'Availability not found');
      return;
    }
    if (method === 'PUT') {
      slot.dayOfWeek = String(body?.dayOfWeek ?? slot.dayOfWeek);
      slot.startTime = String(body?.startTime ?? slot.startTime);
      slot.endTime = String(body?.endTime ?? slot.endTime);
      if (body?.active !== undefined) slot.active = Boolean(body.active);
      slot.updatedAt = isoNow();
      await this.ok(route, slot);
      return;
    }
    if (method === 'DELETE') {
      slot.active = false;
      slot.updatedAt = isoNow();
      await this.ok(route, slot);
      return;
    }
    await this.failWith(route, 405, 'Unsupported availability method');
  }

  private async handleLeaves(
    route: Responder,
    method: string,
    doctorId: string,
    leaveId: string | undefined,
    body: Record<string, unknown> | null,
  ): Promise<void> {
    if (method === 'GET') {
      await this.ok(route, this.leaves.filter((leave) => leave.doctorId === doctorId));
      return;
    }
    if (method === 'POST') {
      const leave: Leave = {
        id: this.nextId('leave'),
        leaveId: this.nextId('leave'),
        doctorId,
        startDate: String(body?.startDate ?? ''),
        endDate: String(body?.endDate ?? ''),
        reason: body?.reason ? String(body.reason) : undefined,
        createdAt: isoNow(),
        updatedAt: isoNow(),
      };
      this.leaves.push(leave);
      await this.ok(route, leave, 201);
      return;
    }
    const index = this.leaves.findIndex(
      (leave) => leave.leaveId === leaveId && leave.doctorId === doctorId,
    );
    if (index < 0) {
      await this.failWith(route, 404, 'Leave not found');
      return;
    }
    if (method === 'PUT') {
      const leave = this.leaves[index];
      leave.startDate = String(body?.startDate ?? leave.startDate);
      leave.endDate = String(body?.endDate ?? leave.endDate);
      leave.reason = body?.reason !== undefined ? String(body.reason) : leave.reason;
      leave.updatedAt = isoNow();
      await this.ok(route, leave);
      return;
    }
    if (method === 'DELETE') {
      this.leaves.splice(index, 1);
      await route.fulfill({ status: 204 });
      return;
    }
    await this.failWith(route, 405, 'Unsupported leave method');
  }

  /**
   * Mirrors the real booking rules: doctor-service `validateSlot` (working hours + approved leave)
   * and appointment-service overlap checks. Returns the first conflict message, or null when the
   * slot is bookable.
   */
  private scheduleConflict(
    patientId: string,
    doctorId: string,
    startTime: string,
    endTime: string,
    excludeAppointmentId?: string,
  ): string | null {
    if (!startTime || !endTime) return 'Appointment start and end times are required';
    const start = new Date(startTime);
    const end = new Date(endTime);
    if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) {
      return 'Appointment times must be valid date-times';
    }
    if (end <= start) return 'Appointment end time must be after start time';
    if (start.toDateString() !== end.toDateString()) {
      return 'An appointment must start and end on the same day';
    }

    const date = `${start.getFullYear()}-${pad(start.getMonth() + 1)}-${pad(start.getDate())}`;
    const onLeave = this.leaves.some(
      (leave) => leave.doctorId === doctorId && date >= leave.startDate && date <= leave.endDate,
    );
    if (onLeave) return `The doctor is on leave on ${date}. Please choose another date.`;

    const dayOfWeek = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'][
      start.getDay()
    ];
    const windows = this.availability.filter(
      (window) => window.doctorId === doctorId && window.active && window.dayOfWeek === dayOfWeek,
    );
    if (windows.length === 0) {
      return `The doctor has no working hours configured for ${dayOfWeek}`;
    }
    const minutes = (value: string) => Number(value.slice(0, 2)) * 60 + Number(value.slice(3, 5));
    const requestedStart = start.getHours() * 60 + start.getMinutes();
    const requestedEnd = end.getHours() * 60 + end.getMinutes();
    const fits = windows.some(
      (window) => requestedStart >= minutes(window.startTime) && requestedEnd <= minutes(window.endTime),
    );
    if (!fits) {
      const description = windows
        .map((window) => `${window.dayOfWeek} ${window.startTime.slice(0, 5)}-${window.endTime.slice(0, 5)}`)
        .join(', ');
      return `The requested slot is outside the doctor's working hours (${description})`;
    }

    const overlaps = (appointment: Appointment) => {
      if (appointment.appointmentId === excludeAppointmentId) return false;
      if (appointment.status === 'CANCELLED') return false;
      return (
        start.getTime() < new Date(appointment.endTime).getTime() &&
        end.getTime() > new Date(appointment.startTime).getTime()
      );
    };
    if (this.appointments.some((appointment) => appointment.doctorId === doctorId && overlaps(appointment))) {
      return 'The doctor already has an appointment that overlaps this time slot';
    }
    if (this.appointments.some((appointment) => appointment.patientId === patientId && overlaps(appointment))) {
      return 'The patient already has an appointment that overlaps this time slot';
    }
    return null;
  }

  private newAvailability(doctorId: string, dayOfWeek: string, startTime: string, endTime: string): Availability {
    const id = this.nextId('availability');
    return {
      id,
      availabilityId: id,
      doctorId,
      dayOfWeek,
      startTime,
      endTime,
      active: true,
      createdAt: isoNow(),
      updatedAt: isoNow(),
    };
  }

  private newInvoice(
    invoiceId: string,
    invoiceNumber: string,
    patientId: string,
    appointmentId: string | undefined,
    status: Invoice['status'],
    total: number,
  ): Invoice {
    return {
      invoiceId,
      invoiceNumber,
      patientId,
      appointmentId,
      status,
      currency: 'USD',
      subtotal: total,
      taxRate: 0,
      taxAmount: 0,
      discount: 0,
      totalAmount: total,
      paidAmount: 0,
      remainingAmount: total,
      issuedAt: isoNow(),
      version: 0,
      items: [
        {
          itemId: `${invoiceId}-item-1`,
          itemType: 'CONSULTATION',
          description: 'Consultation',
          quantity: 1,
          unitPrice: total,
          amount: total,
        },
      ],
      payments: [],
      usedKeys: new Set<string>(),
    };
  }

  private addAppointmentHistory(
    appointment: Appointment,
    action: string,
    fromStatus: AppointmentStatus | undefined,
    actor: string,
  ): void {
    const entries = this.appointmentHistory.get(appointment.appointmentId) ?? [];
    entries.push({
      historyId: this.nextId('appt-hist'),
      appointmentId: appointment.appointmentId,
      action,
      fromStatus,
      toStatus: appointment.status,
      actor,
      occurredAt: isoNow(),
    });
    this.appointmentHistory.set(appointment.appointmentId, entries);
  }

  private analytics(path: string): unknown {
    if (path === '/api/v1/analytics/dashboard/summary') {
      return {
        from: '2026-01-01',
        to: '2026-12-31',
        appointments: 12,
        completedAppointments: 7,
        cancelledAppointments: 2,
        invoicedRevenue: 4200,
        collectedRevenue: 3100,
        refundedRevenue: 0,
        outstandingRevenue: 1100,
        activePatients: 5,
        doctors: 2,
      };
    }
    if (path === '/api/v1/analytics/appointments') {
      return {
        from: '2026-01-01',
        to: '2026-12-31',
        totalAppointments: 12,
        completedAppointments: 7,
        cancelledAppointments: 2,
        statusBreakdown: [
          { status: 'COMPLETED', count: 7 },
          { status: 'SCHEDULED', count: 3 },
          { status: 'CANCELLED', count: 2 },
        ],
        dailyTrend: [
          { date: '2026-11-16', count: 2 },
          { date: '2026-11-17', count: 4 },
        ],
      };
    }
    if (path === '/api/v1/analytics/revenue') {
      return {
        from: '2026-01-01',
        to: '2026-12-31',
        invoiced: 4200,
        collected: 3100,
        refunded: 0,
        outstanding: 1100,
        monthlyTrend: [
          { month: '2026-10', invoiced: 2000, collected: 1500, refunded: 0 },
          { month: '2026-11', invoiced: 2200, collected: 1600, refunded: 0 },
        ],
      };
    }
    if (path === '/api/v1/analytics/doctors') {
      return [
        { doctorId: SEED_USERS.DOCTOR.id, appointments: 8, completed: 6 },
        { doctorId: SECOND_DOCTOR_ID, appointments: 4, completed: 1 },
      ];
    }
    if (path === '/api/v1/analytics/patients') {
      return {
        from: '2026-01-01',
        to: '2026-12-31',
        totalPatients: 5,
        newPatients: 2,
        mostActive: [
          { patientId: SEED_USERS.PATIENT.id, appointments: 3, firstAppointment: '2026-01-15' },
          { patientId: SECOND_PATIENT_ID, appointments: 1, firstAppointment: '2026-05-02' },
        ],
      };
    }
    return null;
  }

  // ---------------------------------------------------------------- auth helpers

  private userFor(role: Role): User {
    const user = [...this.users.values()].find((candidate) => candidate.role === role);
    if (!user) throw new Error(`No seeded user for role ${role}`);
    return user;
  }

  private mintTokens(user: User): { accessToken: string; refreshToken: string } {
    const sequence = ++this.tokenSequence;
    const accessToken = `access.${user.username}.${sequence}`;
    const refreshToken = `refresh.${user.username}.${sequence}`;
    this.accessTokens.set(accessToken, user.username);
    this.refreshTokens.set(refreshToken, user.username);
    return { accessToken, refreshToken };
  }

  private authenticatedUser(headers: Record<string, string>): User | null {
    const authorization = headersValue(headers, 'authorization');
    if (!authorization?.startsWith('Bearer ')) return null;
    const token = authorization.slice('Bearer '.length);
    const username = this.accessTokens.get(token) ?? this.seededTokenUsername(token, 'access');
    return username ? this.users.get(username) ?? null : null;
  }

  /**
   * Recovers the username from a token minted by an earlier demo page load. Only enabled with
   * {@link MockApiOptions.acceptSeededTokens}; otherwise tokens must be in the live registry.
   */
  private seededTokenUsername(token: string, kind: 'access' | 'refresh'): string | undefined {
    if (!this.options.acceptSeededTokens) return undefined;
    const match = token.match(new RegExp(`^${kind}\\.([^.]+)\\.\\d+$`));
    const username = match?.[1];
    return username && this.users.has(username) ? username : undefined;
  }

  private authResponse(
    user: User,
    tokens: { accessToken: string; refreshToken: string },
  ): Record<string, unknown> {
    return {
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
      tokenType: 'Bearer',
      expiresIn: 3600,
      username: user.username,
      email: user.email,
      role: user.role,
    };
  }

  private profile(user: User): Record<string, unknown> {
    return {
      id: user.id,
      username: user.username,
      email: user.email,
      fullName: user.fullName,
      status: 'ACTIVE',
      roles: [user.role],
    };
  }

  private nextId(prefix: string): string {
    this.entitySequence += 1;
    return `${prefix}-${String(this.entitySequence).padStart(4, '0')}`;
  }

  // ---------------------------------------------------------------- transport helpers

  /** Correlation headers echoed on every response, matching the gateway contract. */
  private correlationHeaders(): Record<string, string> {
    return this.currentCorrelationId ? { 'X-Correlation-ID': this.currentCorrelationId } : {};
  }

  private async ok(
    route: Responder,
    data: unknown,
    status = 200,
    headers?: Record<string, string>,
  ): Promise<void> {
    await route.fulfill({
      status,
      headers: {
        ...this.correlationHeaders(),
        ...(headers ?? {}),
        'content-type': 'application/json',
      },
      body: JSON.stringify({
        success: true,
        message: 'OK',
        data,
        timestamp: isoNow(),
      }),
    });
  }

  private async failWith(route: Responder, status: number, message: string): Promise<void> {
    await route.fulfill({
      status,
      headers: { ...this.correlationHeaders(), 'content-type': 'application/json' },
      body: JSON.stringify({
        success: false,
        message,
        timestamp: isoNow(),
      }),
    });
  }
}

function headersValue(headers: Record<string, string> | undefined, name: string): string | undefined {
  if (!headers) return undefined;
  const key = Object.keys(headers).find((candidate) => candidate.toLowerCase() === name);
  return key ? headers[key] : undefined;
}
