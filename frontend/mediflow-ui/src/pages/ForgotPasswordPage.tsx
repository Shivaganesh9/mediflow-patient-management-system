import { FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { authApi } from '../api/auth';
import { ApiError } from '../types/common';

export function ForgotPasswordPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [devToken, setDevToken] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    setMessage(null);
    setDevToken(null);
    try {
      const response = await authApi.requestPasswordReset({ email });
      setMessage(
        response?.message ??
          'If an account exists for that address, a password reset link has been issued.',
      );
      // Only non-production environments expose the token; there is no mail delivery here.
      setDevToken(response?.token ?? null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Unable to request a password reset right now.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="login-page">
      <div className="login-card">
        <h2>Reset your password</h2>
        <p className="login-subtitle">
          Enter the email address on your account. The response is identical whether or not the address exists.
        </p>

        <form className="form" onSubmit={handleSubmit}>
          <label className="field">
            <span>Email address</span>
            <input
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </label>
          {message ? (
            <p className="form-notice" role="status">
              {message}
            </p>
          ) : null}
          {devToken ? (
            <p className="form-notice">
              Development mode:{' '}
              <button
                type="button"
                className="link-btn"
                onClick={() => navigate(`/reset-password?token=${encodeURIComponent(devToken)}`)}
              >
                continue to the reset form
              </button>
              .
            </p>
          ) : null}
          {error ? (
            <p className="form-error" role="alert">
              {error}
            </p>
          ) : null}
          <button className="primary-btn login-btn" type="submit" disabled={submitting}>
            {submitting ? 'Sending…' : 'Request reset'}
          </button>
        </form>

        <p className="login-links">
          <Link to="/login">Back to sign in</Link>
        </p>
      </div>
    </div>
  );
}
