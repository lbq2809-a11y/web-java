import { useCallback, useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { AlertCircle, Cross, Loader2, LogIn, Mail, QrCode } from 'lucide-react';
import { AppShell } from '@/components/layout/AppShell';
import { QrLoginPanel } from '@/components/auth/QrLoginPanel';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { useAuth } from '@/hooks/useAuth';
import { normalizeApiError } from '@/api/httpClient';
import type { AuthResponse, Role } from '@/types/auth.types';

interface LoginLocationState {
  from?: string;
  sessionExpired?: boolean;
}

const CUSTOMER_ONLY_PATHS = ['/prescriptions', '/cart', '/checkout', '/orders'];

function sanitizeRedirectTarget(raw?: string | null): string | undefined {
  if (!raw || !raw.startsWith('/') || raw.startsWith('//')) {
    return undefined;
  }
  return raw;
}

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
  const { login, completeAuthSession } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const state = (location.state as LoginLocationState | null) ?? null;

  const redirectTarget =
    sanitizeRedirectTarget(searchParams.get('redirect')) ??
    sanitizeRedirectTarget(state?.from);

  const [loginTab, setLoginTab] = useState<'PASSWORD' | 'QR'>('PASSWORD');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<{ email?: string; password?: string }>({});
  const [serverError, setServerError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const navigateByRole = useCallback(
    (role: Role) => {
      const destination = resolvePostLoginDestination(role, redirectTarget);
      navigate(destination, { replace: true });
    },
    [navigate, redirectTarget],
  );

  const handleQrAuthenticated = useCallback(
    (authRes: AuthResponse) => {
      const loggedInUser = completeAuthSession(authRes);
      navigateByRole(loggedInUser.role);
    },
    [completeAuthSession, navigateByRole],
  );

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
      navigateByRole(loggedInUser.role);
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
            <CardDescription>
              {loginTab === 'PASSWORD'
                ? 'Nhập email và mật khẩu đã đăng ký'
                : 'Quét mã QR bằng thiết bị đã đăng nhập để vào nhanh'}
            </CardDescription>

            {/* Tab chuyển đổi phương thức đăng nhập */}
            <div className="mt-3 grid grid-cols-2 gap-1.5 rounded-lg border border-[#E2E8F0] bg-[#F8FAFC] p-1">
              <button
                type="button"
                onClick={() => {
                  setLoginTab('PASSWORD');
                  setServerError(null);
                }}
                className={`inline-flex cursor-pointer items-center justify-center gap-1.5 rounded-md px-3 py-2 text-xs font-semibold transition-colors ${
                  loginTab === 'PASSWORD'
                    ? 'bg-white text-[#0F766E] shadow-xs'
                    : 'text-[#475569] hover:text-[#0F172A]'
                }`}
              >
                <Mail className="h-3.5 w-3.5" />
                <span>Email & Mật khẩu</span>
              </button>
              <button
                type="button"
                onClick={() => {
                  setLoginTab('QR');
                  setServerError(null);
                }}
                className={`inline-flex cursor-pointer items-center justify-center gap-1.5 rounded-md px-3 py-2 text-xs font-semibold transition-colors ${
                  loginTab === 'QR'
                    ? 'bg-white text-[#0F766E] shadow-xs'
                    : 'text-[#475569] hover:text-[#0F172A]'
                }`}
              >
                <QrCode className="h-3.5 w-3.5" />
                <span>Quét mã QR</span>
              </button>
            </div>
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

            {loginTab === 'QR' ? (
              <QrLoginPanel onAuthenticated={handleQrAuthenticated} />
            ) : (
              <>
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
                      className="cursor-pointer rounded-md border border-[#CCFBF1] bg-white px-2.5 py-1 font-medium text-[#0F172A] hover:border-[#0F766E]"
                    >
                      Khách hàng
                    </button>
                    <button
                      type="button"
                      onClick={() => fillDemoAccount('duoc_si@pharmacy.local')}
                      className="cursor-pointer rounded-md border border-[#CCFBF1] bg-white px-2.5 py-1 font-medium text-[#0F172A] hover:border-[#0F766E]"
                    >
                      Dược sĩ
                    </button>
                  </div>
                </div>
              </>
            )}

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
