import { useEffect, useState, type ChangeEvent, type FormEvent } from 'react';
import { Navigate } from 'react-router-dom';
import {
  AlertCircle,
  Check,
  Copy,
  Eye,
  FileText,
  ImagePlus,
  Loader2,
  Plus,
  Sparkles,
  Trash2,
  Upload,
  X,
} from 'lucide-react';
import { AppShell } from '@/components/layout/AppShell';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Skeleton } from '@/components/ui/skeleton';
import { PrescriptionStatusBadge } from '@/components/prescription/PrescriptionStatusBadge';
import { PrescriptionImageViewer } from '@/components/prescription/PrescriptionImageViewer';
import { useAuth } from '@/hooks/useAuth';
import { useMedicinesQuery } from '@/hooks/useMedicines';
import {
  useMyPrescriptionsQuery,
  useUploadPrescriptionMutation,
} from '@/hooks/usePrescriptions';
import { useToast } from '@/hooks/useToast';
import { normalizeApiError } from '@/api/httpClient';
import { formatDateTimeVi } from '@/lib/utils';
import type { PrescriptionResponse, PrescriptionStatus } from '@/types/prescription.types';

interface RequestedMedDraft {
  medicineId: string;
  medicineName: string;
  dosageNote: string;
  quantity: number;
}

const STATUS_FILTERS: { value: PrescriptionStatus | ''; label: string }[] = [
  { value: '', label: 'Tất cả' },
  { value: 'CHO_DUYET', label: 'Chờ duyệt' },
  { value: 'DA_DUYET', label: 'Đã duyệt' },
  { value: 'TU_CHOI', label: 'Từ chối' },
];

export function PrescriptionsPage() {
  const { user } = useAuth();
  const { showToast } = useToast();

  const [doctorInfo, setDoctorInfo] = useState('');
  const [diagnosisNote, setDiagnosisNote] = useState('');
  const [selectedMeds, setSelectedMeds] = useState<RequestedMedDraft[]>([]);
  const [pickedEtcId, setPickedEtcId] = useState('');
  const [imageFile, setImageFile] = useState<File | null>(null);
  const [imagePreviewUrl, setImagePreviewUrl] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);

  const [statusFilter, setStatusFilter] = useState<PrescriptionStatus | ''>('');
  const [page, setPage] = useState(0);
  const [previewPrescription, setPreviewPrescription] = useState<PrescriptionResponse | null>(
    null,
  );
  const [copiedId, setCopiedId] = useState<string | null>(null);

  const isCustomer = user?.role === 'KHACH_HANG';

  const etcMedicinesQuery = useMedicinesQuery({
    prescriptionType: 'ETC',
    page: 0,
    size: 50,
  });

  const myPrescriptionsQuery = useMyPrescriptionsQuery(
    { status: statusFilter, page, size: 10 },
    isCustomer,
  );

  const uploadMutation = useUploadPrescriptionMutation();

  useEffect(() => {
    return () => {
      if (imagePreviewUrl) {
        URL.revokeObjectURL(imagePreviewUrl);
      }
    };
  }, [imagePreviewUrl]);

  const setSelectedFileWithPreview = (file: File) => {
    if (imagePreviewUrl) {
      URL.revokeObjectURL(imagePreviewUrl);
    }
    setImageFile(file);
    setImagePreviewUrl(URL.createObjectURL(file));
    setFormError(null);
  };

  const handleFileChange = (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (!['image/jpeg', 'image/png'].includes(file.type)) {
      setFormError('Vui lòng chỉ chọn tệp ảnh định dạng JPG hoặc PNG.');
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      setFormError('Dung lượng ảnh tối đa là 5MB.');
      return;
    }
    setSelectedFileWithPreview(file);
  };

  // Vẽ ảnh đơn thuốc mẫu PNG bằng Canvas để thuận tiện kiểm thử luồng Upload -> MinIO
  const handleCreateSampleImage = () => {
    const canvas = document.createElement('canvas');
    canvas.width = 720;
    canvas.height = 460;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.fillStyle = '#FFFFFF';
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    ctx.strokeStyle = '#0F766E';
    ctx.lineWidth = 4;
    ctx.strokeRect(16, 16, canvas.width - 32, canvas.height - 32);

    ctx.fillStyle = '#F0FDFA';
    ctx.fillRect(20, 20, canvas.width - 40, 78);

    ctx.fillStyle = '#0F766E';
    ctx.font = 'bold 22px sans-serif';
    ctx.fillText('ĐƠN THUỐC ĐIỆN TỬ MẪU (KIỂM THỬ HỆ THỐNG)', 40, 56);

    ctx.fillStyle = '#475569';
    ctx.font = '14px sans-serif';
    ctx.fillText(
      `Ngày kê: ${new Date().toLocaleDateString('vi-VN')}  |  Cơ sở: ${doctorInfo.trim() || 'Phòng khám Đa khoa An Tâm'}`,
      40,
      82,
    );

    ctx.fillStyle = '#0F172A';
    ctx.font = 'bold 16px sans-serif';
    ctx.fillText(`Bệnh nhân: ${user?.fullName || 'Khách hàng'}`, 40, 134);

    ctx.font = '15px sans-serif';
    ctx.fillText(
      `Chẩn đoán / Ghi chú: ${diagnosisNote.trim() || 'Viêm đường hô hấp trên - dùng thuốc theo chỉ định'}`,
      40,
      166,
    );

    ctx.fillStyle = '#0F766E';
    ctx.font = 'bold 16px sans-serif';
    ctx.fillText('Danh mục thuốc đề nghị:', 40, 210);

    ctx.fillStyle = '#0F172A';
    ctx.font = '14px monospace';
    const medsToDraw =
      selectedMeds.length > 0
        ? selectedMeds
        : [{ medicineId: 'sample', medicineName: 'Thuốc kê đơn theo chẩn đoán bác sĩ', dosageNote: 'Theo chỉ dẫn', quantity: 1 }];

    medsToDraw.slice(0, 5).forEach((m, idx) => {
      ctx.fillText(
        `${idx + 1}. ${m.medicineName} — SL: ${m.quantity} (${m.dosageNote || 'Theo chỉ định'})`,
        48,
        244 + idx * 30,
      );
    });

    ctx.fillStyle = '#B45309';
    ctx.font = 'italic 13px sans-serif';
    ctx.fillText('Xác nhận bởi bác sĩ điều trị (Mẫu kiểm thử MinIO PNG)', 40, 418);

    canvas.toBlob((blob) => {
      if (!blob) return;
      const sampleFile = new File([blob], `don-thuoc-mau-${Date.now()}.png`, {
        type: 'image/png',
      });
      setSelectedFileWithPreview(sampleFile);
      showToast({
        title: 'Đã tạo ảnh đơn thuốc mẫu (PNG)',
        description: 'Bạn có thể xem trước ảnh bên dưới và bấm Gửi đơn thuốc.',
        variant: 'info',
      });
    }, 'image/png');
  };

  const handleAddRequestedMed = () => {
    if (!pickedEtcId) return;
    const med = (etcMedicinesQuery.data?.items ?? []).find((m) => m.id === pickedEtcId);
    if (!med) return;
    if (selectedMeds.some((m) => m.medicineId === med.id)) {
      setPickedEtcId('');
      return;
    }
    setSelectedMeds((prev) => [
      ...prev,
      {
        medicineId: med.id,
        medicineName: med.name,
        dosageNote: med.dosage || 'Theo chỉ định bác sĩ',
        quantity: 1,
      },
    ]);
    setPickedEtcId('');
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setFormError(null);

    if (!isCustomer) {
      setFormError('Chỉ tài khoản Khách hàng (KHACH_HANG) mới có thể gửi đơn thuốc mới.');
      return;
    }

    if (!imageFile) {
      setFormError('Vui lòng tải ảnh đơn thuốc (JPG/PNG) hoặc dùng nút tạo ảnh mẫu.');
      return;
    }

    // Gộp thông tin bác sĩ, chẩn đoán và thuốc mong muốn vào notes của PrescriptionUploadForm
    const noteLines: string[] = [];
    if (doctorInfo.trim()) {
      noteLines.push(`Bác sĩ / Cơ sở khám: ${doctorInfo.trim()}`);
    }
    if (diagnosisNote.trim()) {
      noteLines.push(`Chẩn đoán / Ghi chú: ${diagnosisNote.trim()}`);
    }
    if (selectedMeds.length > 0) {
      noteLines.push('Thuốc kê đơn cần mua:');
      selectedMeds.forEach((m, i) => {
        noteLines.push(
          `- ${i + 1}. ${m.medicineName} (ID: ${m.medicineId}) × ${m.quantity}${m.dosageNote ? ` | Liều: ${m.dosageNote}` : ''}`,
        );
      });
    }

    try {
      await uploadMutation.mutateAsync({
        file: imageFile,
        notes: noteLines.join('\n'),
      });
      showToast({
        title: 'Đã gửi đơn thuốc thành công',
        description: 'Đơn thuốc đang ở trạng thái Chờ duyệt để Dược sĩ kiểm tra.',
        variant: 'success',
      });
      setDoctorInfo('');
      setDiagnosisNote('');
      setSelectedMeds([]);
      if (imagePreviewUrl) {
        URL.revokeObjectURL(imagePreviewUrl);
      }
      setImageFile(null);
      setImagePreviewUrl(null);
    } catch (error) {
      const apiErr = normalizeApiError(error);
      setFormError(apiErr.message);
      showToast({
        title: 'Gửi đơn thuốc thất bại',
        description: apiErr.message,
        variant: 'error',
      });
    }
  };

  const handleCopyId = async (id: string) => {
    try {
      await navigator.clipboard.writeText(id);
      setCopiedId(id);
      setTimeout(() => setCopiedId(null), 1500);
    } catch {
      // Bỏ qua nếu trình duyệt chặn clipboard
    }
  };

  const etcItems = etcMedicinesQuery.data?.items ?? [];
  const prescriptionsData = myPrescriptionsQuery.data;

  if (user && (user.role === 'DUOC_SI' || user.role === 'ADMIN')) {
    return <Navigate to="/admin" replace />;
  }

  return (
    <AppShell>
      <div className="space-y-8">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-[#0F172A]">
            Quản lý Đơn thuốc kê đơn (ETC)
          </h1>
          <p className="mt-1 text-sm text-[#475569]">
            Tải ảnh đơn thuốc của bác sĩ để Dược sĩ thẩm định trước khi đặt mua các loại thuốc kê đơn.
          </p>
        </div>

        <div className="grid gap-6 lg:grid-cols-12">
          {/* Cột trái: Form gửi đơn thuốc mới */}
          <section className="h-fit space-y-5 rounded-xl border border-[#E2E8F0] bg-white p-6 lg:col-span-5">
            <div className="flex items-center gap-2 border-b border-[#E2E8F0] pb-3">
              <FileText className="h-5 w-5 text-[#0F766E]" />
              <h2 className="text-base font-bold text-[#0F172A]">
                Gửi đơn thuốc mới
              </h2>
            </div>

            {formError && (
              <div className="flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 p-3 text-xs text-red-700">
                <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
                <span>{formError}</span>
              </div>
            )}

            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="space-y-1.5">
                <label className="block text-sm font-medium text-[#0F172A]">
                  Bác sĩ / Cơ sở khám bệnh
                </label>
                <Input
                  value={doctorInfo}
                  onChange={(e) => setDoctorInfo(e.target.value)}
                  placeholder="Ví dụ: BS. Nguyễn Minh - BV Bạch Mai"
                />
              </div>

              <div className="space-y-1.5">
                <label className="block text-sm font-medium text-[#0F172A]">
                  Chẩn đoán & Ghi chú cho Dược sĩ
                </label>
                <textarea
                  rows={3}
                  value={diagnosisNote}
                  onChange={(e) => setDiagnosisNote(e.target.value)}
                  placeholder="Mô tả triệu chứng, tiền sử dị ứng hoặc ghi chú liều dùng..."
                  className="w-full rounded-lg border border-[#E2E8F0] bg-white px-3 py-2 text-sm text-[#0F172A] focus:border-[#0F766E] focus:outline-none focus:ring-2 focus:ring-[#0F766E]/20"
                />
              </div>

              {/* Chọn nhanh thuốc ETC mong muốn */}
              <div className="space-y-2 rounded-lg border border-[#E2E8F0] bg-[#F8FAFC] p-3">
                <label className="block text-xs font-semibold text-[#0F172A]">
                  Thuốc kê đơn (ETC) trong toa (tùy chọn)
                </label>
                <div className="flex gap-2">
                  <select
                    value={pickedEtcId}
                    onChange={(e) => setPickedEtcId(e.target.value)}
                    className="h-9 flex-1 rounded-lg border border-[#E2E8F0] bg-white px-2.5 text-xs text-[#0F172A] focus:border-[#0F766E] focus:outline-none"
                  >
                    <option value="">-- Chọn thuốc ETC từ danh mục --</option>
                    {etcItems.map((med) => (
                      <option key={med.id} value={med.id}>
                        {med.name} ({med.unit})
                      </option>
                    ))}
                  </select>
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    onClick={handleAddRequestedMed}
                    disabled={!pickedEtcId}
                  >
                    <Plus className="h-4 w-4" />
                  </Button>
                </div>

                {selectedMeds.length > 0 && (
                  <div className="space-y-2 pt-1">
                    {selectedMeds.map((item) => (
                      <div
                        key={item.medicineId}
                        className="flex flex-col gap-1.5 rounded-lg border border-[#E2E8F0] bg-white p-2.5 text-xs"
                      >
                        <div className="flex items-center justify-between gap-2">
                          <span className="font-semibold text-[#0F172A]">
                            {item.medicineName}
                          </span>
                          <button
                            type="button"
                            onClick={() =>
                              setSelectedMeds((prev) =>
                                prev.filter((m) => m.medicineId !== item.medicineId),
                              )
                            }
                            className="text-red-500 hover:text-red-700"
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                          </button>
                        </div>
                        <div className="flex items-center gap-2">
                          <Input
                            value={item.dosageNote}
                            onChange={(e) =>
                              setSelectedMeds((prev) =>
                                prev.map((m) =>
                                  m.medicineId === item.medicineId
                                    ? { ...m, dosageNote: e.target.value }
                                    : m,
                                ),
                              )
                            }
                            placeholder="Liều dùng"
                            className="h-7 flex-1 text-xs"
                          />
                          <Input
                            type="number"
                            min={1}
                            max={99}
                            value={item.quantity}
                            onChange={(e) =>
                              setSelectedMeds((prev) =>
                                prev.map((m) =>
                                  m.medicineId === item.medicineId
                                    ? {
                                        ...m,
                                        quantity: Math.max(1, Number(e.target.value) || 1),
                                      }
                                    : m,
                                ),
                              )
                            }
                            className="h-7 w-16 text-center font-mono text-xs"
                          />
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Upload ảnh đơn thuốc JPG/PNG */}
              <div className="space-y-2">
                <div className="flex items-center justify-between gap-2">
                  <label className="block text-sm font-medium text-[#0F172A]">
                    Ảnh chụp đơn thuốc (JPG/PNG ≤ 5MB) <span className="text-red-500">*</span>
                  </label>
                  <button
                    type="button"
                    onClick={handleCreateSampleImage}
                    className="inline-flex items-center gap-1 rounded-md border border-[#CCFBF1] bg-[#F0FDFA] px-2.5 py-1 text-xs font-semibold text-[#0F766E] hover:bg-[#CCFBF1]/50"
                  >
                    <Sparkles className="h-3.5 w-3.5" />
                    <span>Tạo ảnh đơn thuốc mẫu</span>
                  </button>
                </div>

                <label className="flex cursor-pointer flex-col items-center justify-center rounded-xl border-2 border-dashed border-[#E2E8F0] bg-[#F8FAFC] p-5 text-center transition-colors hover:border-[#0F766E]/60">
                  <ImagePlus className="h-7 w-7 text-[#0F766E]" />
                  <span className="mt-2 text-xs font-medium text-[#0F172A]">
                    {imageFile ? imageFile.name : 'Nhấn để chọn ảnh đơn thuốc từ máy'}
                  </span>
                  <span className="mt-0.5 text-[11px] text-[#475569]">
                    Hỗ trợ JPG, PNG (tối đa 5MB) • Lưu trữ bảo mật trên MinIO
                  </span>
                  <input
                    type="file"
                    accept="image/jpeg,image/png"
                    onChange={handleFileChange}
                    className="sr-only"
                  />
                </label>

                {imagePreviewUrl && (
                  <div className="relative overflow-hidden rounded-lg border border-[#E2E8F0] bg-[#F8FAFC] p-2">
                    <img
                      src={imagePreviewUrl}
                      alt="Xem trước đơn thuốc"
                      className="max-h-52 w-full rounded object-contain"
                    />
                  </div>
                )}
              </div>

              <Button
                type="submit"
                className="w-full"
                disabled={uploadMutation.isPending || !isCustomer}
              >
                {uploadMutation.isPending ? (
                  <>
                    <Loader2 className="h-4 w-4 animate-spin" />
                    <span>Đang tải đơn thuốc lên...</span>
                  </>
                ) : (
                  <>
                    <Upload className="h-4 w-4" />
                    <span>Gửi đơn thuốc để Dược sĩ duyệt</span>
                  </>
                )}
              </Button>
            </form>
          </section>

          {/* Cột phải: Danh sách đơn thuốc đã gửi */}
          <section className="space-y-4 lg:col-span-7">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h2 className="text-lg font-bold text-[#0F172A]">
                Đơn thuốc đã gửi của bạn
              </h2>
              <div className="flex flex-wrap gap-1.5">
                {STATUS_FILTERS.map((tab) => {
                  const active = statusFilter === tab.value;
                  return (
                    <button
                      key={tab.value || 'ALL'}
                      type="button"
                      onClick={() => {
                        setStatusFilter(tab.value);
                        setPage(0);
                      }}
                      className={`rounded-full border px-3 py-1 text-xs font-semibold transition-colors ${
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
            </div>

            {!isCustomer ? (
              <div className="rounded-xl border border-[#E2E8F0] bg-white p-8 text-center text-sm text-[#475569]">
                Đăng nhập bằng tài khoản Khách hàng để xem danh sách đơn thuốc cá nhân.
              </div>
            ) : myPrescriptionsQuery.isLoading ? (
              <div className="space-y-3">
                {Array.from({ length: 3 }).map((_, idx) => (
                  <Skeleton key={idx} className="h-36 w-full rounded-xl" />
                ))}
              </div>
            ) : myPrescriptionsQuery.isError ? (
              <div className="rounded-xl border border-red-200 bg-red-50 p-5 text-sm text-red-700">
                {normalizeApiError(myPrescriptionsQuery.error).message}
              </div>
            ) : !prescriptionsData || prescriptionsData.items.length === 0 ? (
              <div className="rounded-xl border border-[#E2E8F0] bg-white p-10 text-center text-sm text-[#475569]">
                Chưa có đơn thuốc nào khớp với bộ lọc hiện tại.
              </div>
            ) : (
              <div className="space-y-4">
                {prescriptionsData.items.map((p) => (
                  <article
                    key={p.id}
                    className="space-y-3 rounded-xl border border-[#E2E8F0] bg-white p-5"
                  >
                    <div className="flex flex-wrap items-start justify-between gap-2 border-b border-[#E2E8F0] pb-3">
                      <div className="space-y-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <PrescriptionStatusBadge status={p.status} />
                          <span className="font-mono text-xs text-[#475569]">
                            #{p.id.slice(0, 8).toUpperCase()}
                          </span>
                          <button
                            type="button"
                            onClick={() => void handleCopyId(p.id)}
                            title="Sao chép mã UUID đầy đủ"
                            className="inline-flex items-center gap-1 rounded border border-[#E2E8F0] px-1.5 py-0.5 font-mono text-[11px] text-[#475569] hover:border-[#0F766E] hover:text-[#0F766E]"
                          >
                            {copiedId === p.id ? (
                              <>
                                <Check className="h-3 w-3 text-[#0F766E]" />
                                <span>Đã chép</span>
                              </>
                            ) : (
                              <>
                                <Copy className="h-3 w-3" />
                                <span>Chép UUID</span>
                              </>
                            )}
                          </button>
                        </div>
                        <p className="text-xs text-[#475569]">
                          Gửi lúc: {formatDateTimeVi(p.createdAt)}
                          {p.expiresAt && (
                            <>
                              {' • '}Hiệu lực đến:{' '}
                              <span className="font-semibold text-[#0F766E]">
                                {formatDateTimeVi(p.expiresAt)}
                              </span>
                            </>
                          )}
                        </p>
                      </div>

                      <Button
                        type="button"
                        size="sm"
                        variant="outline"
                        onClick={() => setPreviewPrescription(p)}
                      >
                        <Eye className="h-4 w-4" />
                        <span>Xem ảnh đơn</span>
                      </Button>
                    </div>

                    {p.notes && (
                      <p className="whitespace-pre-line rounded-lg bg-[#F8FAFC] p-3 text-xs leading-relaxed text-[#0F172A]">
                        {p.notes}
                      </p>
                    )}

                    {p.status === 'TU_CHOI' && p.rejectionReason && (
                      <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-xs text-red-700">
                        <span className="font-semibold">Lý do từ chối: </span>
                        {p.rejectionReason}
                      </div>
                    )}

                    {p.status === 'DA_DUYET' && p.items && p.items.length > 0 && (
                      <div className="space-y-1.5">
                        <p className="text-xs font-semibold text-[#0F766E]">
                          Danh mục thuốc được Dược sĩ phê duyệt mua:
                        </p>
                        <div className="flex flex-wrap gap-2">
                          {p.items.map((it) => (
                            <span
                              key={it.id}
                              className="inline-flex items-center gap-1.5 rounded-lg border border-[#CCFBF1] bg-[#F0FDFA] px-2.5 py-1 text-xs text-[#0F766E]"
                            >
                              <span className="font-medium">{it.medicineName}</span>
                              <span className="font-mono font-bold">
                                (tối đa {it.maxAllowedQuantity})
                              </span>
                            </span>
                          ))}
                        </div>
                      </div>
                    )}
                  </article>
                ))}

                {prescriptionsData.totalPages > 1 && (
                  <div className="flex items-center justify-between rounded-xl border border-[#E2E8F0] bg-white px-4 py-3 text-xs text-[#475569]">
                    <span>
                      Trang{' '}
                      <span className="font-mono font-semibold">
                        {prescriptionsData.page + 1}
                      </span>{' '}
                      /{' '}
                      <span className="font-mono font-semibold">
                        {prescriptionsData.totalPages}
                      </span>{' '}
                      ({prescriptionsData.totalElements} đơn thuốc)
                    </span>
                    <div className="flex gap-2">
                      <Button
                        type="button"
                        size="sm"
                        variant="outline"
                        onClick={() => setPage((p) => Math.max(0, p - 1))}
                        disabled={prescriptionsData.page <= 0}
                      >
                        Trước
                      </Button>
                      <Button
                        type="button"
                        size="sm"
                        variant="outline"
                        onClick={() => setPage((p) => p + 1)}
                        disabled={
                          prescriptionsData.page + 1 >= prescriptionsData.totalPages
                        }
                      >
                        Sau
                      </Button>
                    </div>
                  </div>
                )}
              </div>
            )}
          </section>
        </div>
      </div>

      {/* Modal xem ảnh đơn thuốc từ MinIO */}
      {previewPrescription && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="w-full max-w-2xl space-y-4 rounded-xl border border-[#E2E8F0] bg-white p-6 shadow-xl">
            <div className="flex items-center justify-between border-b border-[#E2E8F0] pb-3">
              <div>
                <h3 className="text-base font-bold text-[#0F172A]">
                  Ảnh đơn thuốc #{previewPrescription.id.slice(0, 8).toUpperCase()}
                </h3>
                <p className="font-mono text-xs text-[#475569]">
                  {previewPrescription.id}
                </p>
              </div>
              <button
                type="button"
                onClick={() => setPreviewPrescription(null)}
                className="rounded-lg p-1.5 text-[#475569] hover:bg-[#F8FAFC]"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <PrescriptionImageViewer prescriptionId={previewPrescription.id} />

            <div className="flex justify-end">
              <Button variant="outline" onClick={() => setPreviewPrescription(null)}>
                Đóng
              </Button>
            </div>
          </div>
        </div>
      )}
    </AppShell>
  );
}
