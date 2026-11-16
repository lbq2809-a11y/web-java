import { httpClient } from './httpClient';
import type {
  AuthResponse,
  LoginRequest,
  RegisterRequest,
  UserResponse,
} from '@/types/auth.types';

export const authApi = {
  async login(payload: LoginRequest): Promise<AuthResponse> {
    const response = await httpClient.post<AuthResponse>('/v1/auth/login', payload);
    return response.data;
  },

  async register(payload: RegisterRequest): Promise<AuthResponse> {
    const response = await httpClient.post<AuthResponse>('/v1/auth/register', payload);
    return response.data;
  },

  async getMe(): Promise<UserResponse> {
    const response = await httpClient.get<UserResponse>('/v1/auth/me');
    return response.data;
  },
};
