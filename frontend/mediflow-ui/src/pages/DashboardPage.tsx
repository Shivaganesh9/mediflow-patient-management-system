import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useAsync } from '../hooks/useAsync';
import { analyticsApi } from '../api/analytics';
import { appointmentsApi } from '../api/appointments';
import { billingApi } from '../api/billing';
import { medicalRecordsApi } from '../api/medical-records';
import { notificationsApi } from '../api/notifications';
import { DashboardSummary } from '../types/analytics';
import { StatusPill } from '../components/StatusPill';
import { LoadingState, ErrorState, EmptyState } from '../components/PageState';
import { formatCurrency, formatDate, formatDateTime, shortId } from '../utils/format';

export function DashboardPage() {
  const { user, roles, hasRole } = useAuth();
  const isAdmin = hasRole('ADMIN');
  const isDoctor = hasRole('DOCTOR');
  const isPatient = hasRole('PATIENT');
  const isOps = isAdmin || isDoctor;

  const summary = useAsync<DashboardSummary | null>(
    () => (isOps ? analyticsApi.dashboardSummary() : Promise.resolve(null)),
    [isOps],
  );

  const scope = {
    patientId: isPatient ? user?.id : undefined,
    doctorId: isDoctor ? user?.id : undefined,
  };
  const upcoming = useAsync(
    () => appointmentsApi.list({ ...scope, page: 0, size: 5 }),
    [scope.patientId, scope.doctorId],
  );

  // A doctor's dashboard leads with today's clinic list, which is the first thing they need.
  const todayRange = currentClinicDayRange();
  const today = useAsync(
    () =>
      isDoctor
        ? appointmentsApi.list({
            doctorId: user?.id,
            fromTime: todayRange.from,
            toTime: todayRange.to,
            page: 0,
            size: 10,
          })
        : Promise.resolve(null),
    [isDoctor, user?.id, todayRange.from, todayRange.to],
  );

  const invoices = useAsync(
    () =>
      isPatient && user?.id
        ? billingApi.invoices.list({ patientId: user.id, page: 0, size: 5 })
        : Promise.resolve(null),
    [isPatient, user?.id],
  );

  const records = useAsync(
    () =>
      isPatient && user?.id
        ? medicalRecordsApi.list({ patientId: user.id, page: 0, size: 5 })
        : Promise.resolve(null),
    [isPatient, user?.id],
  );

  const notifications = useAsync(() => notificationsApi.list({ page: 0, size: 5 }), []);
  const unread = useAsync(() => notificationsApi.unreadCount(), []);

  const firstName = user?.fullName?.split(' ')[0] ?? user?.username;
  const roleLabel = isAdmin ? 'Administration' : isDoctor ? 'Clinician workspace' : isPatient ? 'Patient portal' : 'Care team';

  return (
    <div className="page-stack">
      <section className="hero-card">
        <div>
          <p className="eyebrow">{roleLabel}</p>
          <h2>Welcome back, {firstName}.</h2>
          <p>
            {isAdmin
              ? 'Monitor patients, appointments, revenue, and clinician activity from one secure hub.'
              : isDoctor
                ? 'Review today\u2019s clinic list, document encounters, and keep your availability current.'
                : isPatient
                  ? 'Track your appointments, medical records, invoices, and care updates in one place.'
                  : 'Track appointments and care updates from your workspace.'}
          </p>
        </div>
        <div className="hero-actions">
          <Link to="/appointments" className="primary-btn">
            {isPatient ? 'Book a visit' : "Open today's board"}
          </Link>
          {isPatient ? (
            <Link to="/billing" className="btn secondary-btn">
              Billing
            </Link>
          ) : null}
          {isDoctor ? (
            <Link to={`/doctors/${user?.id ?? ''}`} className="btn secondary-btn">
              My availability
            </Link>
          ) : null}
        </div>
      </section>

      {isOps ? (
        summary.loading && !summary.data ? (
          <LoadingState label="Loading summary…" />
        ) : summary.error ? (
          <ErrorState message={summary.error} />
        ) : summary.data ? (
          <div className="metrics-grid metrics-grid-4">
            <div className="metric-card">
              <p>Appointments</p>
              <strong>{summary.data.appointments}</strong>
            </div>
            <div className="metric-card">
              <p>Completed</p>
              <strong>{summary.data.completedAppointments}</strong>
            </div>
            <div className="metric-card">
              <p>Collected revenue</p>
              <strong>{formatCurrency(summary.data.collectedRevenue)}</strong>
            </div>
            <div className="metric-card">
              <p>Outstanding</p>
              <strong>{formatCurrency(summary.data.outstandingRevenue)}</strong>
            </div>
            <div className="metric-card">
              <p>Active patients</p>
              <strong>{summary.data.activePatients}</strong>
            </div>
            <div className="metric-card">
              <p>Doctors</p>
              <strong>{summary.data.doctors}</strong>
            </div>
          </div>
        ) : null
      ) : null}

      {isDoctor ? (
        <section className="card">
          <div className="section-header">
            <h3>Today&apos;s schedule</h3>
            <Link to="/appointments">Manage</Link>
          </div>
          {today.loading ? (
            <LoadingState />
          ) : today.error ? (
            <ErrorState message={today.error} />
          ) : today.data && today.data.content.length > 0 ? (
            <div className="stack-list">
              {today.data.content.map((appointment) => (
                <div key={appointment.appointmentId} className="list-item">
                  <div>
                    <strong>{appointment.reason}</strong>
                    <p>
                      {formatDateTime(appointment.startTime)} – {formatDateTime(appointment.endTime)}
                    </p>
                  </div>
                  <div className="muted">
                    <StatusPill status={appointment.status} />
                    <span>Patient {shortId(appointment.patientId)}</span>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <EmptyState message="No appointments booked for today." />
          )}
        </section>
      ) : null}

      {isPatient ? (
        <div className="dashboard-grid">
          <section className="card">
            <div className="section-header">
              <h3>Billing</h3>
              <Link to="/billing">View invoices</Link>
            </div>
            {invoices.loading ? (
              <LoadingState />
            ) : invoices.error ? (
              <ErrorState message={invoices.error} />
            ) : invoices.data && invoices.data.content.length > 0 ? (
              <div className="stack-list">
                {invoices.data.content.map((invoice) => (
                  <Link key={invoice.invoiceId} to={`/billing/${invoice.invoiceId}`} className="list-item">
                    <div>
                      <strong>{invoice.invoiceNumber}</strong>
                      <p>Issued {formatDateTime(invoice.issuedAt)}</p>
                    </div>
                    <div className="muted">
                      <StatusPill status={invoice.status} />
                      <span>
                        {invoice.remainingAmount > 0
                          ? `${formatCurrency(invoice.remainingAmount, invoice.currency)} due`
                          : formatCurrency(invoice.totalAmount, invoice.currency)}
                      </span>
                    </div>
                  </Link>
                ))}
              </div>
            ) : (
              <EmptyState message="No invoices yet." />
            )}
          </section>

          <section className="card">
            <div className="section-header">
              <h3>Medical records</h3>
              <Link to="/medical-records">View records</Link>
            </div>
            {records.loading ? (
              <LoadingState />
            ) : records.error ? (
              <ErrorState message={records.error} />
            ) : records.data && records.data.content.length > 0 ? (
              <div className="stack-list">
                {records.data.content.map((record) => (
                  <div key={record.recordId} className="list-item">
                    <div>
                      <strong>{record.chiefComplaint}</strong>
                      <p>
                        {formatDate(record.encounterDate)} · {record.recordType.replace(/_/g, ' ')}
                      </p>
                    </div>
                    <span className="pill">Record</span>
                  </div>
                ))}
              </div>
            ) : (
              <EmptyState message="No medical records yet." />
            )}
          </section>
        </div>
      ) : null}

      <section className="card">
        <div className="section-header">
          <h3>{isPatient ? 'Upcoming appointments' : 'Recent appointments'}</h3>
          <Link to="/appointments">View all</Link>
        </div>
        {upcoming.loading ? (
          <LoadingState />
        ) : upcoming.error ? (
          <ErrorState message={upcoming.error} />
        ) : upcoming.data && upcoming.data.content.length > 0 ? (
          <div className="stack-list">
            {upcoming.data.content.map((appointment) => (
              <div key={appointment.appointmentId} className="list-item">
                <div>
                  <strong>{appointment.reason}</strong>
                  <p>{formatDateTime(appointment.startTime)}</p>
                </div>
                <div className="muted">
                  <StatusPill status={appointment.status} />
                  <span>
                    {roles.includes('DOCTOR')
                      ? `Patient ${shortId(appointment.patientId)}`
                      : `Doctor ${shortId(appointment.doctorId)}`}
                  </span>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <EmptyState message="No appointments found yet." />
        )}
      </section>

      <section className="card">
        <div className="section-header">
          <h3>
            Notifications{' '}
            {typeof unread.data === 'number' && unread.data > 0 ? (
              <span className="pill pill-info">{unread.data} unread</span>
            ) : null}
          </h3>
          <Link to="/notifications">Open feed</Link>
        </div>
        {notifications.loading ? (
          <LoadingState />
        ) : notifications.error ? (
          <ErrorState message={notifications.error} />
        ) : notifications.data && notifications.data.content.length > 0 ? (
          <div className="stack-list">
            {notifications.data.content.map((notification) => (
              <Link key={notification.notificationId} to="/notifications" className="list-item">
                <div>
                  <strong>{notification.subject}</strong>
                  <p>{notification.message}</p>
                </div>
                <div className="muted">
                  <span>{formatDateTime(notification.createdAt)}</span>
                  {notification.readAt ? null : <span className="pill pill-info">Unread</span>}
                </div>
              </Link>
            ))}
          </div>
        ) : (
          <EmptyState message="No notifications yet." />
        )}
      </section>
    </div>
  );
}

/**
 * Today's clinic range in the browser's local zone, passed as naive date-times because the
 * services interpret appointment times in the configured clinic zone.
 */
function currentClinicDayRange(): { from: string; to: string } {
  const now = new Date();
  const tomorrow = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1);
  return { from: `${localDate(now)}T00:00:00`, to: `${localDate(tomorrow)}T00:00:00` };
}

function localDate(date: Date): string {
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}
