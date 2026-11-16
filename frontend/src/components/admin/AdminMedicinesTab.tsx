import { useState, type FormEvent } from 'react';
import {
  AlertCircle,
  Edit3,
  Loader2,
  Plus,
  Power,
  Search,
  X,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Skeleton } from '@/components/ui/skeleton';
import { MedicineBadge } from '@/components/medicine/MedicineBadge';
import {
  useCategoriesQuery,
  useCreateMedicineMutation,
  useMedicinesQuery,
  useUpdateMedicineMutation,
} from '@/hooks/useMedicines';
import { useToast } from '@/hooks/useToast';
import { normalizeApiError } from '@/api/httpClient';
import { formatCurrencyVnd } from '@/lib/utils';
import type {
  MedicineRequest,
  MedicineResponse,
  PrescriptionType,
} from '@/types/medicine.types';

const SVG_IMAGE_PRESETS = [
  { label: 'Mặc định (Thuốc tổng hợp)', value: '/images/medicines/default-medicine.svg' },
  { label: 'Viên nén OTC', value: '/images/medicines/tablet-otc.svg' },
  { label: 'Viên nén kê đơn ETC', value: '/images/medicines/tablet-etc.svg' },
  { label: 'Kháng sinh ETC', value: '/images/medicines/antibiotic-etc.svg' },
  { label: 'Tim mạch / Huyết áp ETC', value: '/images/medicines/cardio-etc.svg' },
  { label: 'Viên sủi', value: '/images/medicines/effervescent.svg' },
  { label: 'Gói tiêu hóa', value: '/images/medicines/sachet-digestive.svg' },
  { label: 'Chai siro', value: '/images/medicines/syrup-bottle.svg' },
];

const EMPTY_FORM: MedicineRequest = {
  code: '',
  name: '',
  activeIngredient: '',
  manufacturer: '',
  unit: 'Hộp',
  price: 50000,
  imageUrl: '/images/medicines/default-medicine.svg',
  indications: '',
  contraindications: '',
  dosage: '',
  prescriptionType: 'OTC',
  categoryId: '',
  active: true,
};

export function AdminMedicinesTab() {
  const { showToast } = useToast();

  const [keyword, setKeyword] = useState('');
  const [typeFilter, setTypeFilter] = useState<PrescriptionType | ''>('');
  const [page, setPage] = useState(0);

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingMedicine, setEditingMedicine] = useState<MedicineResponse | null>(null);
  const [formState, setFormState] = useState<MedicineRequest>(EMPTY_FORM);
  const [formError, setFormError] = useState<string | null>(null);

  const categoriesQuery = useCategoriesQuery();
  const medicinesQuery = useMedicinesQuery({
    keyword: keyword.trim() || undefined,
    prescriptionType: typeFilter,
    includeInactive: true,
    page,
    size: 10,
    sortBy: 'name',
    sortDir: 'asc',
  });

  const createMutation = useCreateMedicineMutation();
  const updateMutation = useUpdateMedicineMutation();

  const categories = categoriesQuery.data ?? [];

  const openCreateModal = () => {
    setEditingMedicine(null);
    setFormState({
      ...EMPTY_FORM,
      categoryId: categories[0]?.id ?? '',
    });
    setFormError(null);
    setIsModalOpen(true);
  };

  const openEditModal = (med: MedicineResponse) => {
    setEditingMedicine(med);
    setFormState({
      code: med.code,
      name: med.name,
      activeIngredient: med.activeIngredient,
      manufacturer: med.manufacturer,
      unit: med.unit,
      price: med.price,
      imageUrl: med.imageUrl || '/images/medicines/default-medicine.svg',
      indications: med.indications,
      contraindications: med.contraindications,
      dosage: med.dosage,
      prescriptionType: med.prescriptionType,
      categoryId: med.categoryId,
      active: med.active,
    });
    setFormError(null);
    setIsModalOpen(true);
  };

  const handleSave = async (e: FormEvent) => {
    e.preventDefault();
    setFormError(null);

    const normalizedCode = formState.code.trim().toUpperCase();
    if (!/^[A-Z0-9_-]+$/.test(normalizedCode)) {
      setFormError('Mã thuốc chỉ được gồm chữ in hoa, số, dấu gạch ngang hoặc gạch dưới.');
      return;
    }
    if (!formState.categoryId) {
      setFormError('Vui lòng chọn danh mục thuốc.');
      return;
    }

    const payload: MedicineRequest = {
      ...formState,
      code: normalizedCode,
      name: formState.name.trim(),
      activeIngredient: formState.activeIngredient.trim(),
      manufacturer: formState.manufacturer.trim(),
      unit: formState.unit.trim(),
      price: Number(formState.price),
      imageUrl: formState.imageUrl?.trim() || '/images/medicines/default-medicine.svg',
      indications: formState.indications.trim(),
      contraindications: formState.contraindications.trim(),
      dosage: formState.dosage.trim(),
    };

    try {
      if (editingMedicine) {
        await updateMutation.mutateAsync({ id: editingMedicine.id, payload });
        showToast({
          title: 'Đã cập nhật thông tin thuốc',
          description: payload.name,
          variant: 'success',
        });
      } else {
        await createMutation.mutateAsync(payload);
        showToast({
          title: 'Đã thêm thuốc mới thành công',
          description: payload.name,
          variant: 'success',
        });
      }
      setIsModalOpen(false);
    } catch (error) {
      setFormError(normalizeApiError(error).message);
    }
  };

  const handleToggleActive = async (med: MedicineResponse) => {
    try {
      await updateMutation.mutateAsync({
        id: med.id,
        payload: {
          code: med.code,
          name: med.name,
          activeIngredient: med.activeIngredient,
          manufacturer: med.manufacturer,
          unit: med.unit,
          price: med.price,
          imageUrl: med.imageUrl || '/images/medicines/default-medicine.svg',
          indications: med.indications,
          contraindications: med.contraindications,
          dosage: med.dosage,
          prescriptionType: med.prescriptionType,
          categoryId: med.categoryId,
          active: !med.active,
        },
      });
      showToast({
        title: med.active ? 'Đã tạm ngừng kinh doanh thuốc' : 'Đã mở bán lại thuốc',
        description: med.name,
        variant: 'info',
      });
    } catch (error) {
      showToast({
        title: 'Không thể thay đổi trạng thái thuốc',
        description: normalizeApiError(error).message,
        variant: 'error',
      });
    }
  };

  const data = medicinesQuery.data;
  const isSaving = createMutation.isPending || updateMutation.isPending;

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-[#E2E8F0] bg-white p-4">
        <div className="flex flex-1 flex-wrap items-center gap-2">
          <div className="relative min-w-[220px] flex-1 sm:max-w-xs">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[#475569]" />
            <Input
              value={keyword}
              onChange={(e) => {
                setKeyword(e.target.value);
                setPage(0);
              }}
              placeholder="Tìm tên thuốc, mã thuốc, hoạt chất..."
              className="pl-9"
            />
          </div>

          <select
            value={typeFilter}
            onChange={(e) => {
              setTypeFilter(e.target.value as PrescriptionType | '');
              setPage(0);
            }}
            className="h-10 rounded-lg border border-[#E2E8F0] bg-white px-3 text-sm text-[#0F172A] focus:border-[#0F766E] focus:outline-none"
          >
            <option value="">Tất cả loại (OTC & ETC)</option>
            <option value="OTC">Không kê đơn (OTC)</option>
            <option value="ETC">Kê đơn (ETC)</option>
          </select>
        </div>

        <Button type="button" onClick={openCreateModal}>
          <Plus className="h-4 w-4" />
          <span>Thêm thuốc mới</span>
        </Button>
      </div>

      {medicinesQuery.isLoading ? (
        <div className="space-y-2 rounded-xl border border-[#E2E8F0] bg-white p-4">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-12 w-full" />
          ))}
        </div>
      ) : medicinesQuery.isError ? (
        <div className="rounded-xl border border-red-200 bg-red-50 p-5 text-sm text-red-700">
          {normalizeApiError(medicinesQuery.error).message}
        </div>
      ) : !data || data.items.length === 0 ? (
        <div className="rounded-xl border border-[#E2E8F0] bg-white p-10 text-center text-sm text-[#475569]">
          Không tìm thấy thuốc nào phù hợp.
        </div>
      ) : (
        <div className="overflow-hidden rounded-xl border border-[#E2E8F0] bg-white">
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-[#E2E8F0] text-left text-sm">
              <thead className="bg-[#F8FAFC] text-xs font-semibold uppercase tracking-wide text-[#475569]">
                <tr>
                  <th className="px-4 py-3">Thuốc</th>
                  <th className="px-4 py-3">Phân loại</th>
                  <th className="px-4 py-3">Danh mục</th>
                  <th className="px-4 py-3">Giá bán</th>
                  <th className="px-4 py-3">Trạng thái</th>
                  <th className="px-4 py-3 text-right">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#E2E8F0]">
                {data.items.map((med) => (
                  <tr key={med.id} className="hover:bg-[#F8FAFC]/70">
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-3">
                        <img
                          src={med.imageUrl || '/images/medicines/default-medicine.svg'}
                          alt={med.name}
                          className="h-10 w-10 shrink-0 rounded-lg border border-[#E2E8F0] bg-[#F8FAFC] object-contain p-1"
                        />
                        <div className="min-w-0">
                          <p className="font-semibold text-[#0F172A]">{med.name}</p>
                          <p className="font-mono text-xs text-[#475569]">
                            {med.code} • {med.activeIngredient}
                          </p>
                        </div>
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <MedicineBadge type={med.prescriptionType} />
                    </td>
                    <td className="px-4 py-3 text-xs text-[#475569]">
                      {med.categoryName}
                    </td>
                    <td className="px-4 py-3 font-mono font-semibold text-[#0F766E]">
                      {formatCurrencyVnd(med.price)}
                      <span className="ml-1 font-sans text-xs font-normal text-[#475569]">
                        /{med.unit}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <span
                        className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                          med.active
                            ? 'bg-[#F0FDFA] text-[#0F766E] border border-[#CCFBF1]'
                            : 'bg-slate-100 text-slate-600 border border-slate-200'
                        }`}
                      >
                        {med.active ? 'Đang bán' : 'Ngừng bán'}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-right">
                      <div className="inline-flex items-center gap-1.5">
                        <Button
                          type="button"
                          size="sm"
                          variant="outline"
                          onClick={() => openEditModal(med)}
                        >
                          <Edit3 className="h-3.5 w-3.5" />
                          <span>Sửa</span>
                        </Button>
                        <Button
                          type="button"
                          size="sm"
                          variant="outline"
                          disabled={updateMutation.isPending}
                          onClick={() => void handleToggleActive(med)}
                          title={med.active ? 'Ngừng kinh doanh' : 'Mở bán lại'}
                        >
                          <Power
                            className={`h-3.5 w-3.5 ${med.active ? 'text-[#B45309]' : 'text-[#0F766E]'}`}
                          />
                          <span>{med.active ? 'Ẩn' : 'Bật'}</span>
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {data.totalPages > 1 && (
            <div className="flex items-center justify-between border-t border-[#E2E8F0] px-4 py-3 text-xs text-[#475569]">
              <span>
                Trang <span className="font-mono font-semibold">{data.page + 1}</span> /{' '}
                <span className="font-mono font-semibold">{data.totalPages}</span> ({data.totalItems} thuốc)
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

      {/* Modal Thêm / Chỉnh sửa thuốc */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/45 p-4">
          <div className="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-xl border border-[#E2E8F0] bg-white p-6 shadow-xl">
            <div className="flex items-center justify-between border-b border-[#E2E8F0] pb-3">
              <h3 className="text-lg font-bold text-[#0F172A]">
                {editingMedicine ? `Chỉnh sửa: ${editingMedicine.name}` : 'Thêm thuốc mới'}
              </h3>
              <button
                type="button"
                onClick={() => setIsModalOpen(false)}
                className="rounded-lg p-1 text-[#475569] hover:bg-[#F8FAFC]"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            {formError && (
              <div className="mt-4 flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 p-3 text-xs text-red-700">
                <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
                <span>{formError}</span>
              </div>
            )}

            <form onSubmit={handleSave} className="mt-4 space-y-4">
              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-[#0F172A]">
                    Mã thuốc <span className="text-red-500">*</span>
                  </label>
                  <Input
                    required
                    value={formState.code}
                    onChange={(e) => setFormState({ ...formState, code: e.target.value })}
                    placeholder="VD: PARACETAMOL-500"
                    className="font-mono uppercase"
                  />
                </div>

                <div className="space-y-1">
                  <label className="text-xs font-semibold text-[#0F172A]">
                    Tên thuốc <span className="text-red-500">*</span>
                  </label>
                  <Input
                    required
                    value={formState.name}
                    onChange={(e) => setFormState({ ...formState, name: e.target.value })}
                    placeholder="Panadol Extra 500mg"
                  />
                </div>

                <div className="space-y-1">
                  <label className="text-xs font-semibold text-[#0F172A]">
                    Thành phần hoạt chất <span className="text-red-500">*</span>
                  </label>
                  <Input
                    required
                    value={formState.activeIngredient}
                    onChange={(e) =>
                      setFormState({ ...formState, activeIngredient: e.target.value })
                    }
                    placeholder="Paracetamol 500mg + Caffeine 65mg"
                  />
                </div>

                <div className="space-y-1">
                  <label className="text-xs font-semibold text-[#0F172A]">
                    Nhà sản xuất <span className="text-red-500">*</span>
                  </label>
                  <Input
                    required
                    value={formState.manufacturer}
                    onChange={(e) =>
                      setFormState({ ...formState, manufacturer: e.target.value })
                    }
                    placeholder="GSK / Sanofi / DHG..."
                  />
                </div>

                <div className="space-y-1">
                  <label className="text-xs font-semibold text-[#0F172A]">
                    Giá bán (VNĐ) <span className="text-red-500">*</span>
                  </label>
                  <Input
                    type="number"
                    min={100}
                    step={100}
                    required
                    value={formState.price}
                    onChange={(e) =>
                      setFormState({ ...formState, price: Number(e.target.value) })
                    }
                    className="font-mono"
                  />
                </div>

                <div className="space-y-1">
                  <label className="text-xs font-semibold text-[#0F172A]">
                    Đơn vị tính <span className="text-red-500">*</span>
                  </label>
                  <Input
                    required
                    value={formState.unit}
                    onChange={(e) => setFormState({ ...formState, unit: e.target.value })}
                    placeholder="Hộp / Vỉ / Chai / Gói"
                  />
                </div>

                <div className="space-y-1">
                  <label className="text-xs font-semibold text-[#0F172A]">
                    Phân loại kê đơn <span className="text-red-500">*</span>
                  </label>
                  <select
                    value={formState.prescriptionType}
                    onChange={(e) =>
                      setFormState({
                        ...formState,
                        prescriptionType: e.target.value as PrescriptionType,
                      })
                    }
                    className="h-10 w-full rounded-lg border border-[#E2E8F0] bg-white px-3 text-sm text-[#0F172A]"
                  >
                    <option value="OTC">Không kê đơn (OTC)</option>
                    <option value="ETC">Thuốc kê đơn (ETC)</option>
                  </select>
                </div>

                <div className="space-y-1">
                  <label className="text-xs font-semibold text-[#0F172A]">
                    Danh mục <span className="text-red-500">*</span>
                  </label>
                  <select
                    required
                    value={formState.categoryId}
                    onChange={(e) =>
                      setFormState({ ...formState, categoryId: e.target.value })
                    }
                    className="h-10 w-full rounded-lg border border-[#E2E8F0] bg-white px-3 text-sm text-[#0F172A]"
                  >
                    <option value="">-- Chọn danh mục --</option>
                    {categories.map((cat) => (
                      <option key={cat.id} value={cat.id}>
                        {cat.name}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-1">
                  <label className="text-xs font-semibold text-[#0F172A]">
                    Ảnh minh họa SVG mẫu
                  </label>
                  <select
                    value={formState.imageUrl || ''}
                    onChange={(e) =>
                      setFormState({ ...formState, imageUrl: e.target.value })
                    }
                    className="h-10 w-full rounded-lg border border-[#E2E8F0] bg-white px-3 text-sm text-[#0F172A]"
                  >
                    {SVG_IMAGE_PRESETS.map((preset) => (
                      <option key={preset.value} value={preset.value}>
                        {preset.label}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="space-y-1">
                  <label className="text-xs font-semibold text-[#0F172A]">
                    Đường dẫn ảnh tùy chỉnh
                  </label>
                  <Input
                    value={formState.imageUrl || ''}
                    onChange={(e) =>
                      setFormState({ ...formState, imageUrl: e.target.value })
                    }
                    className="font-mono text-xs"
                  />
                </div>
              </div>

              <div className="space-y-1">
                <label className="text-xs font-semibold text-[#0F172A]">
                  Chỉ định điều trị <span className="text-red-500">*</span>
                </label>
                <textarea
                  required
                  rows={2}
                  value={formState.indications}
                  onChange={(e) =>
                    setFormState({ ...formState, indications: e.target.value })
                  }
                  className="w-full rounded-lg border border-[#E2E8F0] px-3 py-2 text-sm focus:border-[#0F766E] focus:outline-none"
                />
              </div>

              <div className="space-y-1">
                <label className="text-xs font-semibold text-[#0F172A]">
                  Chống chỉ định & Thận trọng <span className="text-red-500">*</span>
                </label>
                <textarea
                  required
                  rows={2}
                  value={formState.contraindications}
                  onChange={(e) =>
                    setFormState({ ...formState, contraindications: e.target.value })
                  }
                  className="w-full rounded-lg border border-[#E2E8F0] px-3 py-2 text-sm focus:border-[#0F766E] focus:outline-none"
                />
              </div>

              <div className="space-y-1">
                <label className="text-xs font-semibold text-[#0F172A]">
                  Cách dùng & Liều lượng <span className="text-red-500">*</span>
                </label>
                <textarea
                  required
                  rows={2}
                  value={formState.dosage}
                  onChange={(e) => setFormState({ ...formState, dosage: e.target.value })}
                  className="w-full rounded-lg border border-[#E2E8F0] px-3 py-2 text-sm focus:border-[#0F766E] focus:outline-none"
                />
              </div>

              <label className="inline-flex items-center gap-2 text-sm text-[#0F172A]">
                <input
                  type="checkbox"
                  checked={formState.active}
                  onChange={(e) => setFormState({ ...formState, active: e.target.checked })}
                  className="h-4 w-4 accent-[#0F766E]"
                />
                <span>Đang kinh doanh (hiển thị cho khách hàng)</span>
              </label>

              <div className="flex justify-end gap-2 border-t border-[#E2E8F0] pt-4">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setIsModalOpen(false)}
                  disabled={isSaving}
                >
                  Hủy
                </Button>
                <Button type="submit" disabled={isSaving}>
                  {isSaving && <Loader2 className="h-4 w-4 animate-spin" />}
                  <span>{editingMedicine ? 'Lưu thay đổi' : 'Tạo thuốc mới'}</span>
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
