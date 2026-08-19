import { FormEvent, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useAsync } from '../hooks/useAsync';
import { patientsApi } from '../api/patients';
import { CreatePatientRequest } from '../types/patient';
import { ApiError } from '../types/common';
import { LoadingState, ErrorState, EmptyState } from '../components/PageState';
import { Modal } from '../components/Modal';
import { displayName, formatDate } from '../utils/format';

export function PatientDetailPage() {
  const { patientId = '' } = useParams();
  const navigate = useNavigate();
  const { hasRole } = useAuth();
  const [showEdit, setShowEdit] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const canEdit = hasRole('ADMIN', 'DOCTOR', 'BILLING_STAFF', 'SUPPORT', 'NURSE');
  const canDelete = hasRole('ADMIN');

  const detail = useAsync(() => patientsApi.get(patientId), [patientId]);

  const handleDelete = async () => {
    if (!window.confirm('Delete this patient record? This cannot be undone.')) return;
    setDeleting(true);
    setDeleteError(null);
    try {
      await patientsApi.remove(patientId);
      navigate('/patients');
    } catch (err) {
      if (err instanceof ApiError) setDeleteError(err.message);
      else setDeleteError('Unable to delete patient.');
      setDeleting(false);
    }
  };

  if (detail.loading) return <LoadingState />;
  if (detail.error) return <ErrorState message={detail.error} />;
  if (!detail.data) return <EmptyState message="Patient not found." />;

  const patient = detail.data;

  return (
    <div className="page-stack">
      <section className="hero-card">
        <div>
          <p className="eyebrow">Patient profile</p>
          <h2>{displayName(patient.firstName, patient.lastName)}</h2>
          <p>
            {patient.email} · {patient.phone}
          </p>
        </div>
        <div className="hero-actions">
          {canEdit ? (
            <button className="primary-btn" type="button" onClick={() => setShowEdit(true)}>
              Edit profile
            </button>
          ) : null}
          {canDelete ? (
            <button className="btn danger-btn" type="button" disabled={deleting} onClick={() => void handleDelete()}>
              {deleting ? 'Deleting…' : 'Delete'}
            </button>
          ) : null}
          <Link to="/patients" className="btn secondary-btn">
            Back
          </Link>
        </div>
      </section>

      {deleteError ? <ErrorState message={deleteError} /> : null}

      <section className="card">
        <div className="section-header">
          <h3>Details</h3>
        </div>
        <dl className="detail-grid">
          <div>
            <dt>Patient ID</dt>
            <dd>{patient.patientId}</dd>
          </div>
          <div>
            <dt>Date of birth</dt>
            <dd>{formatDate(patient.dateOfBirth)}</dd>
          </div>
          <div>
            <dt>Address</dt>
            <dd>{patient.address ?? '—'}</dd>
          </div>
          <div>
            <dt>Emergency contact</dt>
            <dd>
              {patient.emergencyContactName ?? '—'}
              {patient.emergencyContactPhone ? ` · ${patient.emergencyContactPhone}` : ''}
            </dd>
          </div>
          <div>
            <dt>Insurance provider</dt>
            <dd>{patient.insuranceProvider ?? '—'}</dd>
          </div>
          <div>
            <dt>Insurance member ID</dt>
            <dd>{patient.insuranceMemberId ?? '—'}</dd>
          </div>
          <div>
            <dt>Medical history</dt>
            <dd>{patient.medicalHistory ?? '—'}</dd>
          </div>
          <div>
            <dt>Record created</dt>
            <dd>{formatDate(patient.createdAt.slice(0, 10))}</dd>
          </div>
        </dl>
      </section>

      {showEdit && canEdit ? (
        <EditPatientModal
          patient={patient}
          onClose={() => setShowEdit(false)}
          onSaved={() => {
            setShowEdit(false);
            detail.reload();
          }}
        />
      ) : null}
    </div>
  );
}

function EditPatientModal({
  patient,
  onClose,
  onSaved,
}: {
  patient: CreatePatientRequest & { patientId: string };
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form, setForm] = useState<CreatePatientRequest>({
    firstName: patient.firstName,
    lastName: patient.lastName,
    email: patient.email,
    phone: patient.phone,
    dateOfBirth: patient.dateOfBirth,
    address: patient.address,
    medicalHistory: patient.medicalHistory,
    emergencyContactName: patient.emergencyContactName,
    emergencyContactPhone: patient.emergencyContactPhone,
    insuranceProvider: patient.insuranceProvider,
    insuranceMemberId: patient.insuranceMemberId,
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const set = (key: keyof CreatePatientRequest, value: string) => setForm((f) => ({ ...f, [key]: value }));

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await patientsApi.update(patient.patientId, form);
      onSaved();
    } catch (err) {
      if (err instanceof ApiError) setError(err.message);
      else setError('Unable to update patient.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal title="Edit patient" onClose={onClose}>
      <form className="form" onSubmit={handleSubmit}>
        <div className="form-grid">
          <label className="field">
            <span>First name *</span>
            <input required value={form.firstName} onChange={(e) => set('firstName', e.target.value)} />
          </label>
          <label className="field">
            <span>Last name *</span>
            <input required value={form.lastName} onChange={(e) => set('lastName', e.target.value)} />
          </label>
          <label className="field">
            <span>Email *</span>
            <input type="email" required value={form.email} onChange={(e) => set('email', e.target.value)} />
          </label>
          <label className="field">
            <span>Phone *</span>
            <input required value={form.phone} onChange={(e) => set('phone', e.target.value)} />
          </label>
          <label className="field">
            <span>Date of birth</span>
            <input type="date" value={form.dateOfBirth ?? ''} onChange={(e) => set('dateOfBirth', e.target.value)} />
          </label>
          <label className="field">
            <span>Address</span>
            <input value={form.address ?? ''} onChange={(e) => set('address', e.target.value)} />
          </label>
          <label className="field">
            <span>Emergency contact</span>
            <input
              value={form.emergencyContactName ?? ''}
              onChange={(e) => set('emergencyContactName', e.target.value)}
            />
          </label>
          <label className="field">
            <span>Emergency phone</span>
            <input
              value={form.emergencyContactPhone ?? ''}
              onChange={(e) => set('emergencyContactPhone', e.target.value)}
            />
          </label>
          <label className="field">
            <span>Insurance provider</span>
            <input
              value={form.insuranceProvider ?? ''}
              onChange={(e) => set('insuranceProvider', e.target.value)}
            />
          </label>
          <label className="field">
            <span>Insurance member ID</span>
            <input
              value={form.insuranceMemberId ?? ''}
              onChange={(e) => set('insuranceMemberId', e.target.value)}
            />
          </label>
        </div>
        <label className="field">
          <span>Medical history</span>
          <textarea
            rows={3}
            value={form.medicalHistory ?? ''}
            onChange={(e) => set('medicalHistory', e.target.value)}
          />
        </label>
        {error ? <p className="form-error">{error}</p> : null}
        <div className="form-actions">
          <button type="button" className="btn secondary-btn" onClick={onClose}>
            Cancel
          </button>
          <button className="primary-btn" type="submit" disabled={submitting}>
            {submitting ? 'Saving…' : 'Save changes'}
          </button>
        </div>
      </form>
    </Modal>
  );
}