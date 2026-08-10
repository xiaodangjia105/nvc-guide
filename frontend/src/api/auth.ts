import { request } from './request';

// ========== 类型 ==========

export interface User {
  id: number;
  username: string;
  email: string;
  createdAt: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  user: User;
}

// ========== API ==========

export const authApi = {
  login: (data: LoginRequest) =>
    request.post<AuthResponse>('/api/auth/login', data),

  register: (data: RegisterRequest) =>
    request.post<AuthResponse>('/api/auth/register', data),

  getMe: () =>
    request.get<User>('/api/auth/me'),
};
