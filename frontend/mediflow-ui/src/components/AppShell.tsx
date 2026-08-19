import { NavLink, Outlet, Link, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useAsync } from '../hooks/useAsync';
import { notificationsApi } from '../api/notifications';

interface NavItem {
  to: string;
  label: string;
  roles?: string[];
}

const navigation: NavItem[] = [
  { to: '/', label: 'Dashboard' },
  { to: '/appointments', label: 'Appointments' },
  { to: '/doctors', label: 'Doctors' },
  { to: '/patients', label: 'Patients', roles: ['ADMIN', 'DOCTOR', 'NURSE', 'BILLING_STAFF', 'SUPPORT'] },
  { to: '/medical-records', label: 'Medical Records', roles: ['ADMIN', 'DOCTOR', 'NURSE', 'PATIENT'] },
  { to: '/billing', label: 'Billing', roles: ['ADMIN', 'DOCTOR', 'NURSE', 'BILLING_STAFF', 'SUPPORT', 'PATIENT'] },
  { to: '/notifications', label: 'Notifications' },
  { to: '/analytics', label: 'Analytics', roles: ['ADMIN', 'DOCTOR'] },
];

export function AppShell() {
  const { user, hasRole, logout } = useAuth();
  const location = useLocation();

  const visible = navigation.filter((item) => !item.roles || hasRole(...item.roles));
  const unread = useAsync(() => notificationsApi.unreadCount(), [location.pathname]);

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <h1>MediFlow</h1>
          <p>Patient management platform</p>
        </div>
        <nav className="nav-list" aria-label="Primary">
          {visible.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </aside>

      <div className="main-panel">
        <header className="topbar">
          <div>
            <h2>Care operations hub</h2>
            <p>Track patients, appointments, billing, and clinician activity.</p>
          </div>
          <div className="topbar-actions">
            <Link to="/notifications" className="bell-link" title="Notifications">
              <svg
                className="bell-icon"
                viewBox="0 0 24 24"
                width="20"
                height="20"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                aria-hidden="true"
              >
                <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
                <path d="M13.73 21a2 2 0 0 1-3.46 0" />
              </svg>
              {typeof unread.data === 'number' && unread.data > 0 ? (
                <span className="bell-badge">{unread.data}</span>
              ) : null}
            </Link>
            <div className="user-chip">
              <strong>{user?.fullName ?? user?.username}</strong>
              <span>{user?.roles.join(', ')}</span>
            </div>
            <button className="btn secondary-btn" type="button" onClick={() => void logout()}>
              Sign out
            </button>
          </div>
        </header>

        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}