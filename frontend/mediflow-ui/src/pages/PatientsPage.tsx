import { FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useAsync } from '../hooks/useAsync';
import { patientsApi } from '../api/patients';
import { CreatePatientRequest } from '../types/patient';
import { ApiError } from '../types/common';
import { LoadingState, ErrorState, EmptyState } from '../components/PageState';
import { Pagination } from '../components/Pagination';
import { Modal } from '../components/Modal';
import { formatDate, displayName } from '../utils/format';

export function PatientsPage() {
  const { hasRole } = useAuth();
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [showCreate, setShowCreate] = useState(false);

  const canCreate = hasRole('ADMIN', 'PATIENT', 'DOCTOR', 'BILLING_STAFF', 'SUPPORT', 'NURSE');
  const showUserIdField = !hasRole('PATIENT');

  const list = useAsync(() => patientsApi.list({ search: search || undefined, page, size: 20 }), [search, page]);

  const handleSearch = (value: string) => {
    setSearch(value);
    setPage(0);
  };

  return (
    <div className="page-stack">
      <section className="hero-card">
        <div>
          <p className="eyebrow">Patient centre</p>
          <h2>Keep patient records organized and actionable.</h2>
          <p>Search patients, review profiles, and coordinate care from a single workspace.</p>
        </div>
        {canCreate ? (
          <button className="primary-btn" type="button" onClick={() => setShowCreate(true)}>
            Add patient
          </button>
        ) : null}
      </section>

      <section className="card">
        <div className="section-header">
          <h3>Patients</h3>
          <input
            className="search-input"
            type="search"
            placeholder="Search by name, email, or phone…"
            value={search}
            onChange={(e) => handleSearch(e.target.value)}
          />
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
                  <th>Patient</th>
                  <th>Email</th>
                  <th>Phone</th>
                  <th>Insurance</th>
                  <th>Created</th>
                </tr>
              </thead>
              <tbody>
                {list.data.content.map((patient) => (
                  <tr
                    key={patient.patientId}
                    className="row-link"
                    onClick={() => navigate(`/patients/${patient.patientId}`)}
                  >
                    <td>{displayName(patient.firstName, patient.lastName)}</td>
                    <td>{patient.email}</td>
                    <td>{patient.phone}</td>
                    <td>{patient.insuranceProvider ?? '—'}</td>
                    <td>{formatDate(patient.createdAt.slice(0, 10))}</td>
                  </tr>
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
          <EmptyState message="No patients found. Try adjusting your search." />
        )}
      </section>

      {showCreate ? (
        <CreatePatientModal
          showUserIdField={showUserIdField}
          onClose={() => setShowCreate(false)}
          onCreated={(id) => navigate(`/patients/${id}`)}
        />
      ) : null}
    </div>
  );
}

function CreatePatientModal({
  showUserIdField,
  onClose,
  onCreated,
}: {
  showUserIdField: boolean;
  onClose: () => void;
  onCreated: (patientId: string) => void;
}) {
  const [form, setForm] = useState<CreatePatientRequest>({
    firstName: '',
    lastName: '',
    email: '',
    phone: '',
    userId: '',
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const set = (key: keyof CreatePatientRequest, value: string) => setForm((f) => ({ ...f, [key]: value }));

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const created = await patientsApi.create(form);
      onCreated(created.patientId);
    } catch (err) {
      if (err instanceof ApiError) setError(err.message);
      else setError('Unable to create patient.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal title="Add patient" onClose={onClose}>
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
        {showUserIdField ? (
          <label className="field">
            <span>Linked user ID</span>
            <input value={form.userId ?? ''} onChange={(e) => set('userId', e.target.value)} />
            <small>Required when the caller is not a patient. Link this profile to an auth user.</small>
          </label>
        ) : null}
        {error ? <p className="form-error">{error}</p> : null}
        <div className="form-actions">
          <button type="button" className="btn secondary-btn" onClick={onClose}>
            Cancel
          </button>
          <button className="primary-btn" type="submit" disabled={submitting}>
            {submitting ? 'Saving…' : 'Create patient'}
          </button>
        </div>
      </form>
    </Modal>
  );
}