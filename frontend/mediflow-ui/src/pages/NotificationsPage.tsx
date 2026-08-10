import React from 'react';

const alerts = [
  { message: 'Lab results uploaded for Ava Patel', time: '5 min ago' },
  { message: 'Appointment reminder sent to Noah Kim', time: '18 min ago' },
  { message: 'Invoice pending review for Mina Alvarez', time: '1 hr ago' }
];

export function NotificationsPage() {
  return (
    <div className="page-stack">
      <section className="hero-card">
        <div>
          <p className="eyebrow">Notifications</p>
          <h2>Stay informed on every care event.</h2>
          <p>Track updates from labs, appointments, billing, and operational workflows.</p>
        </div>
        <button className="primary-btn">Compose alert</button>
      </section>

      <section className="card">
        <div className="section-header">
          <h3>Activity feed</h3>
          <a href="#/analytics">Analytics</a>
        </div>
        <div className="stack-list">
          {alerts.map((alert) => (
            <div key={alert.message} className="list-item">
              <div>
                <strong>{alert.message}</strong>
              </div>
              <span className="muted">{alert.time}</span>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
