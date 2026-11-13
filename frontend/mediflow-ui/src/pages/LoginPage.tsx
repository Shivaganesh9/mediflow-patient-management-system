import { FormEvent, useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { ApiError } from '../types/common';

export function LoginPage() {
  const { status, login, sessionNotice, setSessionNotice } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [usernameOrEmail, setUsernameOrEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const from = (location.state as { from?: string } | null)?.from ?? '/';

  if (status === 'authenticated') return <Navigate to="/" replace />;

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await login(usernameOrEmail, password);
      navigate(from, { replace: true });
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.status === 403) {
          // Only returned after a correct password, so it is safe to show the status.
          setError(`${err.message} If this is unexpected, contact an administrator.`);
        } else if (err.status === 429) {
          setError(err.message || 'Too many sign-in attempts. Please wait and try again.');
        } else {
          setError(err.message);
        }
      } else {
        setError('Unable to sign in. Please try again.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="login-page">
      <div className="login-card">
        <div className="brand brand-login">
          <h1>MediFlow</h1>
          <p>Patient management platform</p>
        </div>
        <h2>Sign in to your workspace</h2>
        <p className="login-subtitle">Use your MediFlow credentials to continue.</p>

        {sessionNotice ? (
          <p className="form-notice" role="status">
            {sessionNotice}
          </p>
        ) : null}

        <form className="form" onSubmit={handleSubmit}>
          <label className="field">
            <span>Username or email</span>
            <input
              type="text"
              autoComplete="username"
              value={usernameOrEmail}
              onChange={(e) => {
                setUsernameOrEmail(e.target.value);
                if (sessionNotice) setSessionNotice(null);
              }}
              required
            />
          </label>
          <label className="field">
            <span>Password</span>
            <input
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </label>
          {error ? (
            <p className="form-error" role="alert">
              {error}
            </p>
          ) : null}
          <button className="primary-btn login-btn" type="submit" disabled={submitting}>
            {submitting ? 'Signing in…' : 'Sign in'}
          </button>
        </form>

        <p className="login-links">
          <Link to="/forgot-password">Forgot your password?</Link>
        </p>

        <p className="login-hint">
          Sign in with a user created by an admin or registered through the auth service.
        </p>
      </div>
    </div>
  );
}
