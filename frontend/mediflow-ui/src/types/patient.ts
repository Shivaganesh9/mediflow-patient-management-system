export interface Patient {
  patientId: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  dateOfBirth?: string;
  address?: string;
  medicalHistory?: string;
  emergencyContactName?: string;
  emergencyContactPhone?: string;
  insuranceProvider?: string;
  insuranceMemberId?: string;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface CreatePatientRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  dateOfBirth?: string;
  address?: string;
  medicalHistory?: string;
  emergencyContactName?: string;
  emergencyContactPhone?: string;
  insuranceProvider?: string;
  insuranceMemberId?: string;
  userId?: string;
}