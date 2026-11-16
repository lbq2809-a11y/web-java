import { useQuery } from '@tanstack/react-query';
import {
  Calendar,
  CheckCircle2,
  Mail,
  Phone,
  Shield,
  User as UserIcon,
} from 'lucide-react';
import { authApi } from '@/api/auth.api';
import { AppShell } from '@/components/layout/AppShell';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { formatDateTimeVi, translateRole } from '@/lib/utils';
import { normalizeApiError } from '@/api/httpClient';

export function ProfilePage() {
  const { data: profile, isLoading, isError, error } = useQuery({
    queryKey: ['auth', 'me'],
    queryFn: () => authApi.getMe(),
  });

  return (
    <AppShell>
      <div className="mx-auto max-w-3xl px-4 py-10 sm:px-6 lg:px-8">
        <div className="mb-6">
          <h1 className="text-2xl font-bold tracking-tight text-[#0F172A]">
            Thông tin tài khoản
          </h1>
          <p className="mt-1 text-sm text-[#475569]">
            Dữ liệu hồ sơ đồng bộ trực tiếp từ hệ thống xác thực (`GET /api/v1/auth/me`).
          </p>
        </div>

        {isLoading ? (
          <Card>
            <CardHeader>
              <Skeleton className="h-6 w-48" />
              <Skeleton className="h-4 w-64" />
            </CardHeader>
            <CardContent className="space-y-4">
              <Skeleton className="h-12 w-full rounded-lg" />
              <Skeleton className="h-12 w-full rounded-lg" />
              <Skeleton className="h-12 w-full rounded-lg" />
            </CardContent>
          </Card>
        ) : isError ? (
          <Alert variant="destructive">
            <div>
              <AlertTitle>Không thể tải thông tin tài khoản</AlertTitle>
              <AlertDescription>{normalizeApiError(error).message}</AlertDescription>
            </div>
          </Alert>
        ) : profile ? (
          <Card>
            <CardHeader className="border-b border-[#F1F5F9] pb-5">
              <div className="flex flex-wrap items-center justify-between gap-4">
                <div className="flex items-center gap-3.5">
                  <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-[#0F766E] text-lg font-bold text-white">
                    {(profile.fullName || profile.email).charAt(0).toUpperCase()}
                  </div>
                  <div>
                    <CardTitle>{profile.fullName}</CardTitle>
                    <CardDescription className="font-mono text-xs mt-1">
                      ID: {profile.id}
                    </CardDescription>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <Badge>{translateRole(profile.role)}</Badge>
                  {profile.active && (
                    <Badge variant="default" className="gap-1">
                      <CheckCircle2 className="h-3 w-3" />
                      <span>Đang hoạt động</span>
                    </Badge>
                  )}
                </div>
              </div>
            </CardHeader>

            <CardContent className="grid gap-4 pt-6 sm:grid-cols-2">
              <div className="rounded-xl border border-[#E2E8F0] bg-[#F8FAFC] p-4">
                <div className="flex items-center gap-2 text-xs font-medium text-[#475569]">
                  <Mail className="h-3.5 w-3.5 text-[#0F766E]" />
                  <span>Địa chỉ Email</span>
                </div>
                <p className="mt-1.5 text-sm font-semibold text-[#0F172A]">{profile.email}</p>
              </div>

              <div className="rounded-xl border border-[#E2E8F0] bg-[#F8FAFC] p-4">
                <div className="flex items-center gap-2 text-xs font-medium text-[#475569]">
                  <Phone className="h-3.5 w-3.5 text-[#0F766E]" />
                  <span>Số điện thoại</span>
                </div>
                <p className="mt-1.5 font-mono text-sm font-semibold text-[#0F172A]">
                  {profile.phone || 'Chưa cập nhật'}
                </p>
              </div>

              <div className="rounded-xl border border-[#E2E8F0] bg-[#F8FAFC] p-4">
                <div className="flex items-center gap-2 text-xs font-medium text-[#475569]">
                  <Shield className="h-3.5 w-3.5 text-[#0F766E]" />
                  <span>Vai trò hệ thống</span>
                </div>
                <p className="mt-1.5 text-sm font-semibold text-[#0F172A]">
                  {translateRole(profile.role)}{' '}
                  <span className="font-mono text-xs font-normal text-[#475569]">
                    ({profile.role})
                  </span>
                </p>
              </div>

              <div className="rounded-xl border border-[#E2E8F0] bg-[#F8FAFC] p-4">
                <div className="flex items-center gap-2 text-xs font-medium text-[#475569]">
                  <Calendar className="h-3.5 w-3.5 text-[#0F766E]" />
                  <span>Ngày tham gia</span>
                </div>
                <p className="mt-1.5 font-mono text-sm font-semibold text-[#0F172A]">
                  {formatDateTimeVi(profile.createdAt)}
                </p>
              </div>

              <div className="rounded-xl border border-[#CCFBF1] bg-[#F0FDFA] p-4 sm:col-span-2">
                <div className="flex items-center gap-2 text-xs font-semibold text-[#0F766E]">
                  <UserIcon className="h-4 w-4" />
                  <span>Quyền hạn tài khoản</span>
                </div>
                <p className="mt-1 text-xs leading-relaxed text-[#475569]">
                  Tài khoản của bạn được xác thực bằng JSON Web Token (RS256) và có quyền truy cập đầy đủ vào tính năng tra cứu thuốc cũng như Trợ lý Tư vấn Thuốc AI.
                </p>
              </div>
            </CardContent>
          </Card>
        ) : null}
      </div>
    </AppShell>
  );
}
