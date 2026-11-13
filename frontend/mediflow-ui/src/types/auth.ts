export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  /** Access-token lifetime in seconds; used for proactive refresh. */
  expiresIn?: number;
  username: string;
  email: string;
  role: string;
}

export interface UserProfile {
  id: string;
  username: string;
  email: string;
  fullName?: string;
  phone?: string;
  status: string;
  roles: string[];
}

export interface LoginRequest {
  usernameOrEmail: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  fullName?: string;
  phone?: string;
  role?: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface PasswordResetRequest {
  email: string;
}

export interface PasswordResetConfirmRequest {
  token: string;
  newPassword: string;
}

export interface PasswordResetResponse {
  message?: string;
  /** Only returned by non-production environments (auth.password-reset.expose-token). */
  token?: string;
}

/** Account statuses returned by the auth service. */
export type AccountStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED' | 'PENDING_VERIFICATION' | 'LOCKED' | 'DELETED';
