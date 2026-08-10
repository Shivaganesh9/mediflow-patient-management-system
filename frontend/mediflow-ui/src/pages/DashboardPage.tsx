import React from 'react';

const highlights = [
  'Secure authentication and session workflows',
  'Patient, appointment, and doctor management',
  'Billing and notification coordination'
];

export function DashboardPage() {
  return (
    <div className="page-stack">
      <section className="hero-card">
        <div>
          <p className="eyebrow">Operations overview</p>
          <h2>Care coordination that feels calm, fast, and clinical.</h2>
          <p>Monitor patients, appointments, providers, billing, and insights from one secure hub.</p>
        </div>
        <button className="primary-btn">Open today’s board</button>
      </section>

      <div className="dashboard-grid">
        <section className="card">
          <div className="section-header">
            <h3>What’s on deck</h3>
            <a href="#/analytics">View analytics</a>
          </div>
          <div className="stack-list">
            <div className="list-item">
              <div>
                <strong>12 appointments today</strong>
                <p>3 are waiting for clinician confirmation.</p>
              </div>
              <span className="pill">Priority</span>
            </div>
            <div className="list-item">
              <div>
                <strong>4 patient records updated</strong>
                <p>New notes were added this morning.</p>
              </div>
              <span className="pill">Live</span>
            </div>
          </div>
        </section>

        <section className="card small-card">
          <h3>Platform capabilities</h3>
          <ul>
            {highlights.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        </section>
      </div>
    </div>
  );
}
