import { useState } from 'react';
import {
  Link,
  Navigate,
  useLocation,
  useSearchParams,
} from 'react-router-dom';
import {
  AlertCircle,
  CheckCircle2,
  Cross,
  Loader2,
  ShieldCheck,
  Smartphone,
} from 'lucide-react';
import { AppShell } from '@/components/layout/AppShell';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { authApi } from '@/api/auth.api';
import { normalizeApiError } from '@/api/httpClient';
import { useAuth } from '@/hooks/useAuth';
import { useToast } from '@/hooks/useToast';

export function QrConfirmPage() {
  const { user, isAuthenticated, isInitializing } = useAuth();
  const { showToast } = useToast();
  const location = useLocation();
  const [searchParams] = useSearchParams();

  const sessionId = searchParams.get('sessionId')?.trim() ?? '';

  const [isConfirming, setIsConfirming] = useState(false);
  const [isConfirmed, setIsConfirmed] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  if (isInitializing) {
    return (
      <AppShell>
        <div className="mx-auto max-w-md space-y-4 px-4 py-12">
          <Skeleton className="h-10 w-48" />
          <Skeleton className="h-56 w-full rounded-xl" />
        </div>
      </AppShell>
    );
  }

  if (!isAuthenticated) {
    const redirectTarget = `${location.pathname}${location.search}`;
    return (
      <Navigate
        to={`/login?redirect=${encodeURIComponent(redirectTarget)}`}
        replace
      />
    );
  }

  const handleConfirm = async () => {
    if (!sessionId) return;
    setErrorMessage(null);
    setIsConfirming(true);
    try {
      await authApi.confirmQrSession(sessionId);
      setIsConfirmed(true);
      showToast({
        title: 'Đã xác nhận đăng nhập QR',
        description: 'Thiết bị kia sẽ tự động đăng nhập trong giây lát.',
        variant: 'success',
      });
    } catch (err) {
      const normalized = normalizeApiError(err);
      setErrorMessage(normalized.message);
    } finally {
      setIsConfirming(false);
    }
  };

  const homePath =
    user?.role === 'DUOC_SI' || user?.role === 'ADMIN' ? '/admin' : '/';

  return (
    <AppShell>
      <div className="mx-auto flex max-w-md flex-col justify-center px-4 py-12 sm:py-16">
        <div className="mb-6 flex flex-col items-center text-center">
          <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-[#0F766E] text-white">
            <Cross className="h-6 w-6" strokeWidth={2.5} />
          </span>
          <h1 className="mt-3 text-2xl font-bold text-[#0F172A]">
            Xác nhận đăng nhập thiết bị
          </h1>
          <p className="mt-1 text-sm text-[#475569]">
            Cấp quyền đăng nhập nhanh qua mã QR từ tài khoản hiện tại
          </p>
        </div>

        <Card>
          <CardHeader className="pb-4">
            <div className="flex items-center gap-2">
              <Smartphone className="h-5 w-5 text-[#0F766E]" />
              <CardTitle>Yêu cầu đăng nhập bằng QR</CardTitle>
            </div>
            <CardDescription>
              Chỉ xác nhận nếu chính bạn vừa mở mã QR trên thiết bị cần đăng nhập
            </CardDescription>
          </CardHeader>

          <CardContent className="space-y-4">
            {!sessionId ? (
              <div className="space-y-4">
                <Alert variant="warning">
                  <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
                  <div>
                    <AlertTitle>Thiếu mã phiên QR</AlertTitle>
                    <AlertDescription>
                      Đường dẫn không chứa tham số <code className="font-mono">sessionId</code> hợp lệ. Vui lòng quét lại mã QR.
                    </AlertDescription>
                  </div>
                </Alert>
                <Button asChild variant="outline" className="w-full">
                  <Link to={homePath}>Quay về trang chính</Link>
                </Button>
              </div>
            ) : (
              <>
                {/* Thông tin tài khoản đang đăng nhập */}
                <div className="space-y-2 rounded-xl border border-[#CCFBF1] bg-[#F0FDFA] p-4 text-sm">
                  <div className="flex items-center gap-2 text-xs font-semibold text-[#0F766E]">
                    <ShieldCheck className="h-4 w-4" />
                    <span>Tài khoản sẽ cấp quyền đăng nhập</span>
                  </div>
                  <div className="space-y-1 text-xs">
                    <p className="font-semibold text-[#0F172A]">
                      {user?.fullName || user?.email}
                    </p>
                    <p className="text-[#475569]">{user?.email}</p>
                    <p className="text-[#475569]">
                      Vai trò:{' '}
                      <span className="font-mono font-semibold text-[#0F766E]">
                        {user?.role}
                      </span>
                    </p>
                  </div>
                </div>

                {/* Mã phiên */}
                <div className="rounded-lg border border-[#E2E8F0] bg-[#F8FAFC] p-3 text-xs">
                  <p className="text-[#475569]">Mã phiên QR (Session ID)</p>
                  <p className="mt-1 break-all font-mono font-semibold text-[#0F172A]">
                    {sessionId}
                  </p>
                </div>

                {errorMessage && (
                  <Alert variant="destructive">
                    <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
                    <div>
                      <AlertTitle>Không thể xác nhận phiên QR</AlertTitle>
                      <AlertDescription>{errorMessage}</AlertDescription>
                    </div>
                  </Alert>
                )}

                {isConfirmed ? (
                  <div className="space-y-4">
                    <Alert variant="teal">
                      <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" />
                      <div>
                        <AlertTitle>Xác nhận thành công</AlertTitle>
                        <AlertDescription>
                          Thiết bị đang chờ sẽ nhận được token và tự động chuyển trang trong vòng vài giây.
                        </AlertDescription>
                      </div>
                    </Alert>
                    <Button asChild variant="outline" className="w-full">
                      <Link to={homePath}>Quay về trang chính</Link>
                    </Button>
                  </div>
                ) : (
                  <div className="space-y-2">
                    <Button
                      type="button"
                      className="w-full"
                      onClick={() => void handleConfirm()}
                      disabled={isConfirming}
                    >
                      {isConfirming ? (
                        <>
                          <Loader2 className="h-4 w-4 animate-spin" />
                          <span>Đang xác nhận...</span>
                        </>
                      ) : (
                        <>
                          <CheckCircle2 className="h-4 w-4" />
                          <span>Xác nhận đăng nhập cho thiết bị này</span>
                        </>
                      )}
                    </Button>
                    <Button asChild variant="ghost" className="w-full">
                      <Link to={homePath}>Hủy và quay lại</Link>
                    </Button>
                  </div>
                )}
              </>
            )}
          </CardContent>
        </Card>
      </div>
    </AppShell>
  );
}
