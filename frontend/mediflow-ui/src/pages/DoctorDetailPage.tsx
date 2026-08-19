import { FormEvent, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useAsync } from '../hooks/useAsync';
import { doctorsApi } from '../api/doctors';
import { AddAvailabilityRequest, AddLeaveRequest } from '../types/doctor';
import { ApiError } from '../types/common';
import { LoadingState, ErrorState, EmptyState } from '../components/PageState';
import { Modal } from '../components/Modal';
import { displayName, formatCurrency, formatDate, formatLocalTime } from '../utils/format';

const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

export function DoctorDetailPage() {
  const { doctorId = '' } = useParams();
  const { hasRole } = useAuth();
  const [showAvailability, setShowAvailability] = useState(false);
  const [showLeave, setShowLeave] = useState(false);

  const canManage = hasRole('ADMIN', 'DOCTOR', 'NURSE', 'BILLING_STAFF', 'SUPPORT');

  const detail = useAsync(() => doctorsApi.get(doctorId), [doctorId]);
  const availability = useAsync(() => doctorsApi.availability.list(doctorId), [doctorId]);
  const leaves = useAsync(() => doctorsApi.leaves.list(doctorId), [doctorId]);

  if (detail.loading) return <LoadingState />;
  if (detail.error) return <ErrorState message={detail.error} />;
  if (!detail.data) return <EmptyState message="Doctor not found." />;

  const doctor = detail.data;

  return (
    <div className="page-stack">
      <section className="hero-card">
        <div>
          <p className="eyebrow">Doctor profile</p>
          <h2>Dr. {displayName(doctor.firstName, doctor.lastName)}</h2>
          <p>
            {doctor.specialty} · {doctor.department}
          </p>
        </div>
        <div className="hero-actions">
          {canManage ? (
            <>
              <button className="primary-btn" type="button" onClick={() => setShowAvailability(true)}>
                Add availability
              </button>
              <button className="btn secondary-btn" type="button" onClick={() => setShowLeave(true)}>
                Add leave
              </button>
            </>
          ) : null}
          <Link to="/doctors" className="btn secondary-btn">
            Back
          </Link>
        </div>
      </section>

      <div className="dashboard-grid">
        <section className="card">
          <div className="section-header">
            <h3>Details</h3>
          </div>
          <dl className="detail-grid">
            <div>
              <dt>Doctor ID</dt>
              <dd>{doctor.doctorId}</dd>
            </div>
            <div>
              <dt>Email</dt>
              <dd>{doctor.email}</dd>
            </div>
            <div>
              <dt>Phone</dt>
              <dd>{doctor.phone}</dd>
            </div>
            <div>
              <dt>Qualification</dt>
              <dd>{doctor.qualification ?? '—'}</dd>
            </div>
            <div>
              <dt>Experience</dt>
              <dd>{doctor.yearsOfExperience} years</dd>
            </div>
            <div>
              <dt>Consultation fee</dt>
              <dd>{formatCurrency(doctor.consultationFee)}</dd>
            </div>
          </dl>
        </section>

        <section className="card">
          <div className="section-header">
            <h3>Weekly availability</h3>
          </div>
          {availability.loading ? (
            <LoadingState />
          ) : availability.error ? (
            <ErrorState message={availability.error} />
          ) : availability.data && availability.data.length > 0 ? (
            <div className="stack-list">
              {availability.data.map((slot) => (
                <div key={slot.availabilityId} className="list-item">
                  <div>
                    <strong>{slot.dayOfWeek}</strong>
                  </div>
                  <div className="muted">
                    <span>
                      {formatLocalTime(slot.startTime)} – {formatLocalTime(slot.endTime)}
                    </span>
                    <span className="pill">{slot.active ? 'Active' : 'Inactive'}</span>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <EmptyState message="No availability set yet." />
          )}
        </section>
      </div>

      <section className="card">
        <div className="section-header">
          <h3>Leaves</h3>
        </div>
        {leaves.loading ? (
          <LoadingState />
        ) : leaves.error ? (
          <ErrorState message={leaves.error} />
        ) : leaves.data && leaves.data.length > 0 ? (
          <div className="stack-list">
            {leaves.data.map((leave) => (
              <div key={leave.leaveId} className="list-item">
                <div>
                  <strong>
                    {formatDate(leave.startDate)} – {formatDate(leave.endDate)}
                  </strong>
                  {leave.reason ? <p>{leave.reason}</p> : null}
                </div>
                <span className="pill pill-warning">Leave</span>
              </div>
            ))}
          </div>
        ) : (
          <EmptyState message="No leaves recorded." />
        )}
      </section>

      {showAvailability && canManage ? (
        <AddAvailabilityModal
          onClose={() => setShowAvailability(false)}
          onSaved={() => {
            setShowAvailability(false);
            availability.reload();
          }}
        />
      ) : null}
      {showLeave && canManage ? (
        <AddLeaveModal
          onClose={() => setShowLeave(false)}
          onSaved={() => {
            setShowLeave(false);
            leaves.reload();
          }}
        />
      ) : null}
    </div>
  );
}

function AddAvailabilityModal({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const { doctorId = '' } = useParams();
  const [form, setForm] = useState<AddAvailabilityRequest>({ dayOfWeek: 'MONDAY', startTime: '09:00', endTime: '17:00' });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await doctorsApi.availability.create(doctorId, {
        dayOfWeek: form.dayOfWeek,
        startTime: `${form.startTime}:00`,
        endTime: `${form.endTime}:00`,
      });
      onSaved();
    } catch (err) {
      if (err instanceof ApiError) setError(err.message);
      else setError('Unable to add availability.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal title="Add availability" onClose={onClose}>
      <form className="form" onSubmit={handleSubmit}>
        <label className="field">
          <span>Day of week *</span>
          <select value={form.dayOfWeek} onChange={(e) => setForm((f) => ({ ...f, dayOfWeek: e.target.value }))}>
            {DAYS.map((day) => (
              <option key={day} value={day}>
                {day}
              </option>
            ))}
          </select>
        </label>
        <div className="form-grid">
          <label className="field">
            <span>Start time *</span>
            <input
              type="time"
              required
              value={form.startTime}
              onChange={(e) => setForm((f) => ({ ...f, startTime: e.target.value }))}
            />
          </label>
          <label className="field">
            <span>End time *</span>
            <input
              type="time"
              required
              value={form.endTime}
              onChange={(e) => setForm((f) => ({ ...f, endTime: e.target.value }))}
            />
          </label>
        </div>
        {error ? <p className="form-error">{error}</p> : null}
        <div className="form-actions">
          <button type="button" className="btn secondary-btn" onClick={onClose}>
            Cancel
          </button>
          <button className="primary-btn" type="submit" disabled={submitting}>
            {submitting ? 'Saving…' : 'Add slot'}
          </button>
        </div>
      </form>
    </Modal>
  );
}

function AddLeaveModal({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const { doctorId = '' } = useParams();
  const [form, setForm] = useState<AddLeaveRequest>({ startDate: '', endDate: '', reason: '' });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await doctorsApi.leaves.create(doctorId, form);
      onSaved();
    } catch (err) {
      if (err instanceof ApiError) setError(err.message);
      else setError('Unable to add leave.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal title="Add leave" onClose={onClose}>
      <form className="form" onSubmit={handleSubmit}>
        <div className="form-grid">
          <label className="field">
            <span>Start date *</span>
            <input
              type="date"
              required
              value={form.startDate}
              onChange={(e) => setForm((f) => ({ ...f, startDate: e.target.value }))}
            />
          </label>
          <label className="field">
            <span>End date *</span>
            <input
              type="date"
              required
              value={form.endDate}
              onChange={(e) => setForm((f) => ({ ...f, endDate: e.target.value }))}
            />
          </label>
        </div>
        <label className="field">
          <span>Reason</span>
          <input value={form.reason ?? ''} onChange={(e) => setForm((f) => ({ ...f, reason: e.target.value }))} />
        </label>
        {error ? <p className="form-error">{error}</p> : null}
        <div className="form-actions">
          <button type="button" className="btn secondary-btn" onClick={onClose}>
            Cancel
          </button>
          <button className="primary-btn" type="submit" disabled={submitting}>
            {submitting ? 'Saving…' : 'Add leave'}
          </button>
        </div>
      </form>
    </Modal>
  );
}