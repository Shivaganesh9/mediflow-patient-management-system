import { api } from './client';
import {
  AppointmentsStats,
  DashboardSummary,
  DoctorStat,
  PatientsStats,
  RevenueStats,
} from '../types/analytics';

export interface AnalyticsRange {
  from?: string;
  to?: string;
}

export const analyticsApi = {
  dashboardSummary: (range: AnalyticsRange = {}) =>
    api<DashboardSummary>('/api/v1/analytics/dashboard/summary', { params: range }),
  appointments: (range: AnalyticsRange = {}) =>
    api<AppointmentsStats>('/api/v1/analytics/appointments', { params: range }),
  revenue: (range: AnalyticsRange = {}) =>
    api<RevenueStats>('/api/v1/analytics/revenue', { params: range }),
  doctors: (range: AnalyticsRange = {}) =>
    api<DoctorStat[]>('/api/v1/analytics/doctors', { params: range }),
  patients: (range: AnalyticsRange = {}) =>
    api<PatientsStats>('/api/v1/analytics/patients', { params: range }),
};