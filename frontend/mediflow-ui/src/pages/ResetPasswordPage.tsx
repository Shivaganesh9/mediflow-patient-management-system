import { FormEvent, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { authApi } from '../api/auth';
import { useAuth } from '../context/AuthContext';
import { ApiError } from '../types/common';

export function ResetPasswordPage() {
  const navigate = useNavigate();
  const { setSessionNotice } = useAuth();
  const [searchParams] = useSearchParams();
  const [token, setToken] = useState(searchParams.get('token') ?? '');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);

    if (newPassword !== confirmPassword) {
      setError('The new password and confirmation do not match.');
      return;
    }

    setSubmitting(true);
    try {
      await authApi.confirmPasswordReset({ token: token.trim(), newPassword });
      setSessionNotice('Password reset successfully. Please sign in with your new password.');
      navigate('/login', { replace: true });
    } catch (err) {
      // Invalid, expired and already-used tokens share one generic message.
      setError(err instanceof ApiError ? err.message : 'Unable to reset the password. Please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="login-page">
      <div className="login-card">
        <h2>Choose a new password</h2>
        <p className="login-subtitle">Reset tokens are single use and expire after 30 minutes.</p>

        <form className="form" onSubmit={handleSubmit}>
          <label className="field">
            <span>Reset token</span>
            <input
              type="text"
              autoComplete="one-time-code"
              value={token}
              onChange={(e) => setToken(e.target.value)}
              required
            />
            <small>Paste the token from your reset email, or open the link that contains it.</small>
          </label>
          <label className="field">
            <span>New password</span>
            <input
              type="password"
              autoComplete="new-password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              required
            />
            <small>At least 8 characters, including both letters and digits.</small>
          </label>
          <label className="field">
            <span>Confirm new password</span>
            <input
              type="password"
              autoComplete="new-password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              required
            />
          </label>
          {error ? (
            <p className="form-error" role="alert">
              {error}
            </p>
          ) : null}
          <button className="primary-btn login-btn" type="submit" disabled={submitting}>
            {submitting ? 'Resetting…' : 'Reset password'}
          </button>
        </form>

        <p className="login-links">
          <Link to="/login">Back to sign in</Link>
        </p>
      </div>
    </div>
  );
}
