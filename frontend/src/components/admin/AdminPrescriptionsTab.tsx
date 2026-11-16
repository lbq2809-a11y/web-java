import { useState } from 'react';
import {
  CheckCircle2,
  Eye,
  Loader2,
  Plus,
  Trash2,
  X,
  XCircle,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Skeleton } from '@/components/ui/skeleton';
import { PrescriptionStatusBadge } from '@/components/prescription/PrescriptionStatusBadge';
import { PrescriptionImageViewer } from '@/components/prescription/PrescriptionImageViewer';
import { useMedicinesQuery } from '@/hooks/useMedicines';
import {
  useAllPrescriptionsQuery,
  useApprovePrescriptionMutation,
  useRejectPrescriptionMutation,
} from '@/hooks/usePrescriptions';
import { useToast } from '@/hooks/useToast';
import { normalizeApiError } from '@/api/httpClient';
import { formatDateTimeVi } from '@/lib/utils';
import type {
  ApproveItemRequest,
  PrescriptionResponse,
  PrescriptionStatus,
} from '@/types/prescription.types';

const STATUS_TABS: { value: PrescriptionStatus | ''; label: string }[] = [
  { value: 'CHO_DUYET', label: 'Chờ duyệt' },
  { value: '', label: 'Tất cả' },
  { value: 'DA_DUYET', label: 'Đã duyệt' },
  { value: 'TU_CHOI', label: 'Từ chối' },
];

export function AdminPrescriptionsTab() {
  const { showToast } = useToast();

  const [statusFilter, setStatusFilter] = useState<PrescriptionStatus | ''>('CHO_DUYET');
  const [page, setPage] = useState(0);

  const [previewTarget, setPreviewTarget] = useState<PrescriptionResponse | null>(null);
  const [approveTarget, setApproveTarget] = useState<PrescriptionResponse | null>(null);
  const [rejectTarget, setRejectTarget] = useState<PrescriptionResponse | null>(null);

  const [approvedItems, setApprovedItems] = useState<ApproveItemRequest[]>([]);
  const [selectedMedId, setSelectedMedId] = useState('');
  const [selectedQty, setSelectedQty] = useState(2);
  const [medSearch, setMedSearch] = useState('');
  const [validDays, setValidDays] = useState(30);
  const [modalError, setModalError] = useState<string | null>(null);

  const [rejectReason, setRejectReason] = useState('');

  const prescriptionsQuery = useAllPrescriptionsQuery({
    status: statusFilter,
    page,
    size: 10,
  });

  // Nạp danh sách thuốc ETC để dược sĩ tìm/chọn nhanh khi duyệt đơn
  const etcMedicinesQuery = useMedicinesQuery({
    keyword: medSearch.trim() || undefined,
    prescriptionType: 'ETC',
    page: 0,
    size: 50,
  });

  const approveMutation = useApprovePrescriptionMutation();
  const rejectMutation = useRejectPrescriptionMutation();

  const openApproveModal = (p: PrescriptionResponse) => {
    setApproveTarget(p);
    setApprovedItems([]);
    setSelectedMedId('');
    setSelectedQty(2);
    setMedSearch('');
    setValidDays(30);
    setModalError(null);
  };

  const handleAddApprovedItem = () => {
    if (!selectedMedId) return;
    const med = (etcMedicinesQuery.data?.items ?? []).find((m) => m.id === selectedMedId);
    if (!med) return;

    setApprovedItems((prev) => {
      const existing = prev.find((i) => i.medicineId === med.id);
      if (existing) {
        return prev.map((i) =>
          i.medicineId === med.id
            ? { ...i, maxAllowedQuantity: Math.max(1, selectedQty) }
            : i,
        );
      }
      return [
        ...prev,
        {
          medicineId: med.id,
          medicineName: med.name,
          maxAllowedQuantity: Math.max(1, selectedQty),
        },
      ];
    });
    setSelectedMedId('');
    setModalError(null);
  };

  const handleConfirmApprove = async () => {
    if (!approveTarget) return;
    if (approvedItems.length === 0) {
      setModalError('Vui lòng chọn ít nhất 1 thuốc kê đơn được phép mua.');
      return;
    }

    setModalError(null);
    const expiresAt = new Date(
      Date.now() + Math.max(1, validDays) * 24 * 60 * 60 * 1000,
    ).toISOString();

    try {
      await approveMutation.mutateAsync({
        id: approveTarget.id,
        payload: {
          expiresAt,
          items: approvedItems,
        },
      });
      showToast({
        title: 'Đã phê duyệt đơn thuốc',
        description: `Đơn #${approveTarget.id.slice(0, 8)} đã sẵn sàng cho khách đặt mua.`,
        variant: 'success',
      });
      setApproveTarget(null);
    } catch (error) {
      setModalError(normalizeApiError(error).message);
    }
  };

  const handleConfirmReject = async () => {
    if (!rejectTarget) return;
    const trimmed = rejectReason.trim();
    if (!trimmed) {
      setModalError('Vui lòng nhập lý do từ chối đơn thuốc.');
      return;
    }

    setModalError(null);
    try {
      await rejectMutation.mutateAsync({
        id: rejectTarget.id,
        payload: { reason: trimmed },
      });
      showToast({
        title: 'Đã từ chối đơn thuốc',
        description: `Đơn #${rejectTarget.id.slice(0, 8)} đã chuyển sang trạng thái Từ chối.`,
        variant: 'info',
      });
      setRejectTarget(null);
      setRejectReason('');
    } catch (error) {
      setModalError(normalizeApiError(error).message);
    }
  };

  const data = prescriptionsQuery.data;
  const etcOptions = etcMedicinesQuery.data?.items ?? [];

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-2">
        {STATUS_TABS.map((tab) => {
          const active = statusFilter === tab.value;
          return (
            <button
              key={tab.value || 'ALL'}
              type="button"
              onClick={() => {
                setStatusFilter(tab.value);
                setPage(0);
              }}
              className={`rounded-full border px-3.5 py-1.5 text-xs font-semibold transition-colors ${
                active
                  ? 'border-[#0F766E] bg-[#0F766E] text-white'
                  : 'border-[#E2E8F0] bg-white text-[#475569] hover:border-[#0F766E]/40 hover:text-[#0F766E]'
              }`}
            >
              {tab.label}
            </button>
          );
        })}
      </div>

      {prescriptionsQuery.isLoading ? (
        <div className="space-y-3">
          {Array.from({ length: 3 }).map((_, idx) => (
            <Skeleton key={idx} className="h-32 w-full rounded-xl" />
          ))}
        </div>
      ) : prescriptionsQuery.isError ? (
        <div className="rounded-xl border border-red-200 bg-red-50 p-5 text-sm text-red-700">
          {normalizeApiError(prescriptionsQuery.error).message}
        </div>
      ) : !data || data.items.length === 0 ? (
        <div className="rounded-xl border border-[#E2E8F0] bg-white p-10 text-center text-sm text-[#475569]">
          Không có đơn thuốc nào trong mục này.
        </div>
      ) : (
        <div className="space-y-4">
          {data.items.map((p) => (
            <article
              key={p.id}
              className="space-y-3 rounded-xl border border-[#E2E8F0] bg-white p-5"
            >
              <div className="flex flex-wrap items-start justify-between gap-3 border-b border-[#E2E8F0] pb-3">
                <div className="space-y-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-mono text-sm font-bold text-[#0F172A]">
                      #{p.id.slice(0, 8).toUpperCase()}
                    </span>
                    <PrescriptionStatusBadge status={p.status} />
                  </div>
                  <p className="text-xs text-[#475569]">
                    Khách hàng:{' '}
                    <span className="font-semibold text-[#0F172A]">
                      {p.customerName || p.customerEmail || p.customerId}
                    </span>
                    {' • '}Gửi lúc: {formatDateTimeVi(p.createdAt)}
                  </p>
                </div>

                <div className="flex flex-wrap gap-2">
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    onClick={() => setPreviewTarget(p)}
                  >
                    <Eye className="h-4 w-4" />
                    <span>Xem ảnh đơn</span>
                  </Button>

                  {p.status === 'CHO_DUYET' && (
                    <>
                      <Button
                        type="button"
                        size="sm"
                        onClick={() => openApproveModal(p)}
                      >
                        <CheckCircle2 className="h-4 w-4" />
                        <span>Duyệt đơn</span>
                      </Button>
                      <Button
                        type="button"
                        size="sm"
                        variant="outline"
                        onClick={() => {
                          setRejectTarget(p);
                          setRejectReason('');
                          setModalError(null);
                        }}
                        className="border-red-200 text-red-600 hover:bg-red-50 hover:text-red-700"
                      >
                        <XCircle className="h-4 w-4" />
                        <span>Từ chối</span>
                      </Button>
                    </>
                  )}
                </div>
              </div>

              {p.notes && (
                <p className="whitespace-pre-line rounded-lg bg-[#F8FAFC] p-3 text-xs leading-relaxed text-[#0F172A]">
                  {p.notes}
                </p>
              )}

              {p.status === 'DA_DUYET' && p.items && p.items.length > 0 && (
                <div className="flex flex-wrap gap-2">
                  {p.items.map((item) => (
                    <span
                      key={item.id}
                      className="rounded-lg border border-[#CCFBF1] bg-[#F0FDFA] px-2.5 py-1 text-xs font-medium text-[#0F766E]"
                    >
                      {item.medicineName} (tối đa{' '}
                      <span className="font-mono font-bold">{item.maxAllowedQuantity}</span>)
                    </span>
                  ))}
                </div>
              )}

              {p.status === 'TU_CHOI' && p.rejectionReason && (
                <p className="rounded-lg border border-red-200 bg-red-50 p-2.5 text-xs text-red-700">
                  Lý do từ chối: {p.rejectionReason}
                </p>
              )}
            </article>
          ))}

          {data.totalPages > 1 && (
            <div className="flex items-center justify-between rounded-xl border border-[#E2E8F0] bg-white px-4 py-3 text-xs text-[#475569]">
              <span>
                Trang <span className="font-mono font-semibold">{data.page + 1}</span> /{' '}
                <span className="font-mono font-semibold">{data.totalPages}</span> ({data.totalElements} đơn thuốc)
              </span>
              <div className="flex gap-2">
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  disabled={data.page <= 0}
                >
                  Trước
                </Button>
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => setPage((p) => p + 1)}
                  disabled={data.page + 1 >= data.totalPages}
                >
                  Sau
                </Button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Modal xem ảnh đơn thuốc */}
      {previewTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="w-full max-w-2xl space-y-4 rounded-xl border border-[#E2E8F0] bg-white p-6 shadow-xl">
            <div className="flex items-center justify-between border-b border-[#E2E8F0] pb-3">
              <h3 className="text-base font-bold text-[#0F172A]">
                Ảnh đơn thuốc #{previewTarget.id.slice(0, 8).toUpperCase()}
              </h3>
              <button
                type="button"
                onClick={() => setPreviewTarget(null)}
                className="rounded-lg p-1 text-[#475569] hover:bg-[#F8FAFC]"
              >
                <X className="h-5 w-5" />
              </button>
            </div>
            <PrescriptionImageViewer prescriptionId={previewTarget.id} />
            <div className="flex justify-end">
              <Button variant="outline" onClick={() => setPreviewTarget(null)}>
                Đóng
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Modal Duyệt đơn thuốc */}
      {approveTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="max-h-[92vh] w-full max-w-3xl overflow-y-auto rounded-xl border border-[#E2E8F0] bg-white p-6 shadow-xl">
            <div className="flex items-center justify-between border-b border-[#E2E8F0] pb-3">
              <div>
                <h3 className="text-lg font-bold text-[#0F172A]">
                  Phê duyệt đơn thuốc #{approveTarget.id.slice(0, 8).toUpperCase()}
                </h3>
                <p className="text-xs text-[#475569]">
                  Chọn các thuốc kê đơn (ETC) và số lượng tối đa khách được phép mua từ toa này.
                </p>
              </div>
              <button
                type="button"
                onClick={() => setApproveTarget(null)}
                className="rounded-lg p-1 text-[#475569] hover:bg-[#F8FAFC]"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <div className="mt-4 grid gap-5 md:grid-cols-2">
              <div className="space-y-3">
                <p className="text-xs font-semibold uppercase text-[#475569]">
                  Ảnh đơn thuốc khách gửi
                </p>
                <PrescriptionImageViewer
                  prescriptionId={approveTarget.id}
                  className="max-h-64 w-full rounded-lg border border-[#E2E8F0] bg-[#F8FAFC] object-contain"
                />
                {approveTarget.notes && (
                  <p className="whitespace-pre-line rounded-lg bg-[#F8FAFC] p-3 text-xs text-[#0F172A]">
                    {approveTarget.notes}
                  </p>
                )}
              </div>

              <div className="space-y-4">
                <div className="space-y-2">
                  <label className="block text-xs font-semibold text-[#0F172A]">
                    Tìm & chọn thuốc ETC cho phép mua <span className="text-red-500">*</span>
                  </label>
                  <Input
                    value={medSearch}
                    onChange={(e) => setMedSearch(e.target.value)}
                    placeholder="Lọc nhanh tên thuốc ETC..."
                    className="h-9 text-xs"
                  />
                  <div className="flex gap-2">
                    <select
                      value={selectedMedId}
                      onChange={(e) => setSelectedMedId(e.target.value)}
                      className="h-9 flex-1 rounded-lg border border-[#E2E8F0] bg-white px-2.5 text-xs text-[#0F172A]"
                    >
                      <option value="">-- Chọn thuốc ETC --</option>
                      {etcOptions.map((med) => (
                        <option key={med.id} value={med.id}>
                          {med.name} ({med.code})
                        </option>
                      ))}
                    </select>
                    <Input
                      type="number"
                      min={1}
                      max={99}
                      value={selectedQty}
                      onChange={(e) =>
                        setSelectedQty(Math.max(1, Number(e.target.value) || 1))
                      }
                      title="Số lượng tối đa"
                      className="h-9 w-16 text-center font-mono text-xs"
                    />
                    <Button
                      type="button"
                      size="sm"
                      onClick={handleAddApprovedItem}
                      disabled={!selectedMedId}
                    >
                      <Plus className="h-4 w-4" />
                    </Button>
                  </div>
                </div>

                {approvedItems.length > 0 && (
                  <div className="space-y-2 rounded-lg border border-[#CCFBF1] bg-[#F0FDFA] p-3">
                    <p className="text-xs font-semibold text-[#0F766E]">
                      Thuốc sẽ được phê duyệt ({approvedItems.length}):
                    </p>
                    {approvedItems.map((it) => (
                      <div
                        key={it.medicineId}
                        className="flex items-center justify-between gap-2 rounded bg-white px-2.5 py-1.5 text-xs"
                      >
                        <div className="min-w-0">
                          <p className="truncate font-semibold text-[#0F172A]">
                            {it.medicineName}
                          </p>
                          <p className="font-mono text-[10px] text-[#475569]">
                            {it.medicineId}
                          </p>
                        </div>
                        <div className="flex items-center gap-2">
                          <span className="font-mono font-bold text-[#0F766E]">
                            Tối đa: {it.maxAllowedQuantity}
                          </span>
                          <button
                            type="button"
                            onClick={() =>
                              setApprovedItems((prev) =>
                                prev.filter((x) => x.medicineId !== it.medicineId),
                              )
                            }
                            className="text-red-500 hover:text-red-700"
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                )}

                <div className="space-y-1">
                  <label className="block text-xs font-semibold text-[#0F172A]">
                    Thời hạn hiệu lực đơn thuốc (ngày)
                  </label>
                  <Input
                    type="number"
                    min={1}
                    max={180}
                    value={validDays}
                    onChange={(e) => setValidDays(Math.max(1, Number(e.target.value) || 30))}
                    className="h-9 w-32 font-mono text-xs"
                  />
                </div>

                {modalError && <p className="text-xs text-red-600">{modalError}</p>}
              </div>
            </div>

            <div className="mt-5 flex justify-end gap-2 border-t border-[#E2E8F0] pt-4">
              <Button
                type="button"
                variant="outline"
                onClick={() => setApproveTarget(null)}
                disabled={approveMutation.isPending}
              >
                Đóng
              </Button>
              <Button
                type="button"
                onClick={() => void handleConfirmApprove()}
                disabled={approveMutation.isPending}
              >
                {approveMutation.isPending && (
                  <Loader2 className="h-4 w-4 animate-spin" />
                )}
                <span>Xác nhận phê duyệt</span>
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Modal Từ chối đơn thuốc */}
      {rejectTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="w-full max-w-md space-y-4 rounded-xl border border-[#E2E8F0] bg-white p-6 shadow-xl">
            <h3 className="text-lg font-bold text-[#0F172A]">
              Từ chối đơn thuốc #{rejectTarget.id.slice(0, 8).toUpperCase()}
            </h3>
            <p className="text-xs text-[#475569]">
              Vui lòng ghi rõ lý do để khách hàng biết và tải lại đơn thuốc hợp lệ:
            </p>
            <textarea
              rows={3}
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
              placeholder="Ví dụ: Ảnh đơn thuốc bị mờ không rõ tên thuốc, thiếu chữ ký bác sĩ..."
              className="w-full rounded-lg border border-[#E2E8F0] px-3 py-2 text-sm focus:border-[#0F766E] focus:outline-none"
            />
            {modalError && <p className="text-xs text-red-600">{modalError}</p>}
            <div className="flex justify-end gap-2">
              <Button
                type="button"
                variant="outline"
                onClick={() => setRejectTarget(null)}
                disabled={rejectMutation.isPending}
              >
                Hủy
              </Button>
              <Button
                type="button"
                variant="destructive"
                onClick={() => void handleConfirmReject()}
                disabled={rejectMutation.isPending}
              >
                {rejectMutation.isPending && (
                  <Loader2 className="h-4 w-4 animate-spin" />
                )}
                <span>Xác nhận từ chối</span>
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
