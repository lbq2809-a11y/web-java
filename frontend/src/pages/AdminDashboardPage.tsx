import { useState } from 'react';
import { FileCheck2, PackageCheck, Pill, ShieldCheck } from 'lucide-react';
import { AppShell } from '@/components/layout/AppShell';
import { AdminMedicinesTab } from '@/components/admin/AdminMedicinesTab';
import { AdminPrescriptionsTab } from '@/components/admin/AdminPrescriptionsTab';
import { AdminOrdersTab } from '@/components/admin/AdminOrdersTab';
import { useAuth } from '@/hooks/useAuth';
import { translateRole } from '@/lib/utils';

type AdminTab = 'medicines' | 'prescriptions' | 'orders';

const TABS: { id: AdminTab; label: string; icon: typeof Pill }[] = [
  { id: 'medicines', label: 'Quản lý thuốc', icon: Pill },
  { id: 'prescriptions', label: 'Duyệt đơn thuốc', icon: FileCheck2 },
  { id: 'orders', label: 'Quản lý đơn hàng', icon: PackageCheck },
];

export function AdminDashboardPage() {
  const { user } = useAuth();
  const [activeTab, setActiveTab] = useState<AdminTab>('medicines');

  return (
    <AppShell>
      <div className="space-y-6">
        <div className="flex flex-wrap items-center justify-between gap-4 rounded-xl border border-[#CCFBF1] bg-[#F0FDFA] p-5">
          <div>
            <div className="flex items-center gap-2 text-xs font-semibold text-[#0F766E]">
              <ShieldCheck className="h-4 w-4" />
              <span>KÊNH DƯỢC SĨ & QUẢN TRỊ VIÊN</span>
            </div>
            <h1 className="mt-1 text-2xl font-bold tracking-tight text-[#0F172A]">
              Điều hành Nhà thuốc Trực tuyến
            </h1>
            <p className="mt-0.5 text-sm text-[#475569]">
              Quản trị danh mục thuốc, thẩm định đơn thuốc kê đơn và xử lý luồng đơn hàng.
            </p>
          </div>

          {user && (
            <div className="rounded-lg border border-[#CCFBF1] bg-white px-4 py-2 text-xs">
              <p className="font-semibold text-[#0F172A]">{user.fullName || user.email}</p>
              <p className="font-medium text-[#0F766E]">{translateRole(user.role)}</p>
            </div>
          )}
        </div>

        {/* Điều hướng 3 tab nghiệp vụ */}
        <div className="flex flex-wrap gap-2 border-b border-[#E2E8F0] pb-3">
          {TABS.map((tab) => {
            const Icon = tab.icon;
            const active = activeTab === tab.id;
            return (
              <button
                key={tab.id}
                type="button"
                onClick={() => setActiveTab(tab.id)}
                className={`inline-flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-semibold transition-colors ${
                  active
                    ? 'bg-[#0F766E] text-white shadow-sm'
                    : 'border border-[#E2E8F0] bg-white text-[#475569] hover:border-[#0F766E]/40 hover:text-[#0F766E]'
                }`}
              >
                <Icon className="h-4 w-4" />
                <span>{tab.label}</span>
              </button>
            );
          })}
        </div>

        {activeTab === 'medicines' && <AdminMedicinesTab />}
        {activeTab === 'prescriptions' && <AdminPrescriptionsTab />}
        {activeTab === 'orders' && <AdminOrdersTab />}
      </div>
    </AppShell>
  );
}
