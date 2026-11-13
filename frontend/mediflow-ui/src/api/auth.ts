import { api } from './client';
import {
  AuthResponse,
  ChangePasswordRequest,
  LoginRequest,
  PasswordResetConfirmRequest,
  PasswordResetRequest,
  PasswordResetResponse,
  RegisterRequest,
  UserProfile,
} from '../types/auth';

export const authApi = {
  login: (body: LoginRequest) => api<AuthResponse>('/api/v1/auth/login', { method: 'POST', body, auth: false }),
  register: (body: RegisterRequest) => api<AuthResponse>('/api/v1/auth/register', { method: 'POST', body, auth: false }),
  me: () => api<UserProfile>('/api/v1/auth/me'),
  logout: () => api<void>('/api/v1/auth/logout', { method: 'POST' }),
  changePassword: (body: ChangePasswordRequest) =>
    api<void>('/api/v1/auth/change-password', { method: 'POST', body }),
  requestPasswordReset: (body: PasswordResetRequest) =>
    api<PasswordResetResponse>('/api/v1/auth/password-reset/request', { method: 'POST', body, auth: false }),
  confirmPasswordReset: (body: PasswordResetConfirmRequest) =>
    api<void>('/api/v1/auth/password-reset/confirm', { method: 'POST', body, auth: false }),
};
