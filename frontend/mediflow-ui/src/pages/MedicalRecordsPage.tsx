import { FormEvent, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { useAsync } from '../hooks/useAsync';
import { medicalRecordsApi } from '../api/medical-records';
import { patientsApi } from '../api/patients';
import { doctorsApi } from '../api/doctors';
import {
  CreateMedicalRecordRequest,
  MedicalRecord,
  MEDICAL_RECORD_TYPES,
  MedicalRecordType,
  UpdateMedicalRecordRequest,
} from '../types/medical-record';
import { ApiError } from '../types/common';
import { LoadingState, ErrorState, EmptyState } from '../components/PageState';
import { Pagination } from '../components/Pagination';
import { Modal } from '../components/Modal';
import { displayName, formatDate, shortId } from '../utils/format';

export function MedicalRecordsPage() {
  const { user, roles, hasRole } = useAuth();
  const [recordType, setRecordType] = useState('');
  const [page, setPage] = useState(0);
  const [showCreate, setShowCreate] = useState(false);

  const isPatient = roles.includes('PATIENT');
  const isDoctor = roles.includes('DOCTOR');
  const canWrite = hasRole('ADMIN', 'DOCTOR');

  const scope = {
    patientId: isPatient ? user?.id : undefined,
    doctorId: isDoctor ? user?.id : undefined,
  };

  const list = useAsync(
    () =>
      medicalRecordsApi.list({
        ...scope,
        recordType: (recordType as MedicalRecordType) || undefined,
        page,
        size: 20,
      }),
    [scope.patientId, scope.doctorId, recordType, page],
  );

  const doctorNames = useAsync(
    () =>
      doctorsApi
        .list({ page: 0, size: 1000 })
        .then((p) => p.content.map((d) => ({ doctorId: d.doctorId, name: `Dr. ${displayName(d.firstName, d.lastName)}` }))),
    [],
  );

  const patientNames = useAsync(
    () =>
      patientsApi
        .list({ page: 0, size: 1000 })
        .then((p) => p.content.map((pt) => ({ patientId: pt.patientId, name: displayName(pt.firstName, pt.lastName) }))),
    [],
  );

  const doctorName = (id: string) => doctorNames.data?.find((d) => d.doctorId === id)?.name ?? shortId(id);
  const patientName = (id: string) => patientNames.data?.find((p) => p.patientId === id)?.name ?? shortId(id);

  return (
    <div className="page-stack">
      <section className="hero-card">
        <div>
          <p className="eyebrow">Medical records</p>
          <h2>Clinical documentation in one place.</h2>
          <p>Review consultations, diagnoses, and treatment plans for each patient.</p>
        </div>
        {canWrite ? (
          <button className="primary-btn" type="button" onClick={() => setShowCreate(true)}>
            New record
          </button>
        ) : null}
      </section>

      <section className="card">
        <div className="section-header">
          <h3>Records</h3>
          <select
            className="search-input"
            value={recordType}
            onChange={(e) => {
              setRecordType(e.target.value);
              setPage(0);
            }}
          >
            <option value="">All types</option>
            {MEDICAL_RECORD_TYPES.map((t) => (
              <option key={t} value={t}>
                {t.replace(/_/g, ' ')}
              </option>
            ))}
          </select>
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
                  <th>Date</th>
                  <th>Type</th>
                  <th>Patient</th>
                  <th>Doctor</th>
                  <th>Chief complaint</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {list.data.content.map((record) => (
                  <RecordRow
                    key={record.recordId}
                    record={record}
                    patientName={patientName(record.patientId)}
                    doctorName={record.doctorId ? doctorName(record.doctorId) : '—'}
                    canEdit={canWrite}
                    onSaved={() => list.reload()}
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
          <EmptyState message="No medical records found." />
        )}
      </section>

      {showCreate && canWrite ? (
        <CreateRecordModal
          isDoctor={isDoctor}
          currentUserId={user?.id ?? ''}
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

function RecordRow({
  record,
  patientName,
  doctorName,
  canEdit,
  onSaved,
}: {
  record: MedicalRecord;
  patientName: string;
  doctorName: string;
  canEdit: boolean;
  onSaved: () => void;
}) {
  const [editing, setEditing] = useState(false);

  return (
    <>
      <tr>
        <td>{formatDate(record.encounterDate)}</td>
        <td>
          <span className="pill">{record.recordType.replace(/_/g, ' ')}</span>
        </td>
        <td>{patientName}</td>
        <td>{doctorName}</td>
        <td>{record.chiefComplaint}</td>
        <td className="row-actions">
          {canEdit ? (
            <button className="btn small-btn" type="button" onClick={() => setEditing(true)}>
              Edit
            </button>
          ) : null}
        </td>
      </tr>
      {editing ? (
        <EditRecordModal
          record={record}
          onClose={() => setEditing(false)}
          onSaved={() => {
            setEditing(false);
            onSaved();
          }}
        />
      ) : null}
    </>
  );
}

function CreateRecordModal({
  isDoctor,
  currentUserId,
  onClose,
  onCreated,
}: {
  isDoctor: boolean;
  currentUserId: string;
  onClose: () => void;
  onCreated: () => void;
}) {
  const [form, setForm] = useState<CreateMedicalRecordRequest>({
    patientId: '',
    doctorId: isDoctor ? currentUserId : '',
    encounterDate: new Date().toISOString().slice(0, 10),
    chiefComplaint: '',
    notes: '',
    recordType: 'CONSULTATION',
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const patients = useAsync(() => patientsApi.list({ page: 0, size: 1000 }), []);

  const set = (key: keyof CreateMedicalRecordRequest, value: string) =>
    setForm((f) => ({ ...f, [key]: value }));

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await medicalRecordsApi.create(form);
      onCreated();
    } catch (err) {
      if (err instanceof ApiError) setError(err.message);
      else setError('Unable to create record.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal title="New medical record" onClose={onClose}>
      <form className="form" onSubmit={handleSubmit}>
        {!isDoctor ? (
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
        ) : (
          <p className="muted">Creating record for: {currentUserId}</p>
        )}
        <div className="form-grid">
          <label className="field">
            <span>Encounter date *</span>
            <input
              type="date"
              required
              value={form.encounterDate}
              onChange={(e) => set('encounterDate', e.target.value)}
            />
          </label>
          <label className="field">
            <span>Record type *</span>
            <select value={form.recordType} onChange={(e) => set('recordType', e.target.value)}>
              {MEDICAL_RECORD_TYPES.map((t) => (
                <option key={t} value={t}>
                  {t.replace(/_/g, ' ')}
                </option>
              ))}
            </select>
          </label>
        </div>
        <label className="field">
          <span>Chief complaint *</span>
          <input required value={form.chiefComplaint} onChange={(e) => set('chiefComplaint', e.target.value)} />
        </label>
        <label className="field">
          <span>Diagnosis</span>
          <input value={form.diagnosis ?? ''} onChange={(e) => set('diagnosis', e.target.value)} />
        </label>
        <label className="field">
          <span>Treatment plan</span>
          <textarea rows={2} value={form.treatmentPlan ?? ''} onChange={(e) => set('treatmentPlan', e.target.value)} />
        </label>
        <label className="field">
          <span>Notes</span>
          <textarea rows={3} value={form.notes} onChange={(e) => set('notes', e.target.value)} />
        </label>
        {error ? <p className="form-error">{error}</p> : null}
        <div className="form-actions">
          <button type="button" className="btn secondary-btn" onClick={onClose}>
            Cancel
          </button>
          <button className="primary-btn" type="submit" disabled={submitting}>
            {submitting ? 'Saving…' : 'Create record'}
          </button>
        </div>
      </form>
    </Modal>
  );
}

function EditRecordModal({
  record,
  onClose,
  onSaved,
}: {
  record: MedicalRecord;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form, setForm] = useState<UpdateMedicalRecordRequest>({
    encounterDate: record.encounterDate,
    recordType: record.recordType,
    chiefComplaint: record.chiefComplaint,
    diagnosis: record.diagnosis,
    treatmentPlan: record.treatmentPlan,
    notes: record.notes,
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const set = (key: keyof UpdateMedicalRecordRequest, value: string) =>
    setForm((f) => ({ ...f, [key]: value }));

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await medicalRecordsApi.update(record.recordId, form);
      onSaved();
    } catch (err) {
      if (err instanceof ApiError) setError(err.message);
      else setError('Unable to update record.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal title="Edit medical record" onClose={onClose}>
      <form className="form" onSubmit={handleSubmit}>
        <div className="form-grid">
          <label className="field">
            <span>Encounter date *</span>
            <input
              type="date"
              required
              value={form.encounterDate ?? ''}
              onChange={(e) => set('encounterDate', e.target.value)}
            />
          </label>
          <label className="field">
            <span>Record type *</span>
            <select value={form.recordType ?? 'CONSULTATION'} onChange={(e) => set('recordType', e.target.value)}>
              {MEDICAL_RECORD_TYPES.map((t) => (
                <option key={t} value={t}>
                  {t.replace(/_/g, ' ')}
                </option>
              ))}
            </select>
          </label>
        </div>
        <label className="field">
          <span>Chief complaint *</span>
          <input
            required
            value={form.chiefComplaint ?? ''}
            onChange={(e) => set('chiefComplaint', e.target.value)}
          />
        </label>
        <label className="field">
          <span>Diagnosis</span>
          <input value={form.diagnosis ?? ''} onChange={(e) => set('diagnosis', e.target.value)} />
        </label>
        <label className="field">
          <span>Treatment plan</span>
          <textarea rows={2} value={form.treatmentPlan ?? ''} onChange={(e) => set('treatmentPlan', e.target.value)} />
        </label>
        <label className="field">
          <span>Notes</span>
          <textarea rows={3} value={form.notes ?? ''} onChange={(e) => set('notes', e.target.value)} />
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