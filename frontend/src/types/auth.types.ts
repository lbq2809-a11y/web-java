export type Role = 'KHACH_HANG' | 'DUOC_SI' | 'ADMIN';

export interface UserResponse {
  id: string;
  email: string;
  fullName: string;
  phone: string | null;
  role: Role;
  active: boolean;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserResponse;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
  phone?: string;
}
