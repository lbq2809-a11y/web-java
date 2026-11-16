import { Link } from 'react-router-dom';
import { ArrowLeft, SearchX } from 'lucide-react';
import { AppShell } from '@/components/layout/AppShell';
import { Button } from '@/components/ui/button';

export function NotFoundPage() {
  return (
    <AppShell>
      <div className="mx-auto flex max-w-lg flex-col items-center px-4 py-20 text-center">
        <div className="flex h-14 w-14 items-center justify-center rounded-2xl border border-[#E2E8F0] bg-white text-[#0F766E]">
          <SearchX className="h-7 w-7" />
        </div>
        <h1 className="mt-5 font-mono text-4xl font-bold text-[#0F172A]">404</h1>
        <p className="mt-2 text-lg font-semibold text-[#0F172A]">
          Không tìm thấy trang bạn yêu cầu
        </p>
        <p className="mt-1 text-sm text-[#475569]">
          Đường dẫn có thể đã thay đổi hoặc không tồn tại trong hệ thống.
        </p>
        <Button className="mt-6" asChild>
          <Link to="/">
            <ArrowLeft className="h-4 w-4" />
            <span>Về trang chủ</span>
          </Link>
        </Button>
      </div>
    </AppShell>
  );
}
