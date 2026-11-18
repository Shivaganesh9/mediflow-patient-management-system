import { api } from './client';
import { Page } from '../types/common';
import {
  CreateMedicalRecordRequest,
  MedicalRecord,
  MedicalRecordHistoryEntry,
  MedicalRecordType,
  UpdateMedicalRecordRequest,
} from '../types/medical-record';

export interface MedicalRecordListParams {
  patientId?: string;
  doctorId?: string;
  recordType?: MedicalRecordType;
  fromDate?: string;
  toDate?: string;
  page?: number;
  size?: number;
}

export const medicalRecordsApi = {
  list: (params: MedicalRecordListParams) =>
    api<Page<MedicalRecord>>('/api/v1/medical-records', {
      params: { ...params, page: params.page ?? 0, size: params.size ?? 20 },
    }),
  get: (recordId: string) => api<MedicalRecord>(`/api/v1/medical-records/${recordId}`),
  history: (recordId: string) =>
    api<MedicalRecordHistoryEntry[]>(`/api/v1/medical-records/${recordId}/history`),
  create: (body: CreateMedicalRecordRequest) =>
    api<MedicalRecord>('/api/v1/medical-records', { method: 'POST', body }),
  update: (recordId: string, body: UpdateMedicalRecordRequest) =>
    api<MedicalRecord>(`/api/v1/medical-records/${recordId}`, { method: 'PUT', body }),
};