import React from 'react';

const invoices = [
  { id: 'INV-2048', patient: 'Ava Patel', amount: '$480', status: 'Paid' },
  { id: 'INV-2049', patient: 'Noah Kim', amount: '$120', status: 'Pending' },
  { id: 'INV-2050', patient: 'Mina Alvarez', amount: '$890', status: 'Reviewed' }
];

export function BillingPage() {
  return (
    <div className="page-stack">
      <section className="hero-card">
        <div>
          <p className="eyebrow">Billing</p>
          <h2>Keep revenue and payments moving without friction.</h2>
          <p>Monitor invoices, balances, and payment milestones across patient accounts.</p>
        </div>
        <button className="primary-btn">Create invoice</button>
      </section>

      <section className="card">
        <div className="section-header">
          <h3>Recent invoices</h3>
          <a href="#/notifications">Notifications</a>
        </div>
        <div className="stack-list">
          {invoices.map((invoice) => (
            <div key={invoice.id} className="list-item">
              <div>
                <strong>{invoice.id}</strong>
                <p>{invoice.patient}</p>
              </div>
              <div className="muted">
                <span>{invoice.amount}</span>
                <span className="pill">{invoice.status}</span>
              </div>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
