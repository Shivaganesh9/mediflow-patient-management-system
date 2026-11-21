import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { DEMO_REPLAY_EVENT } from './install';
import { SEED_USERS } from './mock-api';

const DEMO_ROLES = [
  { label: 'Patient', username: SEED_USERS.PATIENT.username, password: SEED_USERS.PATIENT.password },
  { label: 'Doctor', username: SEED_USERS.DOCTOR.username, password: SEED_USERS.DOCTOR.password },
  { label: 'Admin', username: SEED_USERS.ADMIN.username, password: SEED_USERS.ADMIN.password },
];

/**
 * Persistent demo-mode indicator with one-click role sign-in and a data reset.
 *
 * Rendered only when `VITE_DEMO_MODE=true` (see `src/demo/flags.ts`). This is a convenience
 * shortcut for the seeded demo accounts; it does not change how the application authenticates or
 * authorises requests, and it is not part of production builds.
 */
export function DemoBanner() {
  const { user, status, login } = useAuth();
  const navigate = useNavigate();
  const [busyRole, setBusyRole] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    const onReplay = (event: Event) => {
      const path = (event as CustomEvent<{ path?: string }>).detail?.path;
      setNotice(
        `Payment replayed${path ? ` (${path})` : ''} — the Idempotency-Key returned the original payment, so no duplicate charge was created.`,
      );
    };
    window.addEventListener(DEMO_REPLAY_EVENT, onReplay);
    return () => window.removeEventListener(DEMO_REPLAY_EVENT, onReplay);
  }, []);

  useEffect(() => {
    if (!notice) return;
    const timer = window.setTimeout(() => setNotice(null), 10_000);
    return () => window.clearTimeout(timer);
  }, [notice]);

  const signInAs = async (username: string, password: string, label: string) => {
    setBusyRole(label);
    setNotice(null);
    try {
      await login(username, password);
      navigate('/');
    } catch {
      setNotice(`Could not sign in as ${label}. Reset the demo data and try again.`);
    } finally {
      setBusyRole(null);
    }
  };

  const reset = () => {
    // Tokens and cached profile are the only demo state kept outside the page, so clearing them
    // and reloading rebuilds the seeded world.
    window.localStorage.clear();
    window.location.assign('/');
  };

  return (
    <div className="demo-banner" role="region" aria-label="Demo mode">
      <div className="demo-banner__main">
        <span className="demo-banner__badge">Demo mode</span>
        <span className="demo-banner__text">
          Simulated data — no backend, no real patient records, changes are discarded on reload.
        </span>
      </div>
      <div className="demo-banner__actions">
        <span className="demo-banner__hint">Sign in as</span>
        {DEMO_ROLES.map((role) => (
          <button
            key={role.label}
            type="button"
            className="demo-banner__role"
            disabled={busyRole !== null}
            onClick={() => void signInAs(role.username, role.password, role.label)}
          >
            {busyRole === role.label ? `Signing in as ${role.label}…` : role.label}
          </button>
        ))}
        {status === 'authenticated' && user ? (
          <span className="demo-banner__session">{user.fullName ?? user.username}</span>
        ) : null}
        <button type="button" className="demo-banner__reset" onClick={reset}>
          Reset demo data
        </button>
      </div>
      {notice ? (
        <p className="demo-banner__notice" role="status">
          {notice}
        </p>
      ) : null}
    </div>
  );
}
