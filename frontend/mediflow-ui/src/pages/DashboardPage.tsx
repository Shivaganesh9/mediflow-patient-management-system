import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useAsync } from '../hooks/useAsync';
import { analyticsApi } from '../api/analytics';
import { appointmentsApi } from '../api/appointments';
import { StatusPill } from '../components/StatusPill';
import { LoadingState, ErrorState, EmptyState } from '../components/PageState';
import { formatCurrency, formatDateTime } from '../utils/format';

export function DashboardPage() {
  const { user, roles, hasRole } = useAuth();
  const isOps = hasRole('ADMIN', 'DOCTOR');

  const summary = useAsync(() => analyticsApi.dashboardSummary(), []);
  const scope = {
    patientId: roles.includes('PATIENT') ? user?.id : undefined,
    doctorId: roles.includes('DOCTOR') ? user?.id : undefined,
  };
  const upcoming = useAsync(
    () => appointmentsApi.list({ ...scope, page: 0, size: 5 }),
    [scope.patientId, scope.doctorId],
  );

  const firstName = user?.fullName?.split(' ')[0] ?? user?.username;

  return (
    <div className="page-stack">
      <section className="hero-card">
        <div>
          <p className="eyebrow">Operations overview</p>
          <h2>Welcome back, {firstName}.</h2>
          <p>
            {isOps
              ? 'Monitor patients, appointments, revenue, and clinician activity from one secure hub.'
              : 'Track your appointments and stay on top of care updates from your workspace.'}
          </p>
        </div>
        <Link to="/appointments" className="primary-btn">
          Open today&apos;s board
        </Link>
      </section>

      {isOps ? (
        summary.loading ? (
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

      <section className="card">
        <div className="section-header">
          <h3>Recent appointments</h3>
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
                    {roles.includes('DOCTOR') ? 'Your patient' : `Patient ${appointment.patientId.slice(0, 8)}`}
                  </span>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <EmptyState message="No appointments found yet." />
        )}
      </section>
    </div>
  );
}