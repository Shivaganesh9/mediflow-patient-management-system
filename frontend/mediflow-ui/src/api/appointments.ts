import { api } from './client';
import { Page } from '../types/common';
import {
  Appointment,
  AppointmentHistoryEntry,
  AppointmentStatus,
  CreateAppointmentRequest,
  UpdateAppointmentRequest,
} from '../types/appointment';

export interface AppointmentListParams {
  status?: AppointmentStatus;
  patientId?: string;
  doctorId?: string;
  fromTime?: string;
  toTime?: string;
  page?: number;
  size?: number;
}

export const appointmentsApi = {
  list: (params: AppointmentListParams) =>
    api<Page<Appointment>>('/api/v1/appointments', {
      params: { ...params, page: params.page ?? 0, size: params.size ?? 20 },
    }),
  get: (appointmentId: string) => api<Appointment>(`/api/v1/appointments/${appointmentId}`),
  history: (appointmentId: string) =>
    api<AppointmentHistoryEntry[]>(`/api/v1/appointments/${appointmentId}/history`),
  create: (body: CreateAppointmentRequest) =>
    api<Appointment>('/api/v1/appointments', { method: 'POST', body }),
  update: (appointmentId: string, body: UpdateAppointmentRequest) =>
    api<Appointment>(`/api/v1/appointments/${appointmentId}`, { method: 'PUT', body }),
  confirm: (appointmentId: string) =>
    api<Appointment>(`/api/v1/appointments/${appointmentId}/confirm`, { method: 'PATCH' }),
  start: (appointmentId: string) =>
    api<Appointment>(`/api/v1/appointments/${appointmentId}/start`, { method: 'PATCH' }),
  complete: (appointmentId: string) =>
    api<Appointment>(`/api/v1/appointments/${appointmentId}/complete`, { method: 'PATCH' }),
  noShow: (appointmentId: string) =>
    api<Appointment>(`/api/v1/appointments/${appointmentId}/no-show`, { method: 'PATCH' }),
  cancel: (appointmentId: string) =>
    api<Appointment>(`/api/v1/appointments/${appointmentId}/cancel`, { method: 'PATCH' }),
};
