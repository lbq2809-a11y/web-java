import { useEffect, type ReactNode } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '@/hooks/useAuth';
import { useToast } from '@/hooks/useToast';
import { Skeleton } from '@/components/ui/skeleton';
import type { Role } from '@/types/auth.types';

interface RoleRouteProps {
  allowedRoles: Role[];
  redirectTo?: string;
  silentRedirect?: boolean;
  children: ReactNode;
}

function UnauthorizedRedirect({
  redirectTo,
  silentRedirect,
}: {
  redirectTo: string;
  silentRedirect: boolean;
}) {
  const navigate = useNavigate();
  const { showToast } = useToast();

  useEffect(() => {
    if (!silentRedirect) {
      showToast({
        title: 'Không có quyền truy cập',
        description: 'Kênh Dược sĩ / Quản trị chỉ dành cho tài khoản DUOC_SI hoặc ADMIN.',
        variant: 'warning',
      });
    }
    navigate(redirectTo, { replace: true });
  }, [navigate, redirectTo, showToast, silentRedirect]);

  return null;
}

export function RoleRoute({
  allowedRoles,
  redirectTo = '/',
  silentRedirect = false,
  children,
}: RoleRouteProps) {
  const { isAuthenticated, isInitializing, user } = useAuth();
  const location = useLocation();

  if (isInitializing) {
    return (
      <div className="mx-auto max-w-5xl space-y-4 px-4 py-12">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-32 w-full rounded-xl" />
        <Skeleton className="h-64 w-full rounded-xl" />
      </div>
    );
  }

  if (!isAuthenticated || !user) {
    return (
      <Navigate
        to="/login"
        replace
        state={{ from: `${location.pathname}${location.search}` }}
      />
    );
  }

  if (!allowedRoles.includes(user.role)) {
    if (silentRedirect) {
      return <Navigate to={redirectTo} replace />;
    }
    return <UnauthorizedRedirect redirectTo={redirectTo} silentRedirect={silentRedirect} />;
  }

  return <>{children}</>;
}
