import React from 'react';

const patients = [
  { name: 'Ava Patel', id: 'P-1024', status: 'Stable', lastVisit: 'Today' },
  { name: 'Noah Kim', id: 'P-1025', status: 'Follow-up', lastVisit: 'Yesterday' },
  { name: 'Mina Alvarez', id: 'P-1026', status: 'Needs review', lastVisit: '2 days ago' }
];

export function PatientsPage() {
  return (
    <div className="page-stack">
      <section className="hero-card">
        <div>
          <p className="eyebrow">Patient centre</p>
          <h2>Keep patient records organized and actionable.</h2>
          <p>View recent patients, monitor status, and coordinate care from a single workspace.</p>
        </div>
        <button className="primary-btn">Add patient</button>
      </section>

      <section className="card">
        <div className="section-header">
          <h3>Recent patients</h3>
          <a href="#/appointments">View schedule</a>
        </div>
        <table className="table">
          <thead>
            <tr>
              <th>Patient</th>
              <th>ID</th>
              <th>Status</th>
              <th>Last visit</th>
            </tr>
          </thead>
          <tbody>
            {patients.map((patient) => (
              <tr key={patient.id}>
                <td>{patient.name}</td>
                <td>{patient.id}</td>
                <td><span className="pill">{patient.status}</span></td>
                <td>{patient.lastVisit}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </div>
  );
}
