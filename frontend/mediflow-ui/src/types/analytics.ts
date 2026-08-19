export interface DashboardSummary {
  from: string;
  to: string;
  appointments: number;
  completedAppointments: number;
  cancelledAppointments: number;
  invoicedRevenue: number;
  collectedRevenue: number;
  refundedRevenue: number;
  outstandingRevenue: number;
  activePatients: number;
  doctors: number;
}

export interface StatusCount {
  status: string;
  count: number;
}

export interface DailyCount {
  date: string;
  count: number;
}

export interface AppointmentsStats {
  from: string;
  to: string;
  totalAppointments: number;
  completedAppointments: number;
  cancelledAppointments: number;
  statusBreakdown: StatusCount[];
  dailyTrend: DailyCount[];
}

export interface MonthlyRevenue {
  month: string;
  invoiced: number;
  collected: number;
  refunded: number;
}

export interface RevenueStats {
  from: string;
  to: string;
  invoiced: number;
  collected: number;
  refunded: number;
  outstanding: number;
  monthlyTrend: MonthlyRevenue[];
}

export interface DoctorStat {
  doctorId: string;
  appointments: number;
  completed: number;
}

export interface PatientActivity {
  patientId: string;
  appointments: number;
  firstAppointment?: string;
}

export interface PatientsStats {
  from: string;
  to: string;
  totalPatients: number;
  newPatients: number;
  mostActive: PatientActivity[];
}