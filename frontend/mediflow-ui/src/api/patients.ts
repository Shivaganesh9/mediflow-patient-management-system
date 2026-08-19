import { api } from './client';
import { Page } from '../types/common';
import { CreatePatientRequest, Patient } from '../types/patient';

export interface PatientListParams {
  search?: string;
  page?: number;
  size?: number;
}

export const patientsApi = {
  list: (params: PatientListParams) =>
    api<Page<Patient>>('/api/v1/patients', { params: { ...params, page: params.page ?? 0, size: params.size ?? 20 } }),
  get: (patientId: string) => api<Patient>(`/api/v1/patients/${patientId}`),
  create: (body: CreatePatientRequest) => api<Patient>('/api/v1/patients', { method: 'POST', body }),
  update: (patientId: string, body: CreatePatientRequest) =>
    api<Patient>(`/api/v1/patients/${patientId}`, { method: 'PUT', body }),
  remove: (patientId: string) => api<void>(`/api/v1/patients/${patientId}`, { method: 'DELETE' }),
};