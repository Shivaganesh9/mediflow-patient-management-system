import { Route, Routes } from 'react-router-dom';
import { AppShell } from './components/AppShell';
import { ProtectedRoute } from './routes/ProtectedRoute';
import { RoleRoute } from './routes/RoleRoute';
import { NotFound } from './components/PageState';
import { LoginPage } from './pages/LoginPage';
import { ChangePasswordPage } from './pages/ChangePasswordPage';
import { ForgotPasswordPage } from './pages/ForgotPasswordPage';
import { ResetPasswordPage } from './pages/ResetPasswordPage';
import { DashboardPage } from './pages/DashboardPage';
import { PatientsPage } from './pages/PatientsPage';
import { PatientDetailPage } from './pages/PatientDetailPage';
import { DoctorsPage } from './pages/DoctorsPage';
import { DoctorDetailPage } from './pages/DoctorDetailPage';
import { AppointmentsPage } from './pages/AppointmentsPage';
import { MedicalRecordsPage } from './pages/MedicalRecordsPage';
import { BillingPage } from './pages/BillingPage';
import { InvoiceDetailPage } from './pages/InvoiceDetailPage';
import { NotificationsPage } from './pages/NotificationsPage';
import { AnalyticsPage } from './pages/AnalyticsPage';

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route
        element={
          <ProtectedRoute>
            <AppShell />
          </ProtectedRoute>
        }
      >
        <Route path="/" element={<DashboardPage />} />
        <Route path="/patients" element={<PatientsPage />} />
        <Route path="/patients/:patientId" element={<PatientDetailPage />} />
        <Route path="/doctors" element={<DoctorsPage />} />
        <Route path="/doctors/:doctorId" element={<DoctorDetailPage />} />
        <Route path="/appointments" element={<AppointmentsPage />} />
        <Route
          path="/medical-records"
          element={
            <RoleRoute roles={['ADMIN', 'DOCTOR', 'PATIENT']}>
              <MedicalRecordsPage />
            </RoleRoute>
          }
        />
        <Route path="/billing" element={<BillingPage />} />
        <Route path="/billing/:invoiceId" element={<InvoiceDetailPage />} />
        <Route path="/account/password" element={<ChangePasswordPage />} />
        <Route path="/notifications" element={<NotificationsPage />} />
        <Route
          path="/analytics"
          element={
            <RoleRoute roles={['ADMIN', 'DOCTOR']}>
              <AnalyticsPage />
            </RoleRoute>
          }
        />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  );
}