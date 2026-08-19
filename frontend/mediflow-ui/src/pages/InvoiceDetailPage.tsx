import { FormEvent, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useAsync } from '../hooks/useAsync';
import { billingApi } from '../api/billing';
import { PAYMENT_METHODS, PaymentMethod, RecordPaymentRequest } from '../types/billing';
import { ApiError } from '../types/common';
import { LoadingState, ErrorState, EmptyState } from '../components/PageState';
import { Modal } from '../components/Modal';
import { StatusPill } from '../components/StatusPill';
import { formatCurrency, formatDate, formatDateTime } from '../utils/format';

export function InvoiceDetailPage() {
  const { invoiceId = '' } = useParams();
  const { hasRole } = useAuth();
  const [showPayment, setShowPayment] = useState(false);
  const [refundingId, setRefundingId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const canManage = hasRole('ADMIN', 'BILLING_STAFF');

  const detail = useAsync(() => billingApi.invoices.get(invoiceId), [invoiceId]);

  const handleRefund = async (paymentId: string) => {
    if (!window.confirm('Refund this payment? The invoice balance will be restored.')) return;
    setRefundingId(paymentId);
    setError(null);
    try {
      await billingApi.refund(paymentId);
      detail.reload();
    } catch (err) {
      if (err instanceof ApiError) setError(err.message);
      else setError('Unable to refund payment.');
    } finally {
      setRefundingId(null);
    }
  };

  const handleCancelInvoice = async () => {
    if (!window.confirm('Cancel this invoice? This cannot be undone.')) return;
    setError(null);
    try {
      await billingApi.invoices.cancel(invoiceId);
      detail.reload();
    } catch (err) {
      if (err instanceof ApiError) setError(err.message);
      else setError('Unable to cancel invoice.');
    }
  };

  if (detail.loading) return <LoadingState />;
  if (detail.error) return <ErrorState message={detail.error} />;
  if (!detail.data) return <EmptyState message="Invoice not found." />;

  const invoice = detail.data;
  const canPay = canManage && invoice.status !== 'CANCELLED' && invoice.remainingAmount > 0;
  const canCancel = canManage && invoice.status !== 'CANCELLED';

  return (
    <div className="page-stack">
      <section className="hero-card">
        <div>
          <p className="eyebrow">Invoice</p>
          <h2>{invoice.invoiceNumber}</h2>
          <p>
            Issued {formatDate(invoice.issuedAt.slice(0, 10))} · Patient {invoice.patientId.slice(0, 8)}
          </p>
        </div>
        <div className="hero-actions">
          {canPay ? (
            <button className="primary-btn" type="button" onClick={() => setShowPayment(true)}>
              Record payment
            </button>
          ) : null}
          {canCancel ? (
            <button className="btn danger-btn" type="button" onClick={() => void handleCancelInvoice()}>
              Cancel invoice
            </button>
          ) : null}
          <Link to="/billing" className="btn secondary-btn">
            Back
          </Link>
        </div>
      </section>

      {error ? <ErrorState message={error} /> : null}

      <div className="metrics-grid metrics-grid-4">
        <div className="metric-card">
          <p>Status</p>
          <StatusPill status={invoice.status} />
        </div>
        <div className="metric-card">
          <p>Total</p>
          <strong>{formatCurrency(invoice.totalAmount, invoice.currency)}</strong>
        </div>
        <div className="metric-card">
          <p>Paid</p>
          <strong>{formatCurrency(invoice.paidAmount, invoice.currency)}</strong>
        </div>
        <div className="metric-card">
          <p>Remaining</p>
          <strong>{formatCurrency(invoice.remainingAmount, invoice.currency)}</strong>
        </div>
      </div>

      <div className="dashboard-grid">
        <section className="card">
          <div className="section-header">
            <h3>Line items</h3>
          </div>
          <table className="table">
            <thead>
              <tr>
                <th>Item</th>
                <th>Qty</th>
                <th>Unit price</th>
                <th>Amount</th>
              </tr>
            </thead>
            <tbody>
              {invoice.items.map((item) => (
                <tr key={item.itemId}>
                  <td>
                    {item.description}
                    <br />
                    <span className="muted">{item.itemType.replace(/_/g, ' ')}</span>
                  </td>
                  <td>{item.quantity}</td>
                  <td>{formatCurrency(item.unitPrice, invoice.currency)}</td>
                  <td>{formatCurrency(item.amount, invoice.currency)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <dl className="totals">
            <div>
              <dt>Subtotal</dt>
              <dd>{formatCurrency(invoice.subtotal, invoice.currency)}</dd>
            </div>
            <div>
              <dt>Tax ({invoice.taxRate}%)</dt>
              <dd>{formatCurrency(invoice.taxAmount, invoice.currency)}</dd>
            </div>
            <div>
              <dt>Discount</dt>
              <dd>−{formatCurrency(invoice.discount, invoice.currency)}</dd>
            </div>
            <div>
              <dt>Due date</dt>
              <dd>{formatDate(invoice.dueDate)}</dd>
            </div>
          </dl>
        </section>

        <section className="card">
          <div className="section-header">
            <h3>Payments</h3>
          </div>
          {invoice.payments.length === 0 ? (
            <EmptyState message="No payments recorded yet." />
          ) : (
            <div className="stack-list">
              {invoice.payments.map((payment) => (
                <div key={payment.paymentId} className="list-item">
                  <div>
                    <strong>{formatCurrency(payment.amount, invoice.currency)}</strong>
                    <p>
                      {payment.method.replace(/_/g, ' ')} · {formatDateTime(payment.paidAt)}
                    </p>
                  </div>
                  <div className="muted">
                    <StatusPill status={payment.status} />
                    {canManage && payment.status !== 'REFUNDED' ? (
                      <button
                        className="btn small-btn"
                        type="button"
                        disabled={refundingId === payment.paymentId}
                        onClick={() => void handleRefund(payment.paymentId)}
                      >
                        {refundingId === payment.paymentId ? '…' : 'Refund'}
                      </button>
                    ) : null}
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>
      </div>

      {invoice.notes ? (
        <section className="card">
          <div className="section-header">
            <h3>Notes</h3>
          </div>
          <p className="muted">{invoice.notes}</p>
        </section>
      ) : null}

      {showPayment && canManage ? (
        <PaymentModal
          invoiceId={invoice.invoiceId}
          currency={invoice.currency}
          remaining={invoice.remainingAmount}
          onClose={() => setShowPayment(false)}
          onSaved={() => {
            setShowPayment(false);
            detail.reload();
          }}
        />
      ) : null}
    </div>
  );
}

function PaymentModal({
  invoiceId,
  currency,
  remaining,
  onClose,
  onSaved,
}: {
  invoiceId: string;
  currency: string;
  remaining: number;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [form, setForm] = useState<RecordPaymentRequest>({ amount: remaining, method: 'CARD' });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await billingApi.invoices.recordPayment(invoiceId, form);
      onSaved();
    } catch (err) {
      if (err instanceof ApiError) setError(err.message);
      else setError('Unable to record payment.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal title="Record payment" onClose={onClose}>
      <form className="form" onSubmit={handleSubmit}>
        <label className="field">
          <span>Amount *</span>
          <input
            type="number"
            min={0.01}
            max={remaining}
            step="0.01"
            required
            value={form.amount}
            onChange={(e) => setForm((f) => ({ ...f, amount: Number(e.target.value) }))}
          />
          <small>
            {formatCurrency(remaining, currency)} remaining
          </small>
        </label>
        <label className="field">
          <span>Method *</span>
          <select value={form.method} onChange={(e) => setForm((f) => ({ ...f, method: e.target.value as PaymentMethod }))}>
            {PAYMENT_METHODS.map((m) => (
              <option key={m} value={m}>
                {m.replace(/_/g, ' ')}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span>Reference</span>
          <input
            value={form.reference ?? ''}
            onChange={(e) => setForm((f) => ({ ...f, reference: e.target.value }))}
          />
        </label>
        {error ? <p className="form-error">{error}</p> : null}
        <div className="form-actions">
          <button type="button" className="btn secondary-btn" onClick={onClose}>
            Cancel
          </button>
          <button className="primary-btn" type="submit" disabled={submitting}>
            {submitting ? 'Saving…' : 'Record payment'}
          </button>
        </div>
      </form>
    </Modal>
  );
}