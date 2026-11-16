import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { AlertCircle, Cross, Loader2, UserPlus } from 'lucide-react';
import { AppShell } from '@/components/layout/AppShell';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { useAuth } from '@/hooks/useAuth';
import { normalizeApiError } from '@/api/httpClient';

interface RegisterErrors {
  fullName?: string;
  email?: string;
  phone?: string;
  password?: string;
  confirmPassword?: string;
}

export function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();

  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<RegisterErrors>({});
  const [serverError, setServerError] = useState<{ message: string; details: string[] } | null>(
    null,
  );
  const [isSubmitting, setIsSubmitting] = useState(false);

  const validate = (): boolean => {
    const errors: RegisterErrors = {};
    if (!fullName.trim()) {
      errors.fullName = 'Họ tên không được để trống';
    }
    if (!email.trim()) {
      errors.email = 'Email không được để trống';
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
      errors.email = 'Email không đúng định dạng';
    }
    if (phone.trim().length > 20) {
      errors.phone = 'Số điện thoại tối đa 20 ký tự';
    }
    if (!password) {
      errors.password = 'Mật khẩu không được để trống';
    } else if (password.length < 6) {
      errors.password = 'Mật khẩu phải có ít nhất 6 ký tự';
    }
    if (confirmPassword !== password) {
      errors.confirmPassword = 'Mật khẩu xác nhận không khớp';
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
      await register({
        fullName: fullName.trim(),
        email: email.trim(),
        password,
        phone: phone.trim() || undefined,
      });
      navigate('/', { replace: true });
    } catch (err) {
      const normalized = normalizeApiError(err);
      setServerError({
        message: normalized.message,
        details: normalized.details,
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <AppShell>
      <div className="mx-auto flex max-w-md flex-col justify-center px-4 py-12 sm:py-16">
        <div className="mb-6 flex flex-col items-center text-center">
          <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-[#0F766E] text-white">
            <Cross className="h-6 w-6" strokeWidth={2.5} />
          </span>
          <h1 className="mt-3 text-2xl font-bold text-[#0F172A]">Đăng ký khách hàng</h1>
          <p className="mt-1 text-sm text-[#475569]">
            Tạo tài khoản miễn phí để lưu lịch sử tư vấn dược AI
          </p>
        </div>

        <Card>
          <CardHeader className="pb-4">
            <CardTitle>Thông tin cá nhân</CardTitle>
            <CardDescription>Điền đầy đủ các trường bắt buộc bên dưới</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            {serverError && (
              <Alert variant="destructive">
                <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
                <div>
                  <AlertTitle>Không thể đăng ký</AlertTitle>
                  <AlertDescription>
                    <p>{serverError.message}</p>
                    {serverError.details.length > 0 && (
                      <ul className="mt-1 list-disc pl-4 text-xs">
                        {serverError.details.map((detail, idx) => (
                          <li key={idx}>{detail}</li>
                        ))}
                      </ul>
                    )}
                  </AlertDescription>
                </div>
              </Alert>
            )}

            <form onSubmit={handleSubmit} noValidate className="space-y-4">
              <div className="space-y-1.5">
                <label htmlFor="reg-name" className="text-sm font-medium text-[#0F172A]">
                  Họ và tên <span className="text-red-500">*</span>
                </label>
                <Input
                  id="reg-name"
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  placeholder="Nguyễn Văn A"
                />
                {fieldErrors.fullName && (
                  <p className="text-xs text-red-600">{fieldErrors.fullName}</p>
                )}
              </div>

              <div className="space-y-1.5">
                <label htmlFor="reg-email" className="text-sm font-medium text-[#0F172A]">
                  Email <span className="text-red-500">*</span>
                </label>
                <Input
                  id="reg-email"
                  type="email"
                  autoComplete="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="email@example.com"
                />
                {fieldErrors.email && (
                  <p className="text-xs text-red-600">{fieldErrors.email}</p>
                )}
              </div>

              <div className="space-y-1.5">
                <label htmlFor="reg-phone" className="text-sm font-medium text-[#0F172A]">
                  Số điện thoại
                </label>
                <Input
                  id="reg-phone"
                  type="tel"
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                  placeholder="0901234567"
                />
                {fieldErrors.phone && (
                  <p className="text-xs text-red-600">{fieldErrors.phone}</p>
                )}
              </div>

              <div className="space-y-1.5">
                <label htmlFor="reg-password" className="text-sm font-medium text-[#0F172A]">
                  Mật khẩu <span className="text-red-500">*</span>
                </label>
                <Input
                  id="reg-password"
                  type="password"
                  autoComplete="new-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Tối thiểu 6 ký tự"
                />
                {fieldErrors.password && (
                  <p className="text-xs text-red-600">{fieldErrors.password}</p>
                )}
              </div>

              <div className="space-y-1.5">
                <label htmlFor="reg-confirm" className="text-sm font-medium text-[#0F172A]">
                  Xác nhận mật khẩu <span className="text-red-500">*</span>
                </label>
                <Input
                  id="reg-confirm"
                  type="password"
                  autoComplete="new-password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  placeholder="Nhập lại mật khẩu"
                />
                {fieldErrors.confirmPassword && (
                  <p className="text-xs text-red-600">{fieldErrors.confirmPassword}</p>
                )}
              </div>

              <Button type="submit" className="w-full" disabled={isSubmitting}>
                {isSubmitting ? (
                  <>
                    <Loader2 className="h-4 w-4 animate-spin" />
                    <span>Đang tạo tài khoản...</span>
                  </>
                ) : (
                  <>
                    <UserPlus className="h-4 w-4" />
                    <span>Đăng ký tài khoản</span>
                  </>
                )}
              </Button>
            </form>

            <p className="text-center text-sm text-[#475569]">
              Đã có tài khoản?{' '}
              <Link to="/login" className="font-semibold text-[#0F766E] hover:underline">
                Đăng nhập
              </Link>
            </p>
          </CardContent>
        </Card>
      </div>
    </AppShell>
  );
}
