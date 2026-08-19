import { useState } from 'react';
import { useAsync } from '../hooks/useAsync';
import { analyticsApi, AnalyticsRange } from '../api/analytics';
import { LoadingState, ErrorState, EmptyState } from '../components/PageState';
import { BarChart, MultiBarChart } from '../components/Charts';
import { StatusPill } from '../components/StatusPill';
import { formatCurrency } from '../utils/format';

export function AnalyticsPage() {
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');

  const range: AnalyticsRange = { from: from || undefined, to: to || undefined };
  const rangeKey = `${from}|${to}`;

  const summary = useAsync(() => analyticsApi.dashboardSummary(range), [rangeKey]);
  const appointments = useAsync(() => analyticsApi.appointments(range), [rangeKey]);
  const revenue = useAsync(() => analyticsApi.revenue(range), [rangeKey]);
  const doctors = useAsync(() => analyticsApi.doctors(range), [rangeKey]);
  const patients = useAsync(() => analyticsApi.patients(range), [rangeKey]);

  return (
    <div className="page-stack">
      <section className="hero-card">
        <div>
          <p className="eyebrow">Analytics</p>
          <h2>See the health of your clinic in real time.</h2>
          <p>Review appointments, revenue, and clinician activity across your practice.</p>
        </div>
        <div className="toolbar">
          <input type="date" aria-label="From" value={from} onChange={(e) => setFrom(e.target.value)} />
          <input type="date" aria-label="To" value={to} onChange={(e) => setTo(e.target.value)} />
        </div>
      </section>

      {summary.loading ? (
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
            <p>Cancelled</p>
            <strong>{summary.data.cancelledAppointments}</strong>
          </div>
          <div className="metric-card">
            <p>Invoiced</p>
            <strong>{formatCurrency(summary.data.invoicedRevenue)}</strong>
          </div>
          <div className="metric-card">
            <p>Collected</p>
            <strong>{formatCurrency(summary.data.collectedRevenue)}</strong>
          </div>
          <div className="metric-card">
            <p>Refunded</p>
            <strong>{formatCurrency(summary.data.refundedRevenue)}</strong>
          </div>
          <div className="metric-card">
            <p>Outstanding</p>
            <strong>{formatCurrency(summary.data.outstandingRevenue)}</strong>
          </div>
          <div className="metric-card">
            <p>Active patients</p>
            <strong>{summary.data.activePatients}</strong>
          </div>
        </div>
      ) : null}

      <div className="dashboard-grid">
        <section className="card">
          <div className="section-header">
            <h3>Appointment status breakdown</h3>
          </div>
          {appointments.loading ? (
            <LoadingState />
          ) : appointments.error ? (
            <ErrorState message={appointments.error} />
          ) : appointments.data ? (
            <div className="status-breakdown">
              {appointments.data.statusBreakdown.map((entry) => (
                <span key={entry.status}>
                  <StatusPill status={entry.status} /> <strong>{entry.count}</strong>
                </span>
              ))}
            </div>
          ) : null}
        </section>

        <section className="card">
          <div className="section-header">
            <h3>Daily appointment trend</h3>
          </div>
          {appointments.loading ? (
            <LoadingState />
          ) : appointments.error ? (
            <ErrorState message={appointments.error} />
          ) : appointments.data && appointments.data.dailyTrend.length > 0 ? (
            <BarChart
              data={appointments.data.dailyTrend.map((d) => ({ label: d.date.slice(5), value: d.count }))}
            />
          ) : (
            <EmptyState message="No appointment data for the selected range." />
          )}
        </section>
      </div>

      <section className="card">
        <div className="section-header">
          <h3>Monthly revenue</h3>
        </div>
        {revenue.loading ? (
          <LoadingState />
        ) : revenue.error ? (
          <ErrorState message={revenue.error} />
        ) : revenue.data && revenue.data.monthlyTrend.length > 0 ? (
          <MultiBarChart
            labels={revenue.data.monthlyTrend.map((m) => m.month)}
            series={[
              { name: 'Invoiced', values: revenue.data.monthlyTrend.map((m) => m.invoiced), color: '#1f78ff' },
              { name: 'Collected', values: revenue.data.monthlyTrend.map((m) => m.collected), color: '#27ae60' },
              { name: 'Refunded', values: revenue.data.monthlyTrend.map((m) => m.refunded), color: '#e74c3c' },
            ]}
          />
        ) : (
          <EmptyState message="No revenue data for the selected range." />
        )}
      </section>

      <div className="dashboard-grid">
        <section className="card">
          <div className="section-header">
            <h3>Doctors by appointment volume</h3>
          </div>
          {doctors.loading ? (
            <LoadingState />
          ) : doctors.error ? (
            <ErrorState message={doctors.error} />
          ) : doctors.data && doctors.data.length > 0 ? (
            <BarChart
              data={doctors.data.map((d) => ({ label: d.doctorId.slice(0, 6), value: d.appointments }))}
            />
          ) : (
            <EmptyState message="No doctor activity for the selected range." />
          )}
        </section>

        <section className="card">
          <div className="section-header">
            <h3>Most active patients</h3>
          </div>
          {patients.loading ? (
            <LoadingState />
          ) : patients.error ? (
            <ErrorState message={patients.error} />
          ) : patients.data && patients.data.mostActive.length > 0 ? (
            <div className="stack-list">
              {patients.data.mostActive.map((p) => (
                <div key={p.patientId} className="list-item">
                  <div>
                    <strong>{p.patientId.slice(0, 12)}</strong>
                  </div>
                  <span className="muted">{p.appointments} appointments</span>
                </div>
              ))}
            </div>
          ) : (
            <EmptyState message="No patient activity for the selected range." />
          )}
        </section>
      </div>
    </div>
  );
}