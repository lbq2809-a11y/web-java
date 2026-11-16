import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { AlertCircle, Cross, Loader2, LogIn } from 'lucide-react';
import { AppShell } from '@/components/layout/AppShell';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { useAuth } from '@/hooks/useAuth';
import { normalizeApiError } from '@/api/httpClient';
import type { Role } from '@/types/auth.types';

interface LoginLocationState {
  from?: string;
  sessionExpired?: boolean;
}

const CUSTOMER_ONLY_PATHS = ['/prescriptions', '/cart', '/checkout', '/orders'];

function resolvePostLoginDestination(role: Role, from?: string): string {
  const cleanFrom = from?.split('?')[0];
  if (role === 'DUOC_SI' || role === 'ADMIN') {
    const isCustomerOnlyOrRoot =
      !from ||
      cleanFrom === '/' ||
      CUSTOMER_ONLY_PATHS.some(
        (path) => cleanFrom === path || cleanFrom?.startsWith(`${path}/`),
      );
    return isCustomerOnlyOrRoot ? '/admin' : from;
  }

  if (!from || cleanFrom === '/admin' || cleanFrom?.startsWith('/admin/')) {
    return '/';
  }
  return from;
}

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const state = (location.state as LoginLocationState | null) ?? null;

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<{ email?: string; password?: string }>({});
  const [serverError, setServerError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const validate = (): boolean => {
    const errors: { email?: string; password?: string } = {};
    const trimmedEmail = email.trim();

    if (!trimmedEmail) {
      errors.email = 'Email không được để trống';
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmedEmail)) {
      errors.email = 'Email không đúng định dạng';
    }

    if (!password) {
      errors.password = 'Mật khẩu không được để trống';
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setServerError(null);
    if (!validate()) return;

    setIsSubmitting(true);
    try {
      const loggedInUser = await login({ email: email.trim(), password });
      const destination = resolvePostLoginDestination(loggedInUser.role, state?.from);
      navigate(destination, { replace: true });
    } catch (err) {
      const normalized = normalizeApiError(err);
      setServerError(normalized.message || 'Email hoặc mật khẩu không chính xác');
    } finally {
      setIsSubmitting(false);
    }
  };

  const fillDemoAccount = (demoEmail: string) => {
    setEmail(demoEmail);
    setPassword('123456');
    setFieldErrors({});
    setServerError(null);
  };

  return (
    <AppShell>
      <div className="mx-auto flex max-w-md flex-col justify-center px-4 py-12 sm:py-16">
        <div className="mb-6 flex flex-col items-center text-center">
          <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-[#0F766E] text-white">
            <Cross className="h-6 w-6" strokeWidth={2.5} />
          </span>
          <h1 className="mt-3 text-2xl font-bold text-[#0F172A]">Đăng nhập tài khoản</h1>
          <p className="mt-1 text-sm text-[#475569]">
            Đăng nhập để sử dụng Trợ lý Tư vấn Thuốc AI và quản lý hồ sơ của bạn
          </p>
        </div>

        <Card>
          <CardHeader className="pb-4">
            <CardTitle>Thông tin đăng nhập</CardTitle>
            <CardDescription>Nhập email và mật khẩu đã đăng ký</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            {state?.sessionExpired && !serverError && (
              <Alert variant="warning">
                <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
                <div>
                  <AlertTitle>Phiên đăng nhập hết hạn</AlertTitle>
                  <AlertDescription>
                    Vui lòng đăng nhập lại để tiếp tục thao tác.
                  </AlertDescription>
                </div>
              </Alert>
            )}

            {serverError && (
              <Alert variant="destructive">
                <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
                <div>
                  <AlertTitle>Đăng nhập không thành công</AlertTitle>
                  <AlertDescription>{serverError}</AlertDescription>
                </div>
              </Alert>
            )}

            <form onSubmit={handleSubmit} noValidate className="space-y-4">
              <div className="space-y-1.5">
                <label htmlFor="login-email" className="text-sm font-medium text-[#0F172A]">
                  Email
                </label>
                <Input
                  id="login-email"
                  type="email"
                  autoComplete="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="khach_hang@pharmacy.local"
                />
                {fieldErrors.email && (
                  <p className="text-xs text-red-600">{fieldErrors.email}</p>
                )}
              </div>

              <div className="space-y-1.5">
                <label htmlFor="login-password" className="text-sm font-medium text-[#0F172A]">
                  Mật khẩu
                </label>
                <Input
                  id="login-password"
                  type="password"
                  autoComplete="current-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Nhập mật khẩu"
                />
                {fieldErrors.password && (
                  <p className="text-xs text-red-600">{fieldErrors.password}</p>
                )}
              </div>

              <Button type="submit" className="w-full" disabled={isSubmitting}>
                {isSubmitting ? (
                  <>
                    <Loader2 className="h-4 w-4 animate-spin" />
                    <span>Đang xác thực...</span>
                  </>
                ) : (
                  <>
                    <LogIn className="h-4 w-4" />
                    <span>Đăng nhập</span>
                  </>
                )}
              </Button>
            </form>

            {/* Gợi ý tài khoản seed thử nghiệm từ auth-service */}
            <div className="rounded-xl border border-[#CCFBF1] bg-[#F0FDFA] p-3.5 text-xs">
              <p className="font-semibold text-[#0F766E]">
                Tài khoản thử nghiệm (Mật khẩu: <span className="font-mono">123456</span>)
              </p>
              <div className="mt-2 flex flex-wrap gap-2">
                <button
                  type="button"
                  onClick={() => fillDemoAccount('khach_hang@pharmacy.local')}
                  className="rounded-md border border-[#CCFBF1] bg-white px-2.5 py-1 font-medium text-[#0F172A] hover:border-[#0F766E] cursor-pointer"
                >
                  Khách hàng
                </button>
                <button
                  type="button"
                  onClick={() => fillDemoAccount('duoc_si@pharmacy.local')}
                  className="rounded-md border border-[#CCFBF1] bg-white px-2.5 py-1 font-medium text-[#0F172A] hover:border-[#0F766E] cursor-pointer"
                >
                  Dược sĩ
                </button>
              </div>
            </div>

            <p className="text-center text-sm text-[#475569]">
              Chưa có tài khoản?{' '}
              <Link to="/register" className="font-semibold text-[#0F766E] hover:underline">
                Đăng ký ngay
              </Link>
            </p>
          </CardContent>
        </Card>
      </div>
    </AppShell>
  );
}
