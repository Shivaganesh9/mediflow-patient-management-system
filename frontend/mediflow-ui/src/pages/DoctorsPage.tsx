import React from 'react';

const doctors = [
  { name: 'Dr. Morgan', specialty: 'Cardiology', availability: 'Available now' },
  { name: 'Dr. Chen', specialty: 'Neurology', availability: 'In clinic' },
  { name: 'Dr. Rivera', specialty: 'Primary care', availability: 'On call' }
];

export function DoctorsPage() {
  return (
    <div className="page-stack">
      <section className="hero-card">
        <div>
          <p className="eyebrow">Care team</p>
          <h2>Support clinical staff with a clear operating view.</h2>
          <p>Review provider capacity, specialities, and current availability at a glance.</p>
        </div>
        <button className="primary-btn">Invite provider</button>
      </section>

      <section className="card">
        <div className="section-header">
          <h3>Providers</h3>
          <a href="#/billing">Billing overview</a>
        </div>
        <div className="stack-list">
          {doctors.map((doctor) => (
            <div key={doctor.name} className="list-item">
              <div>
                <strong>{doctor.name}</strong>
                <p>{doctor.specialty}</p>
              </div>
              <span className="pill">{doctor.availability}</span>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
