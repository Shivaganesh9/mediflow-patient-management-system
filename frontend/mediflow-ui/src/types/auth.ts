export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
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