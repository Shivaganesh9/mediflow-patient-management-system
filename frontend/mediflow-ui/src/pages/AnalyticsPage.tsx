import React from 'react';

const metrics = [
  { label: 'Patient visits', value: '1,284' },
  { label: 'Average wait', value: '12 min' },
  { label: 'On-time rate', value: '94%' }
];

export function AnalyticsPage() {
  return (
    <div className="page-stack">
      <section className="hero-card">
        <div>
          <p className="eyebrow">Analytics</p>
          <h2>See the health of your clinic in real time.</h2>
          <p>Review visits, service effectiveness, and operational performance from one view.</p>
        </div>
        <button className="primary-btn">Export report</button>
      </section>

      <section className="card">
        <div className="section-header">
          <h3>Key metrics</h3>
          <a href="#/">Back to dashboard</a>
        </div>
        <div className="metrics-grid">
          {metrics.map((metric) => (
            <div key={metric.label} className="metric-card">
              <p>{metric.label}</p>
              <strong>{metric.value}</strong>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
