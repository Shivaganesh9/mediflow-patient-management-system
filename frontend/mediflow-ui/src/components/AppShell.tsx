import React, { useMemo, useState } from 'react';
import { DashboardPage } from '../pages/DashboardPage';
import { PatientsPage } from '../pages/PatientsPage';
import { AppointmentsPage } from '../pages/AppointmentsPage';
import { DoctorsPage } from '../pages/DoctorsPage';
import { BillingPage } from '../pages/BillingPage';
import { NotificationsPage } from '../pages/NotificationsPage';
import { AnalyticsPage } from '../pages/AnalyticsPage';
import '../styles.css';

const navigation = [
  { id: 'dashboard', label: 'Dashboard', page: <DashboardPage /> },
  { id: 'patients', label: 'Patients', page: <PatientsPage /> },
  { id: 'appointments', label: 'Appointments', page: <AppointmentsPage /> },
  { id: 'doctors', label: 'Doctors', page: <DoctorsPage /> },
  { id: 'billing', label: 'Billing', page: <BillingPage /> },
  { id: 'notifications', label: 'Notifications', page: <NotificationsPage /> },
  { id: 'analytics', label: 'Analytics', page: <AnalyticsPage /> }
];

export function AppShell() {
  const [activeView, setActiveView] = useState('dashboard');

  const activePage = useMemo(() => navigation.find((item) => item.id === activeView)?.page ?? <DashboardPage />, [activeView]);

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <h1>MediFlow</h1>
          <p>Patient management platform</p>
        </div>
        <nav className="nav-list" aria-label="Primary">
          {navigation.map((item) => (
            <button
              key={item.id}
              className={`nav-link ${activeView === item.id ? 'active' : ''}`}
              onClick={() => setActiveView(item.id)}
              type="button"
            >
              {item.label}
            </button>
          ))}
        </nav>
      </aside>

      <div className="main-panel">
        <header className="topbar">
          <div>
            <h2>Care operations hub</h2>
            <p>Track patients, appointments, billing, and clinician activity.</p>
          </div>
          <button className="primary-btn" type="button">New case</button>
        </header>

        <main className="content">{activePage}</main>
      </div>
    </div>
  );
}
