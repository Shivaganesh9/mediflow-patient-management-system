export type MedicalRecordType =
  | 'CONSULTATION'
  | 'FOLLOW_UP'
  | 'DIAGNOSIS'
  | 'LAB_REPORT'
  | 'PROCEDURE'
  | 'OTHER';

export interface MedicalRecord {
  id: string;
  recordId: string;
  patientId: string;
  appointmentId?: string;
  doctorId?: string;
  encounterDate: string;
  recordType: MedicalRecordType;
  chiefComplaint: string;
  diagnosis?: string;
  treatmentPlan?: string;
  notes: string;
  version: number;
  createdAt: string;
  updatedAt: string;
  createdBy?: string;
  updatedBy?: string;
}

export interface CreateMedicalRecordRequest {
  patientId: string;
  encounterDate: string;
  chiefComplaint: string;
  notes: string;
  appointmentId?: string;
  doctorId?: string;
  diagnosis?: string;
  treatmentPlan?: string;
  recordType?: MedicalRecordType;
}

export type UpdateMedicalRecordRequest = Partial<CreateMedicalRecordRequest>;

export const MEDICAL_RECORD_TYPES: MedicalRecordType[] = [
  'CONSULTATION',
  'FOLLOW_UP',
  'DIAGNOSIS',
  'LAB_REPORT',
  'PROCEDURE',
  'OTHER',
];