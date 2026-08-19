import { api } from './client';
import { Page } from '../types/common';
import {
  Appointment,
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
  create: (body: CreateAppointmentRequest) =>
    api<Appointment>('/api/v1/appointments', { method: 'POST', body }),
  update: (appointmentId: string, body: UpdateAppointmentRequest) =>
    api<Appointment>(`/api/v1/appointments/${appointmentId}`, { method: 'PUT', body }),
  cancel: (appointmentId: string) =>
    api<Appointment>(`/api/v1/appointments/${appointmentId}/cancel`, { method: 'PATCH' }),
};