import { httpClient } from './httpClient';
import type {
  AuthResponse,
  ConfirmQrResponse,
  CreateQrSessionResponse,
  LoginRequest,
  QrSessionStatusResponse,
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

  async createQrSession(): Promise<CreateQrSessionResponse> {
    const response = await httpClient.post<CreateQrSessionResponse>('/v1/auth/qr/session');
    return response.data;
  },

  async getQrSessionStatus(
    sessionId: string,
    pollSecret: string,
  ): Promise<QrSessionStatusResponse> {
    const response = await httpClient.get<QrSessionStatusResponse>(
      `/v1/auth/qr/session/${sessionId}`,
      {
        headers: {
          'X-QR-Secret': pollSecret,
        },
      },
    );
    return response.data;
  },

  async confirmQrSession(sessionId: string): Promise<ConfirmQrResponse> {
    const response = await httpClient.post<ConfirmQrResponse>('/v1/auth/qr/confirm', {
      sessionId,
    });
    return response.data;
  },
};
