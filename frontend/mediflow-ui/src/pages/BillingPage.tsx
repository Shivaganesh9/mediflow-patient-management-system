import { FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useAsync } from '../hooks/useAsync';
import { billingApi } from '../api/billing';
import { patientsApi } from '../api/patients';
import {
  CreateInvoiceItemRequest,
  CreateInvoiceRequest,
  InvoiceStatus,
  INVOICE_ITEM_TYPES,
  INVOICE_STATUSES,
} from '../types/billing';
import { ApiError } from '../types/common';
import { LoadingState, ErrorState, EmptyState } from '../components/PageState';
import { Pagination } from '../components/Pagination';
import { Modal } from '../components/Modal';
import { StatusPill } from '../components/StatusPill';
import { displayName, formatCurrency, formatDate, shortId } from '../utils/format';

export function BillingPage() {
  const { user, roles, hasRole } = useAuth();
  const navigate = useNavigate();
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [showCreate, setShowCreate] = useState(false);

  const isPatient = roles.includes('PATIENT');
  const canCreate = hasRole('ADMIN', 'BILLING_STAFF');

  const scope = { patientId: isPatient ? user?.id : undefined };

  const list = useAsync(
    () =>
      billingApi.invoices.list({
        ...scope,
        status: (status as InvoiceStatus) || undefined,
        page,
        size: 20,
      }),
    [scope.patientId, status, page],
  );

  const patientNames = useAsync(
    () =>
      patientsApi
        .list({ page: 0, size: 1000 })
        .then((p) => p.content.map((pt) => ({ patientId: pt.patientId, name: displayName(pt.firstName, pt.lastName) }))),
    [],
  );

  const patientName = (id: string) => patientNames.data?.find((p) => p.patientId === id)?.name ?? shortId(id);

  return (
    <div className="page-stack">
      <section className="hero-card">
        <div>
          <p className="eyebrow">Billing</p>
          <h2>Keep revenue and payments moving without friction.</h2>
          <p>Monitor invoices, balances, and payment milestones across patient accounts.</p>
        </div>
        {canCreate ? (
          <button className="primary-btn" type="button" onClick={() => setShowCreate(true)}>
            Create invoice
          </button>
        ) : null}
      </section>

      <section className="card">
        <div className="section-header">
          <h3>Invoices</h3>
          <select
            className="search-input"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value);
              setPage(0);
            }}
          >
            <option value="">All statuses</option>
            {INVOICE_STATUSES.map((s) => (
              <option key={s} value={s}>
                {s.replace(/_/g, ' ')}
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
                  <th>Invoice</th>
                  <th>Patient</th>
                  <th>Status</th>
                  <th>Total</th>
                  <th>Remaining</th>
                  <th>Issued</th>
                </tr>
              </thead>
              <tbody>
                {list.data.content.map((invoice) => (
                  <tr key={invoice.invoiceId} className="row-link" onClick={() => navigate(`/billing/${invoice.invoiceId}`)}>
                    <td>{invoice.invoiceNumber}</td>
                    <td>{patientName(invoice.patientId)}</td>
                    <td>
                      <StatusPill status={invoice.status} />
                    </td>
                    <td>{formatCurrency(invoice.totalAmount, invoice.currency)}</td>
                    <td>{formatCurrency(invoice.remainingAmount, invoice.currency)}</td>
                    <td>{formatDate(invoice.issuedAt.slice(0, 10))}</td>
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
          <EmptyState message="No invoices found." />
        )}
      </section>

      {showCreate && canCreate ? (
        <CreateInvoiceModal
          onClose={() => setShowCreate(false)}
          onCreated={(id) => navigate(`/billing/${id}`)}
        />
      ) : null}
    </div>
  );
}

function CreateInvoiceModal({
  onClose,
  onCreated,
}: {
  onClose: () => void;
  onCreated: (invoiceId: string) => void;
}) {
  const [form, setForm] = useState<CreateInvoiceRequest>({
    patientId: '',
    currency: 'USD',
    items: [{ description: '', quantity: 1, unitPrice: 0 }],
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const patients = useAsync(() => patientsApi.list({ page: 0, size: 1000 }), []);

  const set = (key: keyof CreateInvoiceRequest, value: unknown) =>
    setForm((f) => ({ ...f, [key]: value }));

  const setItem = (index: number, key: keyof CreateInvoiceItemRequest, value: string | number) =>
    setForm((f) => {
      const items = f.items.map((item, i) => (i === index ? { ...item, [key]: value } : item));
      return { ...f, items };
    });

  const addItem = () =>
    setForm((f) => ({ ...f, items: [...f.items, { description: '', quantity: 1, unitPrice: 0 }] }));

  const removeItem = (index: number) =>
    setForm((f) => ({ ...f, items: f.items.filter((_, i) => i !== index) }));

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const created = await billingApi.invoices.create(form);
      onCreated(created.invoiceId);
    } catch (err) {
      if (err instanceof ApiError) setError(err.message);
      else setError('Unable to create invoice.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal title="Create invoice" onClose={onClose}>
      <form className="form" onSubmit={handleSubmit}>
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
        <div className="form-grid">
          <label className="field">
            <span>Currency *</span>
            <select value={form.currency} onChange={(e) => set('currency', e.target.value)}>
              <option value="USD">USD</option>
              <option value="EUR">EUR</option>
              <option value="INR">INR</option>
              <option value="GBP">GBP</option>
            </select>
          </label>
          <label className="field">
            <span>Due date</span>
            <input type="date" value={form.dueDate ?? ''} onChange={(e) => set('dueDate', e.target.value)} />
          </label>
          <label className="field">
            <span>Tax rate (%)</span>
            <input
              type="number"
              min={0}
              step="0.01"
              value={form.taxRate ?? 0}
              onChange={(e) => set('taxRate', Number(e.target.value))}
            />
          </label>
          <label className="field">
            <span>Discount</span>
            <input
              type="number"
              min={0}
              step="0.01"
              value={form.discount ?? 0}
              onChange={(e) => set('discount', Number(e.target.value))}
            />
          </label>
        </div>

        <div className="items-editor">
          <p className="items-title">Line items</p>
          {form.items.map((item, index) => (
            <div key={index} className="items-row">
              <select
                value={item.itemType ?? 'OTHER'}
                onChange={(e) => setItem(index, 'itemType', e.target.value)}
              >
                {INVOICE_ITEM_TYPES.map((t) => (
                  <option key={t} value={t}>
                    {t.replace(/_/g, ' ')}
                  </option>
                ))}
              </select>
              <input
                placeholder="Description *"
                required
                value={item.description}
                onChange={(e) => setItem(index, 'description', e.target.value)}
              />
              <input
                type="number"
                min={1}
                placeholder="Qty"
                value={item.quantity}
                onChange={(e) => setItem(index, 'quantity', Number(e.target.value))}
              />
              <input
                type="number"
                min={0}
                step="0.01"
                placeholder="Unit price"
                value={item.unitPrice}
                onChange={(e) => setItem(index, 'unitPrice', Number(e.target.value))}
              />
              <button
                type="button"
                className="icon-btn"
                aria-label="Remove line item"
                disabled={form.items.length === 1}
                onClick={() => removeItem(index)}
              >
                ×
              </button>
            </div>
          ))}
          <button type="button" className="btn small-btn" onClick={addItem}>
            + Add line item
          </button>
        </div>

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
            {submitting ? 'Saving…' : 'Create invoice'}
          </button>
        </div>
      </form>
    </Modal>
  );
}