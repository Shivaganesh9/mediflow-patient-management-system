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
  createdBy?: string;
  updatedBy?: string;
  version: number;
}

export type AppointmentHistoryAction =
  | 'CREATED'
  | 'RESCHEDULED'
  | 'CONFIRMED'
  | 'STARTED'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'NO_SHOW';

/** One entry of an appointment's server-side audit trail. */
export interface AppointmentHistoryEntry {
  historyId: string;
  appointmentId: string;
  action: AppointmentHistoryAction;
  fromStatus?: AppointmentStatus;
  toStatus: AppointmentStatus;
  actor?: string;
  details?: string;
  occurredAt: string;
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