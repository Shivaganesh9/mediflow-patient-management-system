import { FormEvent, useMemo, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { useAsync } from '../hooks/useAsync';
import { appointmentsApi } from '../api/appointments';
import { patientsApi } from '../api/patients';
import { doctorsApi } from '../api/doctors';
import {
  APPOINTMENT_STATUSES,
  Appointment,
  AppointmentHistoryEntry,
  AppointmentStatus,
  CreateAppointmentRequest,
  UpdateAppointmentRequest,
} from '../types/appointment';
import { ApiError } from '../types/common';
import { LoadingState, ErrorState, EmptyState } from '../components/PageState';
import { Pagination } from '../components/Pagination';
import { Modal } from '../components/Modal';
import { StatusPill } from '../components/StatusPill';
import { displayName, formatDateTime, fromLocalDateTimeInput, shortId, toLocalDateTimeInput } from '../utils/format';

const DAY_NAMES = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];

export function AppointmentsPage() {
  const { user, roles, hasRole } = useAuth();
  const [status, setStatus] = useState('');
  const [fromTime, setFromTime] = useState('');
  const [toTime, setToTime] = useState('');
  const [page, setPage] = useState(0);
  const [showCreate, setShowCreate] = useState(false);

  const isPatient = roles.includes('PATIENT');
  const isDoctor = roles.includes('DOCTOR');
  const isStaff = hasRole('ADMIN', 'NURSE', 'BILLING_STAFF', 'SUPPORT');
  const canClinical = hasRole('ADMIN', 'DOCTOR', 'NURSE');

  const scope = useMemo(
    () => ({
      patientId: isPatient ? user?.id : undefined,
      doctorId: isDoctor ? user?.id : undefined,
    }),
    [isPatient, isDoctor, user?.id],
  );

  const list = useAsync(
    () =>
      appointmentsApi.list({
        ...scope,
        status: (status as AppointmentStatus) || undefined,
        fromTime: fromTime ? fromLocalDateTimeInput(fromTime) : undefined,
        toTime: toTime ? fromLocalDateTimeInput(toTime) : undefined,
        page,
        size: 20,
      }),
    [scope.patientId, scope.doctorId, status, fromTime, toTime, page],
  );

  const doctorNames = useAsync(
    () =>
      isDoctor
        ? Promise.resolve([{ doctorId: user?.id ?? '', name: user?.fullName ?? shortId(user?.id) }])
        : doctorsApi.list({ page: 0, size: 1000 }).then((p) => p.content.map((d) => ({ doctorId: d.doctorId, name: `Dr. ${displayName(d.firstName, d.lastName)}` }))),
    [isDoctor, user?.id, user?.fullName],
  );

  const patientNames = useAsync(
    () =>
      isPatient
        ? Promise.resolve([{ patientId: user?.id ?? '', name: user?.fullName ?? shortId(user?.id) }])
        : patientsApi.list({ page: 0, size: 1000 }).then((p) => p.content.map((pt) => ({ patientId: pt.patientId, name: displayName(pt.firstName, pt.lastName) }))),
    [isPatient, user?.id, user?.fullName],
  );

  const doctorName = (id: string) => doctorNames.data?.find((d) => d.doctorId === id)?.name ?? shortId(id);
  const patientName = (id: string) => patientNames.data?.find((p) => p.patientId === id)?.name ?? shortId(id);

  const canCreate = isPatient || isDoctor || isStaff;

  return (
    <div className="page-stack">
      <section className="hero-card">
        <div>
          <p className="eyebrow">Appointments</p>
          <h2>Coordinate every visit with confidence.</h2>
          <p>Track upcoming consultations, follow-ups, and cancellations in one view.</p>
        </div>
        {canCreate ? (
          <button className="primary-btn" type="button" onClick={() => setShowCreate(true)}>
            Schedule visit
          </button>
        ) : null}
      </section>

      <section className="card">
        <div className="section-header">
          <h3>Appointments</h3>
          <div className="toolbar">
            <select
              className="search-input"
              value={status}
              onChange={(e) => {
                setStatus(e.target.value);
                setPage(0);
              }}
            >
              <option value="">All statuses</option>
              {APPOINTMENT_STATUSES.map((s) => (
                <option key={s} value={s}>
                  {s.replace(/_/g, ' ')}
                </option>
              ))}
            </select>
            <input
              className="search-input"
              type="datetime-local"
              aria-label="From"
              value={fromTime}
              onChange={(e) => {
                setFromTime(e.target.value);
                setPage(0);
              }}
            />
            <input
              className="search-input"
              type="datetime-local"
              aria-label="To"
              value={toTime}
              onChange={(e) => {
                setToTime(e.target.value);
                setPage(0);
              }}
            />
          </div>
        </div>

        {list.loading ? (
          <LoadingState />
        ) : list.error ? (
          <ErrorState message={list.error} />
        ) : list.data && list.data.content.length > 0 ? (
          <>
            <table className="table">
              <thead>
                <tr>
                  <th>Reason</th>
                  <th>Patient</th>
                  <th>Doctor</th>
                  <th>Start</th>
                  <th>End</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {list.data.content.map((appointment) => (
                  <AppointmentRow
                    key={appointment.appointmentId}
                    appointment={appointment}
                    patientName={patientName(appointment.patientId)}
                    doctorName={doctorName(appointment.doctorId)}
                    canClinical={canClinical}
                    onChanged={() => list.reload()}
                  />
                ))}
              </tbody>
            </table>
            <Pagination
              page={list.data.number}
              totalPages={list.data.totalPages}
              totalElements={list.data.totalElements}
              pageSize={list.data.size}
              onPageChange={setPage}
            />
          </>
        ) : (
          <EmptyState message="No appointments match the current filters." />
        )}
      </section>

      {showCreate && canCreate ? (
        <CreateAppointmentModal
          isPatient={isPatient}
          isDoctor={isDoctor}
          currentUserId={user?.id ?? ''}
          currentUserName={user?.fullName}
          onClose={() => setShowCreate(false)}
          onCreated={() => {
            setShowCreate(false);
            list.reload();
          }}
        />
      ) : null}
    </div>
  );
}

function AppointmentRow({
  appointment,
  patientName,
  doctorName,
  canClinical,
  onChanged,
}: {
  appointment: Appointment;
  patientName: string;
  doctorName: string;
  canClinical: boolean;
  onChanged: () => void;
}) {
  const [rescheduling, setRescheduling] = useState(false);
  const [showingHistory, setShowingHistory] = useState(false);
  const [pendingAction, setPendingAction] = useState<string | null>(null);

  const mutable = appointment.status === 'SCHEDULED' || appointment.status === 'CONFIRMED';
  const busy = pendingAction !== null;

  const runTransition = async (label: string, prompt: string, action: () => Promise<unknown>) => {
    if (!window.confirm(prompt)) return;
    setPendingAction(label);
    try {
      await action();
      onChanged();
    } catch (err) {
      window.alert(transitionErrorMessage(err));
      // A rejected transition usually means the appointment moved on in another tab or by another
      // user; refreshing keeps this row honest instead of leaving a stale action set.
      onChanged();
    } finally {
      setPendingAction(null);
    }
  };

  const handleCancel = () =>
    runTransition('cancel', `Cancel this appointment (${appointment.reason})?`, () =>
      appointmentsApi.cancel(appointment.appointmentId),
    );

  return (
    <>
      <tr>
        <td>{appointment.reason}</td>
        <td>{patientName}</td>
        <td>{doctorName}</td>
        <td>{formatDateTime(appointment.startTime)}</td>
        <td>{formatDateTime(appointment.endTime)}</td>
        <td>
          <StatusPill status={appointment.status} />
        </td>
        <td className="row-actions">
          {appointment.status === 'SCHEDULED' ? (
            <button
              className="btn small-btn"
              type="button"
              disabled={busy}
              onClick={() =>
                void runTransition('confirm', 'Confirm this appointment?', () =>
                  appointmentsApi.confirm(appointment.appointmentId),
                )
              }
            >
              {pendingAction === 'confirm' ? '…' : 'Confirm'}
            </button>
          ) : null}
          {canClinical && appointment.status === 'CONFIRMED' ? (
            <button
              className="btn small-btn"
              type="button"
              disabled={busy}
              onClick={() =>
                void runTransition('start', 'Start this appointment now?', () =>
                  appointmentsApi.start(appointment.appointmentId),
                )
              }
            >
              {pendingAction === 'start' ? '…' : 'Start'}
            </button>
          ) : null}
          {canClinical && appointment.status === 'IN_PROGRESS' ? (
            <button
              className="btn small-btn"
              type="button"
              disabled={busy}
              onClick={() =>
                void runTransition('complete', 'Mark this appointment as completed?', () =>
                  appointmentsApi.complete(appointment.appointmentId),
                )
              }
            >
              {pendingAction === 'complete' ? '…' : 'Complete'}
            </button>
          ) : null}
          {canClinical && (appointment.status === 'SCHEDULED' || appointment.status === 'CONFIRMED') ? (
            <button
              className="btn small-btn"
              type="button"
              disabled={busy}
              onClick={() =>
                void runTransition('no-show', 'Mark this appointment as a no-show?', () =>
                  appointmentsApi.noShow(appointment.appointmentId),
                )
              }
            >
              {pendingAction === 'no-show' ? '…' : 'No-show'}
            </button>
          ) : null}
          <button className="btn small-btn" type="button" onClick={() => setShowingHistory(true)}>
            History
          </button>
          {mutable ? (
            <button className="btn small-btn" type="button" disabled={busy} onClick={() => setRescheduling(true)}>
              Reschedule
            </button>
          ) : null}
          {mutable ? (
            <button className="btn small-btn" type="button" disabled={busy} onClick={() => void handleCancel()}>
              {pendingAction === 'cancel' ? '…' : 'Cancel'}
            </button>
          ) : null}
        </td>
      </tr>
      {rescheduling ? (
        <RescheduleModal
          appointment={appointment}
          onClose={() => setRescheduling(false)}
          onSaved={() => {
            setRescheduling(false);
            onChanged();
          }}
        />
      ) : null}
      {showingHistory ? (
        <HistoryModal appointment={appointment} onClose={() => setShowingHistory(false)} />
      ) : null}
    </>
  );
}

function HistoryModal({ appointment, onClose }: { appointment: Appointment; onClose: () => void }) {
  const history = useAsync(
    () => appointmentsApi.history(appointment.appointmentId),
    [appointment.appointmentId],
  );

  return (
    <Modal title={`History — ${appointment.reason}`} onClose={onClose}>
      {history.loading ? (
        <LoadingState />
      ) : history.error ? (
        <ErrorState message={history.error} />
      ) : history.data && history.data.length > 0 ? (
        <table className="table">
          <thead>
            <tr>
              <th>When</th>
              <th>Action</th>
              <th>Status</th>
              <th>Actor</th>
            </tr>
          </thead>
          <tbody>
            {history.data.map((entry: AppointmentHistoryEntry) => (
              <tr key={entry.historyId}>
                <td>{formatDateTime(entry.occurredAt)}</td>
                <td>
                  <span className="pill">{entry.action.replace(/_/g, ' ')}</span>
                  {entry.details ? <p className="form-hint">{entry.details}</p> : null}
                </td>
                <td>
                  {entry.fromStatus ? `${entry.fromStatus.replace(/_/g, ' ')} → ` : ''}
                  {entry.toStatus.replace(/_/g, ' ')}
                </td>
                <td>{entry.actor ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <EmptyState message="No history recorded for this appointment." />
      )}
    </Modal>
  );
}

function CreateAppointmentModal({
  isPatient,
  isDoctor,
  currentUserId,
  currentUserName,
  onClose,
  onCreated,
}: {
  isPatient: boolean;
  isDoctor: boolean;
  currentUserId: string;
  currentUserName?: string;
  onClose: () => void;
  onCreated: () => void;
}) {
  const [form, setForm] = useState<CreateAppointmentRequest>({
    patientId: isPatient ? currentUserId : '',
    doctorId: isDoctor ? currentUserId : '',
    startTime: '',
    endTime: '',
    reason: '',
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const patients = useAsync(
    () => patientsApi.list({ page: 0, size: 1000 }),
    [isPatient],
  );
  const doctors = useAsync(() => doctorsApi.list({ page: 0, size: 1000 }), [isDoctor]);

  const set = (key: keyof CreateAppointmentRequest, value: string) => setForm((f) => ({ ...f, [key]: value }));

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await appointmentsApi.create({
        ...form,
        startTime: fromLocalDateTimeInput(form.startTime),
        endTime: fromLocalDateTimeInput(form.endTime),
      });
      onCreated();
    } catch (err) {
      setError(appointmentErrorMessage(err, 'Unable to schedule appointment.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal title="Schedule visit" onClose={onClose}>
      <form className="form" onSubmit={handleSubmit}>
        {isPatient ? (
          <p className="form-hint">Booking as: {currentUserName ?? currentUserId}</p>
        ) : null}
        {isDoctor ? (
          <p className="form-hint">Booking as: {currentUserName ?? currentUserId}</p>
        ) : null}
        {!isPatient ? (
          <label className="field">
            <span>Patient *</span>
            <select required value={form.patientId} onChange={(e) => set('patientId', e.target.value)}>
              <option value="">Select patient…</option>
              {patients.data?.content.map((p) => (
                <option key={p.patientId} value={p.patientId}>
                  {displayName(p.firstName, p.lastName)} ({shortId(p.patientId)})
                </option>
              ))}
            </select>
          </label>
        ) : null}
        {!isDoctor ? (
          <label className="field">
            <span>Doctor *</span>
            <select required value={form.doctorId} onChange={(e) => set('doctorId', e.target.value)}>
              <option value="">Select doctor…</option>
              {doctors.data?.content.map((d) => (
                <option key={d.doctorId} value={d.doctorId}>
                  Dr. {displayName(d.firstName, d.lastName)} — {d.specialty}
                </option>
              ))}
            </select>
          </label>
        ) : null}
        <AvailabilityHint doctorId={form.doctorId} startTime={form.startTime} endTime={form.endTime} />
        <div className="form-grid">
          <label className="field">
            <span>Start *</span>
            <input
              type="datetime-local"
              required
              value={toLocalDateTimeInput(form.startTime)}
              onChange={(e) => set('startTime', fromLocalDateTimeInput(e.target.value))}
            />
          </label>
          <label className="field">
            <span>End *</span>
            <input
              type="datetime-local"
              required
              value={toLocalDateTimeInput(form.endTime)}
              onChange={(e) => set('endTime', fromLocalDateTimeInput(e.target.value))}
            />
          </label>
        </div>
        <label className="field">
          <span>Reason *</span>
          <input required value={form.reason} onChange={(e) => set('reason', e.target.value)} />
        </label>
        <label className="field">
          <span>Appointment type</span>
          <input value={form.appointmentType ?? ''} onChange={(e) => set('appointmentType', e.target.value)} />
        </label>
        <label className="field">
          <span>Notes</span>
          <textarea rows={2} value={form.notes ?? ''} onChange={(e) => set('notes', e.target.value)} />
        </label>
        {error ? <p className="form-error">{error}</p> : null}
        <div className="form-actions">
          <button type="button" className="btn secondary-btn" onClick={onClose}>
            Cancel
          </button>
          <button className="primary-btn" type="submit" disabled={submitting}>
            {submitting ? 'Scheduling…' : 'Schedule'}
          </button>
        </div>
      </form>
    </Modal>
  );
}

/**
 * Shows the selected doctor's active working hours and approved leave before the request is sent.
 * The appointment service still validates the slot; these hints only help the user pick a time that
 * will be accepted.
 */
function AvailabilityHint({
  doctorId,
  startTime,
  endTime,
}: {
  doctorId: string;
  startTime: string;
  endTime: string;
}) {
  const availability = useAsync(
    () => (doctorId ? doctorsApi.availability.list(doctorId) : Promise.resolve([])),
    [doctorId],
  );
  const leaves = useAsync(
    () => (doctorId ? doctorsApi.leaves.list(doctorId) : Promise.resolve([])),
    [doctorId],
  );

  if (!doctorId) return null;
  if (availability.loading || leaves.loading) {
    return <p className="form-hint">Checking the doctor&apos;s working hours…</p>;
  }
  if (availability.error) {
    return <p className="form-hint">Working hours are unavailable: {availability.error}</p>;
  }

  const windows = availability.data ?? [];
  if (windows.length === 0) {
    return (
      <p className="form-warning">
        This doctor has no active working hours. Bookings will be rejected until availability is added.
      </p>
    );
  }

  const date = startTime.slice(0, 10);
  const day = weekdayOf(date);
  const dayWindows = day ? windows.filter((w) => w.dayOfWeek === day) : [];
  const leave = date ? leaves.data?.find((l) => date >= l.startDate && date <= l.endDate) : undefined;

  const windowLabel = (from: string, to: string) => `${from.slice(0, 5)}–${to.slice(0, 5)}`;
  const daySummary = day
    ? dayWindows.map((w) => windowLabel(w.startTime, w.endTime)).join(', ')
    : '';

  let message: string | null = null;
  let warning = false;
  if (leave) {
    warning = true;
    message = `Dr. is on leave ${leave.startDate} – ${leave.endDate}. Pick another date.`;
  } else if (date && dayWindows.length === 0) {
    warning = true;
    message = `No working hours on ${day}. Pick another day.`;
  } else if (date && startTime.length >= 16 && endTime.length >= 16) {
    const start = startTime.slice(11, 16);
    const end = endTime.slice(11, 16);
    const fits = dayWindows.some(
      (w) => start >= w.startTime.slice(0, 5) && end <= w.endTime.slice(0, 5),
    );
    if (!fits) {
      warning = true;
      message = `The selected time is outside the doctor's working hours (${daySummary}).`;
    } else {
      message = 'The selected time is within the doctor\u2019s working hours.';
    }
  }

  return (
    <div className="form-hint-block">
      <p className="form-hint">
        Working hours: {windows.map((w) => `${w.dayOfWeek.slice(0, 3)} ${windowLabel(w.startTime, w.endTime)}`).join(' · ')}
      </p>
      {message ? <p className={warning ? 'form-warning' : 'form-hint'}>{message}</p> : null}
    </div>
  );
}

function RescheduleModal({
  appointment,
  onClose,
  onSaved,
}: {
  appointment: Appointment;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form, setForm] = useState<UpdateAppointmentRequest>({
    startTime: appointment.startTime,
    endTime: appointment.endTime,
    notes: appointment.notes,
    reason: appointment.reason,
    appointmentType: appointment.appointmentType,
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const set = (key: keyof UpdateAppointmentRequest, value: string) => setForm((f) => ({ ...f, [key]: value }));

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await appointmentsApi.update(appointment.appointmentId, {
        ...form,
        startTime: fromLocalDateTimeInput(form.startTime),
        endTime: fromLocalDateTimeInput(form.endTime),
      });
      onSaved();
    } catch (err) {
      setError(appointmentErrorMessage(err, 'Unable to reschedule appointment.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal title="Reschedule appointment" onClose={onClose}>
      <form className="form" onSubmit={handleSubmit}>
        <AvailabilityHint
          doctorId={appointment.doctorId}
          startTime={form.startTime}
          endTime={form.endTime}
        />
        <div className="form-grid">
          <label className="field">
            <span>Start *</span>
            <input
              type="datetime-local"
              required
              value={toLocalDateTimeInput(form.startTime)}
              onChange={(e) => set('startTime', fromLocalDateTimeInput(e.target.value))}
            />
          </label>
          <label className="field">
            <span>End *</span>
            <input
              type="datetime-local"
              required
              value={toLocalDateTimeInput(form.endTime)}
              onChange={(e) => set('endTime', fromLocalDateTimeInput(e.target.value))}
            />
          </label>
        </div>
        <label className="field">
          <span>Reason</span>
          <input value={form.reason ?? ''} onChange={(e) => set('reason', e.target.value)} />
        </label>
        <label className="field">
          <span>Appointment type</span>
          <input value={form.appointmentType ?? ''} onChange={(e) => set('appointmentType', e.target.value)} />
        </label>
        <label className="field">
          <span>Notes</span>
          <textarea rows={2} value={form.notes ?? ''} onChange={(e) => set('notes', e.target.value)} />
        </label>
        {error ? <p className="form-error">{error}</p> : null}
        <div className="form-actions">
          <button type="button" className="btn secondary-btn" onClick={onClose}>
            Cancel
          </button>
          <button className="primary-btn" type="submit" disabled={submitting}>
            {submitting ? 'Saving…' : 'Reschedule'}
          </button>
        </div>
      </form>
    </Modal>
  );
}

function weekdayOf(date: string): string | null {
  if (!date) return null;
  const parsed = new Date(`${date}T00:00:00`);
  return Number.isNaN(parsed.getTime()) ? null : DAY_NAMES[parsed.getDay()];
}

/**
 * Turns an appointment API failure into a message the user can act on. A 409 means the doctor's
 * slot is already taken (or the appointment changed state), so point at the fix instead of
 * showing a bare "Request failed".
 */
function appointmentErrorMessage(err: unknown, fallback: string): string {
  if (err instanceof ApiError) {
    return err.status === 409 ? `${err.message}. Please choose a different time slot.` : err.message;
  }
  return fallback;
}

function transitionErrorMessage(err: unknown): string {
  if (err instanceof ApiError) {
    return err.status === 409
      ? `${err.message}. The appointment may have changed; the list will be refreshed.`
      : err.message;
  }
  return 'Unable to update the appointment.';
}
