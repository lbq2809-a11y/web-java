import { useState } from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import {
  ClipboardList,
  Cross,
  FileText,
  LogOut,
  Menu,
  MessageSquareHeart,
  Pill,
  ShieldCheck,
  ShoppingCart,
  User as UserIcon,
  X,
} from 'lucide-react';
import { useAuth } from '@/hooks/useAuth';
import { useCart } from '@/hooks/useCart';
import { NotificationBell } from '@/components/layout/NotificationBell';
import { Button } from '@/components/ui/button';
import { cn, translateRole } from '@/lib/utils';

export function Navbar() {
  const { isAuthenticated, user, logout } = useAuth();
  const { totalItems } = useCart();
  const navigate = useNavigate();
  const [mobileOpen, setMobileOpen] = useState(false);

  const closeMobile = () => setMobileOpen(false);

  const handleLogout = () => {
    closeMobile();
    logout();
    navigate('/login', { replace: true, state: null });
  };

  const isCustomer = isAuthenticated && user?.role === 'KHACH_HANG';
  const isStaff =
    isAuthenticated && (user?.role === 'DUOC_SI' || user?.role === 'ADMIN');

  const navLinks = [
    { to: '/', label: 'Trang chủ', end: true },
    { to: '/medicines', label: 'Danh mục thuốc', end: false },
    { to: '/consultation', label: 'Tư vấn AI', end: false },
    ...(isCustomer
      ? [
          { to: '/prescriptions', label: 'Đơn thuốc', end: false },
          { to: '/orders', label: 'Đơn hàng', end: false },
        ]
      : []),
    ...(isStaff ? [{ to: '/admin', label: 'Kênh Dược sĩ', end: false }] : []),
  ];

  return (
    <header className="sticky top-0 z-40 border-b border-[#E2E8F0] bg-white/95 backdrop-blur-sm">
      <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
        {/* Logo nhà thuốc */}
        <Link to="/" className="flex items-center gap-2.5 group" onClick={closeMobile}>
          <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-[#0F766E] text-white transition-colors group-hover:bg-[#115E59]">
            <Cross className="h-5 w-5" strokeWidth={2.5} />
          </span>
          <div className="flex flex-col leading-tight">
            <span className="text-base font-bold tracking-tight text-[#0F172A]">
              An Tâm Pharmacy
            </span>
            <span className="hidden text-[11px] font-medium text-[#475569] sm:block">
              Nhà thuốc trực tuyến & Tư vấn Dược AI
            </span>
          </div>
        </Link>

        {/* Điều hướng Desktop */}
        <nav className="hidden items-center gap-1 md:flex" aria-label="Điều hướng chính">
          {navLinks.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                cn(
                  'rounded-lg px-3 py-2 text-sm font-medium transition-colors flex items-center gap-1.5',
                  isActive
                    ? 'bg-[#F0FDFA] text-[#0F766E] border border-[#CCFBF1]'
                    : 'text-[#475569] hover:bg-[#F8FAFC] hover:text-[#0F172A] border border-transparent',
                )
              }
            >
              {item.to === '/medicines' && <Pill className="h-4 w-4" />}
              {item.to === '/consultation' && <MessageSquareHeart className="h-4 w-4" />}
              {item.to === '/prescriptions' && <FileText className="h-4 w-4" />}
              {item.to === '/orders' && <ClipboardList className="h-4 w-4" />}
              {item.to === '/admin' && <ShieldCheck className="h-4 w-4 text-[#0F766E]" />}
              {item.label}
            </NavLink>
          ))}
        </nav>

        {/* Cụm Thông báo, Giỏ hàng & Tài khoản */}
        <div className="flex items-center gap-2">
          <NotificationBell />
          <Link
            to="/cart"
            onClick={closeMobile}
            aria-label={`Giỏ hàng (${totalItems} sản phẩm)`}
            className="relative inline-flex h-10 items-center gap-1.5 rounded-lg border border-[#E2E8F0] bg-white px-3 text-sm font-medium text-[#0F172A] transition-colors hover:border-[#0F766E]/50 hover:bg-[#F0FDFA]"
          >
            <ShoppingCart className="h-4 w-4 text-[#0F766E]" />
            <span className="hidden sm:inline">Giỏ hàng</span>
            {totalItems > 0 && (
              <span className="flex h-5 min-w-[20px] items-center justify-center rounded-full bg-[#0F766E] px-1.5 font-mono text-[11px] font-bold text-white">
                {totalItems > 99 ? '99+' : totalItems}
              </span>
            )}
          </Link>

          <div className="hidden items-center gap-2 md:flex">
            {isAuthenticated && user ? (
              <>
                <Link
                  to="/profile"
                  className="flex items-center gap-2 rounded-lg border border-[#E2E8F0] bg-[#F8FAFC] px-3 py-1.5 text-sm transition-colors hover:border-[#0F766E]/40 hover:bg-[#F0FDFA]"
                >
                  <span className="flex h-6 w-6 items-center justify-center rounded-full bg-[#0F766E] text-xs font-semibold text-white">
                    {(user.fullName || user.email).charAt(0).toUpperCase()}
                  </span>
                  <div className="flex flex-col leading-none">
                    <span className="max-w-[130px] truncate text-xs font-semibold text-[#0F172A]">
                      {user.fullName || user.email}
                    </span>
                    <span className="mt-0.5 text-[10px] text-[#475569]">
                      {translateRole(user.role)}
                    </span>
                  </div>
                </Link>
                <Button variant="outline" size="sm" onClick={handleLogout} title="Đăng xuất">
                  <LogOut className="h-4 w-4" />
                  <span>Đăng xuất</span>
                </Button>
              </>
            ) : (
              <>
                <Button variant="ghost" size="sm" asChild>
                  <Link to="/login">Đăng nhập</Link>
                </Button>
                <Button size="sm" asChild>
                  <Link to="/register">Đăng ký</Link>
                </Button>
              </>
            )}
          </div>

          {/* Nút mở menu Mobile */}
          <button
            type="button"
            onClick={() => setMobileOpen((prev) => !prev)}
            className="inline-flex h-10 w-10 items-center justify-center rounded-lg border border-[#E2E8F0] bg-white text-[#0F172A] hover:bg-[#F8FAFC] md:hidden cursor-pointer"
            aria-label={mobileOpen ? 'Đóng menu' : 'Mở menu'}
            aria-expanded={mobileOpen}
          >
            {mobileOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
          </button>
        </div>
      </div>

      {/* Menu Drawer cho Mobile */}
      {mobileOpen && (
        <div className="border-t border-[#E2E8F0] bg-white px-4 pb-5 pt-3 md:hidden">
          <nav className="flex flex-col space-y-1" aria-label="Điều hướng di động">
            {navLinks.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                onClick={closeMobile}
                className={({ isActive }) =>
                  cn(
                    'flex items-center gap-2 rounded-lg px-3 py-2.5 text-sm font-medium',
                    isActive
                      ? 'bg-[#F0FDFA] text-[#0F766E] border border-[#CCFBF1]'
                      : 'text-[#475569] hover:bg-[#F8FAFC] hover:text-[#0F172A]',
                  )
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>

          <div className="mt-4 border-t border-[#E2E8F0] pt-4">
            {isAuthenticated && user ? (
              <div className="space-y-3">
                <Link
                  to="/profile"
                  onClick={closeMobile}
                  className="flex items-center gap-3 rounded-lg border border-[#E2E8F0] bg-[#F8FAFC] p-3"
                >
                  <UserIcon className="h-5 w-5 text-[#0F766E]" />
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-semibold text-[#0F172A]">
                      {user.fullName || user.email}
                    </p>
                    <p className="truncate text-xs text-[#475569]">{user.email}</p>
                  </div>
                  <span className="text-xs font-medium text-[#0F766E]">Tài khoản</span>
                </Link>
                <Button
                  variant="outline"
                  className="w-full justify-center"
                  onClick={handleLogout}
                >
                  <LogOut className="h-4 w-4" />
                  Đăng xuất
                </Button>
              </div>
            ) : (
              <div className="grid grid-cols-2 gap-2">
                <Button variant="outline" asChild onClick={closeMobile}>
                  <Link to="/login">Đăng nhập</Link>
                </Button>
                <Button asChild onClick={closeMobile}>
                  <Link to="/register">Đăng ký</Link>
                </Button>
              </div>
            )}
          </div>
        </div>
      )}
    </header>
  );
}
