export type AppointmentStatus =
  | 'SCHEDULED'
  | 'CONFIRMED'
  | 'IN_PROGRESS'
  | 'CANCELLED'
  | 'COMPLETED'
  | 'NO_SHOW';

export interface Appointment {
  appointmentId: string;
  patientId: string;
  doctorId: string;
  startTime: string;
  endTime: string;
  status: AppointmentStatus;
  notes?: string;
  reason: string;
  appointmentType?: string;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface CreateAppointmentRequest {
  patientId: string;
  doctorId: string;
  startTime: string;
  endTime: string;
  reason: string;
  notes?: string;
  appointmentType?: string;
}

export interface UpdateAppointmentRequest {
  startTime: string;
  endTime: string;
  notes?: string;
  reason?: string;
  appointmentType?: string;
}

export const APPOINTMENT_STATUSES: AppointmentStatus[] = [
  'SCHEDULED',
  'CONFIRMED',
  'IN_PROGRESS',
  'CANCELLED',
  'COMPLETED',
  'NO_SHOW',
];