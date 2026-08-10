import React from 'react';

const appointments = [
  { title: 'Cardiology consult', patient: 'Ava Patel', time: '09:30', doctor: 'Dr. Morgan' },
  { title: 'Lab review', patient: 'Noah Kim', time: '11:00', doctor: 'Dr. Chen' },
  { title: 'Follow-up', patient: 'Mina Alvarez', time: '14:15', doctor: 'Dr. Rivera' }
];

export function AppointmentsPage() {
  return (
    <div className="page-stack">
      <section className="hero-card">
        <div>
          <p className="eyebrow">Appointments</p>
          <h2>Coordinate every visit with confidence.</h2>
          <p>Track upcoming consultations, room readiness, and follow-up care in one timeline.</p>
        </div>
        <button className="primary-btn">Schedule visit</button>
      </section>

      <section className="card">
        <div className="section-header">
          <h3>Today’s schedule</h3>
          <a href="#/doctors">View doctors</a>
        </div>
        <div className="stack-list">
          {appointments.map((appointment) => (
            <div key={appointment.time} className="list-item">
              <div>
                <strong>{appointment.title}</strong>
                <p>{appointment.patient}</p>
              </div>
              <div className="muted">
                <span>{appointment.time}</span>
                <span>{appointment.doctor}</span>
              </div>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
