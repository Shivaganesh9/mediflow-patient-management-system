import { api } from './client';
import { Page } from '../types/common';
import {
  AddAvailabilityRequest,
  AddLeaveRequest,
  CreateDoctorRequest,
  Doctor,
  DoctorAvailability,
  DoctorLeave,
  UpdateAvailabilityRequest,
  UpdateLeaveRequest,
} from '../types/doctor';

export interface DoctorListParams {
  search?: string;
  department?: string;
  page?: number;
  size?: number;
}

export const doctorsApi = {
  list: (params: DoctorListParams) =>
    api<Page<Doctor>>('/api/v1/doctors', { params: { ...params, page: params.page ?? 0, size: params.size ?? 20 } }),
  get: (doctorId: string) => api<Doctor>(`/api/v1/doctors/${doctorId}`),
  create: (body: CreateDoctorRequest) => api<Doctor>('/api/v1/doctors', { method: 'POST', body }),
  update: (doctorId: string, body: CreateDoctorRequest) =>
    api<Doctor>(`/api/v1/doctors/${doctorId}`, { method: 'PUT', body }),
  availability: {
    list: (doctorId: string) => api<DoctorAvailability[]>(`/api/v1/doctors/${doctorId}/availability`),
    create: (doctorId: string, body: AddAvailabilityRequest) =>
      api<DoctorAvailability>(`/api/v1/doctors/${doctorId}/availability`, { method: 'POST', body }),
    update: (doctorId: string, availabilityId: string, body: UpdateAvailabilityRequest) =>
      api<DoctorAvailability>(`/api/v1/doctors/${doctorId}/availability/${availabilityId}`, {
        method: 'PUT',
        body,
      }),
    deactivate: (doctorId: string, availabilityId: string) =>
      api<DoctorAvailability>(`/api/v1/doctors/${doctorId}/availability/${availabilityId}`, {
        method: 'DELETE',
      }),
  },
  leaves: {
    list: (doctorId: string) => api<DoctorLeave[]>(`/api/v1/doctors/${doctorId}/leaves`),
    create: (doctorId: string, body: AddLeaveRequest) =>
      api<DoctorLeave>(`/api/v1/doctors/${doctorId}/leaves`, { method: 'POST', body }),
    update: (doctorId: string, leaveId: string, body: UpdateLeaveRequest) =>
      api<DoctorLeave>(`/api/v1/doctors/${doctorId}/leaves/${leaveId}`, { method: 'PUT', body }),
    cancel: (doctorId: string, leaveId: string) =>
      api<DoctorLeave>(`/api/v1/doctors/${doctorId}/leaves/${leaveId}`, { method: 'DELETE' }),
  },
};
