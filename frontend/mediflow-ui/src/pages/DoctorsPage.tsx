import { FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useAsync } from '../hooks/useAsync';
import { doctorsApi } from '../api/doctors';
import { CreateDoctorRequest } from '../types/doctor';
import { ApiError } from '../types/common';
import { LoadingState, ErrorState, EmptyState } from '../components/PageState';
import { Pagination } from '../components/Pagination';
import { Modal } from '../components/Modal';
import { displayName, formatCurrency } from '../utils/format';

export function DoctorsPage() {
  const { hasRole } = useAuth();
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const [department, setDepartment] = useState('');
  const [page, setPage] = useState(0);
  const [showCreate, setShowCreate] = useState(false);

  const canCreate = hasRole('ADMIN', 'DOCTOR', 'NURSE', 'BILLING_STAFF', 'SUPPORT');
  const showUserIdField = !hasRole('DOCTOR');

  const list = useAsync(
    () => doctorsApi.list({ search: search || undefined, department: department || undefined, page, size: 20 }),
    [search, department, page],
  );

  return (
    <div className="page-stack">
      <section className="hero-card">
        <div>
          <p className="eyebrow">Care team</p>
          <h2>Support clinical staff with a clear operating view.</h2>
          <p>Review providers, specialities, and availability at a glance.</p>
        </div>
        {canCreate ? (
          <button className="primary-btn" type="button" onClick={() => setShowCreate(true)}>
            Add doctor
          </button>
        ) : null}
      </section>

      <section className="card">
        <div className="section-header">
          <h3>Doctors</h3>
          <div className="toolbar">
            <input
              className="search-input"
              type="search"
              placeholder="Search by name or specialty…"
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(0);
              }}
            />
            <select
              className="search-input"
              value={department}
              onChange={(e) => {
                setDepartment(e.target.value);
                setPage(0);
              }}
            >
              <option value="">All departments</option>
              <option value="CARDIOLOGY">Cardiology</option>
              <option value="NEUROLOGY">Neurology</option>
              <option value="PEDIATRICS">Pediatrics</option>
              <option value="ONCOLOGY">Oncology</option>
              <option value="ORTHOPEDICS">Orthopedics</option>
              <option value="GENERAL">General</option>
            </select>
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
                  <th>Doctor</th>
                  <th>Specialty</th>
                  <th>Department</th>
                  <th>Experience</th>
                  <th>Consultation fee</th>
                </tr>
              </thead>
              <tbody>
                {list.data.content.map((doctor) => (
                  <tr key={doctor.doctorId} className="row-link" onClick={() => navigate(`/doctors/${doctor.doctorId}`)}>
                    <td>Dr. {displayName(doctor.firstName, doctor.lastName)}</td>
                    <td>{doctor.specialty}</td>
                    <td>{doctor.department}</td>
                    <td>{doctor.yearsOfExperience} yrs</td>
                    <td>{formatCurrency(doctor.consultationFee)}</td>
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
          <EmptyState message="No doctors found. Try adjusting your search." />
        )}
      </section>

      {showCreate ? (
        <CreateDoctorModal
          showUserIdField={showUserIdField}
          onClose={() => setShowCreate(false)}
          onCreated={(id) => navigate(`/doctors/${id}`)}
        />
      ) : null}
    </div>
  );
}

function CreateDoctorModal({
  showUserIdField,
  onClose,
  onCreated,
}: {
  showUserIdField: boolean;
  onClose: () => void;
  onCreated: (doctorId: string) => void;
}) {
  const [form, setForm] = useState<CreateDoctorRequest>({
    firstName: '',
    lastName: '',
    email: '',
    phone: '',
    specialty: '',
    yearsOfExperience: 0,
    department: 'GENERAL',
    consultationFee: 0,
    userId: '',
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const set = (key: keyof CreateDoctorRequest, value: string | number) =>
    setForm((f) => ({ ...f, [key]: value }));

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const created = await doctorsApi.create(form);
      onCreated(created.doctorId);
    } catch (err) {
      if (err instanceof ApiError) setError(err.message);
      else setError('Unable to create doctor.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal title="Add doctor" onClose={onClose}>
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
            <span>Specialty *</span>
            <input required value={form.specialty} onChange={(e) => set('specialty', e.target.value)} />
          </label>
          <label className="field">
            <span>Department *</span>
            <select value={form.department} onChange={(e) => set('department', e.target.value)}>
              <option value="CARDIOLOGY">Cardiology</option>
              <option value="NEUROLOGY">Neurology</option>
              <option value="PEDIATRICS">Pediatrics</option>
              <option value="ONCOLOGY">Oncology</option>
              <option value="ORTHOPEDICS">Orthopedics</option>
              <option value="GENERAL">General</option>
            </select>
          </label>
          <label className="field">
            <span>Years of experience *</span>
            <input
              type="number"
              min={0}
              required
              value={form.yearsOfExperience}
              onChange={(e) => set('yearsOfExperience', Number(e.target.value))}
            />
          </label>
          <label className="field">
            <span>Consultation fee *</span>
            <input
              type="number"
              min={0}
              step="0.01"
              required
              value={form.consultationFee}
              onChange={(e) => set('consultationFee', Number(e.target.value))}
            />
          </label>
          <label className="field">
            <span>Qualification</span>
            <input value={form.qualification ?? ''} onChange={(e) => set('qualification', e.target.value)} />
          </label>
        </div>
        {showUserIdField ? (
          <label className="field">
            <span>Linked user ID</span>
            <input value={form.userId ?? ''} onChange={(e) => set('userId', e.target.value)} />
            <small>Required when the caller is not a doctor. Link this profile to an auth user.</small>
          </label>
        ) : null}
        {error ? <p className="form-error">{error}</p> : null}
        <div className="form-actions">
          <button type="button" className="btn secondary-btn" onClick={onClose}>
            Cancel
          </button>
          <button className="primary-btn" type="submit" disabled={submitting}>
            {submitting ? 'Saving…' : 'Create doctor'}
          </button>
        </div>
      </form>
    </Modal>
  );
}