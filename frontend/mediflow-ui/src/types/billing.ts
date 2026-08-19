export type InvoiceStatus = 'PENDING' | 'PARTIALLY_PAID' | 'PAID' | 'CANCELLED';
export type PaymentStatus = 'COMPLETED' | 'REFUNDED';
export type PaymentMethod =
  | 'CASH'
  | 'CARD'
  | 'INSURANCE'
  | 'BANK_TRANSFER'
  | 'ONLINE'
  | 'OTHER';
export type InvoiceItemType =
  | 'CONSULTATION'
  | 'MEDICATION'
  | 'LAB_TEST'
  | 'PROCEDURE'
  | 'ROOM'
  | 'OTHER';

export interface InvoiceItem {
  itemId: string;
  itemType: InvoiceItemType;
  description: string;
  quantity: number;
  unitPrice: number;
  amount: number;
}

export interface Payment {
  paymentId: string;
  invoiceId: string;
  amount: number;
  method: PaymentMethod;
  reference?: string;
  status: PaymentStatus;
  paidAt: string;
}

export interface Invoice {
  invoiceId: string;
  invoiceNumber: string;
  patientId: string;
  appointmentId?: string;
  status: InvoiceStatus;
  currency: string;
  subtotal: number;
  taxRate: number;
  taxAmount: number;
  discount: number;
  totalAmount: number;
  paidAmount: number;
  remainingAmount: number;
  dueDate?: string;
  issuedAt: string;
  paidAt?: string;
  cancelledAt?: string;
  notes?: string;
  version: number;
  items: InvoiceItem[];
  payments: Payment[];
}

export interface CreateInvoiceItemRequest {
  itemType?: InvoiceItemType;
  description: string;
  quantity: number;
  unitPrice: number;
}

export interface CreateInvoiceRequest {
  patientId: string;
  appointmentId?: string;
  currency: string;
  dueDate?: string;
  taxRate?: number;
  discount?: number;
  notes?: string;
  items: CreateInvoiceItemRequest[];
}

export interface UpdateInvoiceRequest {
  taxRate?: number;
  discount?: number;
  dueDate?: string;
  notes?: string;
  items?: CreateInvoiceItemRequest[];
}

export interface RecordPaymentRequest {
  amount: number;
  method: PaymentMethod;
  reference?: string;
}

export const INVOICE_STATUSES: InvoiceStatus[] = ['PENDING', 'PARTIALLY_PAID', 'PAID', 'CANCELLED'];
export const PAYMENT_METHODS: PaymentMethod[] = ['CASH', 'CARD', 'INSURANCE', 'BANK_TRANSFER', 'ONLINE', 'OTHER'];
export const INVOICE_ITEM_TYPES: InvoiceItemType[] = [
  'CONSULTATION',
  'MEDICATION',
  'LAB_TEST',
  'PROCEDURE',
  'ROOM',
  'OTHER',
];