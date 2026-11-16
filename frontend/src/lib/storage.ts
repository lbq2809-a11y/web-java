import type { UserResponse } from '@/types/auth.types';

const TOKEN_KEY = 'pharmacy_access_token';
const USER_KEY = 'pharmacy_user_profile';

export const authStorage = {
  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  },

  setToken(token: string): void {
    localStorage.setItem(TOKEN_KEY, token);
  },

  getUser(): UserResponse | null {
    const raw = localStorage.getItem(USER_KEY);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as UserResponse;
    } catch {
      localStorage.removeItem(USER_KEY);
      return null;
    }
  },

  setUser(user: UserResponse): void {
    localStorage.setItem(USER_KEY, JSON.stringify(user));
  },

  clear(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  },
};
