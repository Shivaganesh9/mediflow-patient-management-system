import { api } from './client';
import { AuthResponse, LoginRequest, RegisterRequest, UserProfile } from '../types/auth';

export const authApi = {
  login: (body: LoginRequest) => api<AuthResponse>('/api/v1/auth/login', { method: 'POST', body, auth: false }),
  register: (body: RegisterRequest) => api<AuthResponse>('/api/v1/auth/register', { method: 'POST', body, auth: false }),
  me: () => api<UserProfile>('/api/v1/auth/me'),
  logout: () => api<void>('/api/v1/auth/logout', { method: 'POST' }),
};