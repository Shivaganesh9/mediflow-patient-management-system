import { FormEvent, useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { ApiError } from '../types/common';

export function LoginPage() {
  const { status, login } = useAuth();
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
      if (err instanceof ApiError) setError(err.message);
      else setError('Unable to sign in. Please try again.');
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

        <form className="form" onSubmit={handleSubmit}>
          <label className="field">
            <span>Username or email</span>
            <input
              type="text"
              autoComplete="username"
              value={usernameOrEmail}
              onChange={(e) => setUsernameOrEmail(e.target.value)}
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
          {error ? <p className="form-error">{error}</p> : null}
          <button className="primary-btn login-btn" type="submit" disabled={submitting}>
            {submitting ? 'Signing in…' : 'Sign in'}
          </button>
        </form>

        <p className="login-hint">
          Sign in with a user created by an admin or registered through the auth service.
        </p>
      </div>
    </div>
  );
}