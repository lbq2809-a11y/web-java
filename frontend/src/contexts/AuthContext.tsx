import {
  createContext,
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { authApi } from '@/api/auth.api';
import { registerUnauthorizedHandler } from '@/api/httpClient';
import { authStorage } from '@/lib/storage';
import type {
  LoginRequest,
  RegisterRequest,
  UserResponse,
} from '@/types/auth.types';

export interface AuthContextValue {
  user: UserResponse | null;
  token: string | null;
  isAuthenticated: boolean;
  isInitializing: boolean;
  login: (payload: LoginRequest) => Promise<UserResponse>;
  register: (payload: RegisterRequest) => Promise<UserResponse>;
  logout: () => void;
  refreshProfile: () => Promise<UserResponse>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();

  const [token, setToken] = useState<string | null>(() => authStorage.getToken());
  const [user, setUser] = useState<UserResponse | null>(() => authStorage.getUser());
  const [isInitializing, setIsInitializing] = useState<boolean>(() =>
    Boolean(authStorage.getToken() && !authStorage.getUser()),
  );

  const clearSession = useCallback(() => {
    authStorage.clear();
    setToken(null);
    setUser(null);
    queryClient.removeQueries({ queryKey: ['consultations'] });
  }, [queryClient]);

  // Đăng ký handler bắt 401 Unauthorized toàn cục từ httpClient
  useEffect(() => {
    return registerUnauthorizedHandler(() => {
      clearSession();
      if (location.pathname !== '/login') {
        navigate('/login', {
          replace: true,
          state: {
            from: `${location.pathname}${location.search}`,
            sessionExpired: true,
          },
        });
      }
    });
  }, [clearSession, location.pathname, location.search, navigate]);

  // Đồng bộ hồ sơ người dùng khi khởi động nếu đã có token
  useEffect(() => {
    let cancelled = false;
    const storedToken = authStorage.getToken();
    if (!storedToken) {
      setIsInitializing(false);
      return;
    }

    authApi
      .getMe()
      .then((profile) => {
        if (!cancelled) {
          authStorage.setUser(profile);
          setUser(profile);
        }
      })
      .catch(() => {
        // Lỗi 401 đã được interceptor xử lý; các lỗi mạng tạm thời giữ trạng thái hiện có
      })
      .finally(() => {
        if (!cancelled) {
          setIsInitializing(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(async (payload: LoginRequest): Promise<UserResponse> => {
    const authRes = await authApi.login(payload);
    authStorage.setToken(authRes.accessToken);
    authStorage.setUser(authRes.user);
    setToken(authRes.accessToken);
    setUser(authRes.user);
    return authRes.user;
  }, []);

  const register = useCallback(async (payload: RegisterRequest): Promise<UserResponse> => {
    const authRes = await authApi.register(payload);
    authStorage.setToken(authRes.accessToken);
    authStorage.setUser(authRes.user);
    setToken(authRes.accessToken);
    setUser(authRes.user);
    return authRes.user;
  }, []);

  const logout = useCallback(() => {
    clearSession();
    navigate('/', { replace: true });
  }, [clearSession, navigate]);

  const refreshProfile = useCallback(async (): Promise<UserResponse> => {
    const profile = await authApi.getMe();
    authStorage.setUser(profile);
    setUser(profile);
    return profile;
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      token,
      isAuthenticated: Boolean(token),
      isInitializing,
      login,
      register,
      logout,
      refreshProfile,
    }),
    [user, token, isInitializing, login, register, logout, refreshProfile],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
