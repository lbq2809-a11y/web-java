import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { authStorage } from '@/lib/storage';
import type { ErrorResponse } from '@/types/common.types';

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api';

type UnauthorizedHandler = () => void;
let unauthorizedHandler: UnauthorizedHandler | null = null;

export function registerUnauthorizedHandler(handler: UnauthorizedHandler): () => void {
  unauthorizedHandler = handler;
  return () => {
    if (unauthorizedHandler === handler) {
      unauthorizedHandler = null;
    }
  };
}

export interface NormalizedApiError {
  status: number;
  code: string;
  message: string;
  details: string[];
}

export function normalizeApiError(error: unknown): NormalizedApiError {
  if (axios.isAxiosError(error)) {
    const axiosErr = error as AxiosError<Partial<ErrorResponse>>;
    const status = axiosErr.response?.status ?? 0;
    const data = axiosErr.response?.data;

    if (data && typeof data === 'object' && typeof data.message === 'string') {
      return {
        status,
        code: typeof data.code === 'string' ? data.code : 'API_ERROR',
        message: data.message,
        details: Array.isArray(data.details) ? data.details : [],
      };
    }

    if (status === 0) {
      return {
        status: 0,
        code: 'NETWORK_ERROR',
        message: 'Không thể kết nối tới máy chủ. Vui lòng kiểm tra các dịch vụ backend.',
        details: [],
      };
    }

    return {
      status,
      code: 'HTTP_ERROR',
      message: axiosErr.message || 'Đã xảy ra lỗi khi gọi hệ thống.',
      details: [],
    };
  }

  if (error instanceof Error) {
    return {
      status: 0,
      code: 'UNKNOWN_ERROR',
      message: error.message,
      details: [],
    };
  }

  return {
    status: 0,
    code: 'UNKNOWN_ERROR',
    message: 'Đã xảy ra lỗi không xác định.',
    details: [],
  };
}

export const httpClient = axios.create({
  baseURL,
  timeout: 20000,
  headers: {
    'Content-Type': 'application/json',
  },
});

httpClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = authStorage.getToken();
  const isPublicQrEndpoint = config.url?.includes('/v1/auth/qr/session') ?? false;
  if (token && !isPublicQrEndpoint) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  if (config.data instanceof FormData) {
    config.headers.delete('Content-Type');
  }
  return config;
});

httpClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    const status = error.response?.status;
    const requestUrl = error.config?.url ?? '';

    // Bỏ qua xử lý redirect 401 cho endpoint đăng nhập/đăng ký và API tra cứu tồn kho công khai
    const isAuthAttemptEndpoint =
      requestUrl.includes('/auth/login') || requestUrl.includes('/auth/register');
    const isPublicStockCheck = requestUrl.includes('/v1/stocks');

    if (status === 401 && !isAuthAttemptEndpoint && !isPublicStockCheck) {
      authStorage.clear();
      if (unauthorizedHandler) {
        unauthorizedHandler();
      } else if (window.location.pathname !== '/login') {
        window.location.assign('/login');
      }
    }

    return Promise.reject(error);
  },
);
