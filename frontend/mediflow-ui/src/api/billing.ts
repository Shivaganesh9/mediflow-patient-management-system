import { api } from './client';
import { Page } from '../types/common';
import {
  CreateInvoiceRequest,
  Invoice,
  InvoiceStatus,
  Payment,
  RecordPaymentRequest,
  UpdateInvoiceRequest,
} from '../types/billing';

export interface InvoiceListParams {
  status?: InvoiceStatus;
  patientId?: string;
  appointmentId?: string;
  page?: number;
  size?: number;
}

export const billingApi = {
  invoices: {
    list: (params: InvoiceListParams) =>
      api<Page<Invoice>>('/api/v1/invoices', {
        params: { ...params, page: params.page ?? 0, size: params.size ?? 20 },
      }),
    get: (invoiceId: string) => api<Invoice>(`/api/v1/invoices/${invoiceId}`),
    create: (body: CreateInvoiceRequest) =>
      api<Invoice>('/api/v1/invoices', { method: 'POST', body }),
    update: (invoiceId: string, body: UpdateInvoiceRequest) =>
      api<Invoice>(`/api/v1/invoices/${invoiceId}`, { method: 'PUT', body }),
    cancel: (invoiceId: string) =>
      api<Invoice>(`/api/v1/invoices/${invoiceId}/cancel`, { method: 'POST' }),
    payments: (invoiceId: string) => api<Payment[]>(`/api/v1/invoices/${invoiceId}/payments`),
    recordPayment: (invoiceId: string, body: RecordPaymentRequest) =>
      api<Invoice>(`/api/v1/invoices/${invoiceId}/payments`, { method: 'POST', body }),
  },
  refund: (paymentId: string) =>
    api<Invoice>(`/api/v1/payments/${paymentId}/refund`, { method: 'POST' }),
};