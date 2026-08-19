export interface Doctor {
  doctorId: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  specialty: string;
  yearsOfExperience: number;
  department: string;
  qualification?: string;
  consultationFee: number;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface CreateDoctorRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  specialty: string;
  yearsOfExperience: number;
  department: string;
  qualification?: string;
  consultationFee: number;
  userId?: string;
}

export interface DoctorAvailability {
  id: string;
  availabilityId: string;
  doctorId: string;
  dayOfWeek: string;
  startTime: string;
  endTime: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AddAvailabilityRequest {
  dayOfWeek: string;
  startTime: string;
  endTime: string;
}

export interface DoctorLeave {
  id: string;
  leaveId: string;
  doctorId: string;
  startDate: string;
  endDate: string;
  reason?: string;
  createdAt: string;
  updatedAt: string;
}

export interface AddLeaveRequest {
  startDate: string;
  endDate: string;
  reason?: string;
}