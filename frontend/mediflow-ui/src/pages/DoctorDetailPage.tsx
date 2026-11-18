import { FormEvent, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useAsync } from '../hooks/useAsync';
import { doctorsApi } from '../api/doctors';
import {
  AddAvailabilityRequest,
  AddLeaveRequest,
  DoctorAvailability,
  DoctorLeave,
} from '../types/doctor';
import { ApiError } from '../types/common';
import { LoadingState, ErrorState, EmptyState } from '../components/PageState';
import { Modal } from '../components/Modal';
import { displayName, formatCurrency, formatDate, formatLocalTime } from '../utils/format';

const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

export function DoctorDetailPage() {
  const { doctorId = '' } = useParams();
  const { user, hasRole } = useAuth();
  const [showAvailability, setShowAvailability] = useState(false);
  const [editingAvailability, setEditingAvailability] = useState<DoctorAvailability | null>(null);
  const [showLeave, setShowLeave] = useState(false);
  const [editingLeave, setEditingLeave] = useState<DoctorLeave | null>(null);
  const [busyKey, setBusyKey] = useState<string | null>(null);

  const detail = useAsync(() => doctorsApi.get(doctorId), [doctorId]);
  const availability = useAsync(() => doctorsApi.availability.list(doctorId), [doctorId]);
  const leaves = useAsync(() => doctorsApi.leaves.list(doctorId), [doctorId]);

  if (detail.loading) return <LoadingState />;
  if (detail.error) return <ErrorState message={detail.error} />;
  if (!detail.data) return <EmptyState message="Doctor not found." />;

  const doctor = detail.data;
  // Admins manage any schedule; a doctor manages only their own profile (the service enforces
  // this too, the UI just avoids offering actions that would be rejected).
  const canManage = hasRole('ADMIN') || (hasRole('DOCTOR') && user?.id === doctor.doctorId);

  const deactivateAvailability = async (slot: DoctorAvailability) => {
    if (!window.confirm(`Deactivate ${slot.dayOfWeek} ${formatLocalTime(slot.startTime)}–${formatLocalTime(slot.endTime)}?`)) {
      return;
    }
    setBusyKey(`availability:${slot.availabilityId}`);
    try {
      await doctorsApi.availability.deactivate(doctorId, slot.availabilityId);
      availability.reload();
    } catch (err) {
      window.alert(errorMessage(err, 'Unable to deactivate availability.'));
    } finally {
      setBusyKey(null);
    }
  };

  const cancelLeave = async (leave: DoctorLeave) => {
    if (!window.confirm(`Cancel leave ${formatDate(leave.startDate)} – ${formatDate(leave.endDate)}?`)) {
      return;
    }
    setBusyKey(`leave:${leave.leaveId}`);
    try {
      await doctorsApi.leaves.cancel(doctorId, leave.leaveId);
      leaves.reload();
    } catch (err) {
      window.alert(errorMessage(err, 'Unable to cancel leave.'));
    } finally {
      setBusyKey(null);
    }
  };

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
            <>
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
                      {canManage ? (
                        <div className="row-actions">
                          <button
                            className="btn small-btn"
                            type="button"
                            disabled={busyKey !== null}
                            onClick={() => setEditingAvailability(slot)}
                          >
                            Edit
                          </button>
                          <button
                            className="btn small-btn"
                            type="button"
                            disabled={busyKey !== null}
                            onClick={() => void deactivateAvailability(slot)}
                          >
                            {busyKey === `availability:${slot.availabilityId}` ? '…' : 'Deactivate'}
                          </button>
                        </div>
                      ) : null}
                    </div>
                  </div>
                ))}
              </div>
              <p className="form-hint">
                Only active windows are listed. Deactivated windows stop accepting new bookings;
                existing appointments are unaffected.
              </p>
            </>
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
                <div className="muted">
                  <span className="pill pill-warning">Leave</span>
                  {canManage ? (
                    <div className="row-actions">
                      <button
                        className="btn small-btn"
                        type="button"
                        disabled={busyKey !== null}
                        onClick={() => setEditingLeave(leave)}
                      >
                        Edit
                      </button>
                      <button
                        className="btn small-btn"
                        type="button"
                        disabled={busyKey !== null}
                        onClick={() => void cancelLeave(leave)}
                      >
                        {busyKey === `leave:${leave.leaveId}` ? '…' : 'Cancel leave'}
                      </button>
                    </div>
                  ) : null}
                </div>
              </div>
            ))}
          </div>
        ) : (
          <EmptyState message="No leaves recorded." />
        )}
      </section>

      {showAvailability && canManage ? (
        <AvailabilityModal
          doctorId={doctorId}
          onClose={() => setShowAvailability(false)}
          onSaved={() => {
            setShowAvailability(false);
            availability.reload();
          }}
        />
      ) : null}
      {editingAvailability && canManage ? (
        <AvailabilityModal
          doctorId={doctorId}
          slot={editingAvailability}
          onClose={() => setEditingAvailability(null)}
          onSaved={() => {
            setEditingAvailability(null);
            availability.reload();
          }}
        />
      ) : null}
      {showLeave && canManage ? (
        <LeaveModal
          doctorId={doctorId}
          onClose={() => setShowLeave(false)}
          onSaved={() => {
            setShowLeave(false);
            leaves.reload();
          }}
        />
      ) : null}
      {editingLeave && canManage ? (
        <LeaveModal
          doctorId={doctorId}
          leave={editingLeave}
          onClose={() => setEditingLeave(null)}
          onSaved={() => {
            setEditingLeave(null);
            leaves.reload();
          }}
        />
      ) : null}
    </div>
  );
}

function AvailabilityModal({
  doctorId,
  slot,
  onClose,
  onSaved,
}: {
  doctorId: string;
  slot?: DoctorAvailability;
  onClose: () => void;
  onSaved: () => void;
}) {
  const editing = !!slot;
  const [form, setForm] = useState<AddAvailabilityRequest>({
    dayOfWeek: slot?.dayOfWeek ?? 'MONDAY',
    startTime: slot ? slot.startTime.slice(0, 5) : '09:00',
    endTime: slot ? slot.endTime.slice(0, 5) : '17:00',
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    const body = {
      dayOfWeek: form.dayOfWeek,
      startTime: `${form.startTime}:00`,
      endTime: `${form.endTime}:00`,
    };
    try {
      if (slot) {
        await doctorsApi.availability.update(doctorId, slot.availabilityId, { ...body, active: true });
      } else {
        await doctorsApi.availability.create(doctorId, body);
      }
      onSaved();
    } catch (err) {
      setError(errorMessage(err, editing ? 'Unable to update availability.' : 'Unable to add availability.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal title={editing ? 'Edit availability' : 'Add availability'} onClose={onClose}>
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
        <p className="form-hint">
          A booking must fit entirely inside one window. Windows on the same day cannot overlap.
        </p>
        {error ? <p className="form-error">{error}</p> : null}
        <div className="form-actions">
          <button type="button" className="btn secondary-btn" onClick={onClose}>
            Cancel
          </button>
          <button className="primary-btn" type="submit" disabled={submitting}>
            {submitting ? 'Saving…' : editing ? 'Save changes' : 'Add slot'}
          </button>
        </div>
      </form>
    </Modal>
  );
}

function LeaveModal({
  doctorId,
  leave,
  onClose,
  onSaved,
}: {
  doctorId: string;
  leave?: DoctorLeave;
  onClose: () => void;
  onSaved: () => void;
}) {
  const editing = !!leave;
  const [form, setForm] = useState<AddLeaveRequest>({
    startDate: leave?.startDate ?? '',
    endDate: leave?.endDate ?? '',
    reason: leave?.reason ?? '',
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      if (leave) {
        await doctorsApi.leaves.update(doctorId, leave.leaveId, form);
      } else {
        await doctorsApi.leaves.create(doctorId, form);
      }
      onSaved();
    } catch (err) {
      setError(errorMessage(err, editing ? 'Unable to update leave.' : 'Unable to add leave.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal title={editing ? 'Edit leave' : 'Add leave'} onClose={onClose}>
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
        <p className="form-hint">Approved leave blocks new bookings for every day in the range.</p>
        {error ? <p className="form-error">{error}</p> : null}
        <div className="form-actions">
          <button type="button" className="btn secondary-btn" onClick={onClose}>
            Cancel
          </button>
          <button className="primary-btn" type="submit" disabled={submitting}>
            {submitting ? 'Saving…' : editing ? 'Save changes' : 'Add leave'}
          </button>
        </div>
      </form>
    </Modal>
  );
}

function errorMessage(err: unknown, fallback: string): string {
  return err instanceof ApiError ? err.message : fallback;
}
