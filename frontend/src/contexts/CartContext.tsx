import {
  createContext,
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { orderApi } from '@/api/order.api';
import { useAuth } from '@/hooks/useAuth';
import { useToast } from '@/hooks/useToast';
import type { PrescriptionType } from '@/types/medicine.types';

export interface LocalCartItem {
  medicineId: string;
  medicineCode: string;
  medicineName: string;
  unit: string;
  price: number;
  imageUrl: string | null;
  prescriptionType: PrescriptionType;
  quantity: number;
}

export interface AddCartItemInput {
  medicineId: string;
  medicineCode?: string;
  medicineName: string;
  unit?: string;
  price: number;
  imageUrl?: string | null;
  prescriptionType: PrescriptionType;
  quantity?: number;
}

export interface CartContextValue {
  items: LocalCartItem[];
  totalItems: number;
  totalAmount: number;
  hasEtcItems: boolean;
  isSyncing: boolean;
  getItemQuantity: (medicineId: string) => number;
  addItem: (input: AddCartItemInput, maxAvailable?: number | null) => Promise<boolean>;
  updateQuantity: (
    medicineId: string,
    quantity: number,
    maxAvailable?: number | null,
  ) => Promise<void>;
  removeItem: (medicineId: string) => Promise<void>;
  clearCart: () => Promise<void>;
  syncCartToBackend: () => Promise<void>;
}

export const CartContext = createContext<CartContextValue | null>(null);

const GUEST_STORAGE_KEY = 'pharmacy_cart_guest';
const getStorageKey = (userId?: string | null) =>
  userId ? `pharmacy_cart_${userId}` : GUEST_STORAGE_KEY;

function readStorage(key: string): LocalCartItem[] {
  const raw = localStorage.getItem(key);
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? (parsed as LocalCartItem[]) : [];
  } catch {
    localStorage.removeItem(key);
    return [];
  }
}

function writeStorage(key: string, items: LocalCartItem[]): void {
  localStorage.setItem(key, JSON.stringify(items));
}

export function CartProvider({ children }: { children: ReactNode }) {
  const { user, isAuthenticated } = useAuth();
  const { showToast } = useToast();

  const isCustomer = isAuthenticated && user?.role === 'KHACH_HANG';
  const storageKey = getStorageKey(user?.id);

  const [items, setItems] = useState<LocalCartItem[]>(() => readStorage(storageKey));
  const [isSyncing, setIsSyncing] = useState(false);

  // Tải & đồng bộ giỏ hàng khi người dùng đổi trạng thái đăng nhập
  useEffect(() => {
    const localItems = readStorage(storageKey);
    setItems(localItems);

    if (!isCustomer) {
      return;
    }

    let cancelled = false;
    const syncOnLogin = async () => {
      setIsSyncing(true);
      try {
        const guestItems = readStorage(GUEST_STORAGE_KEY);
        if (guestItems.length > 0) {
          for (const g of guestItems) {
            await orderApi.addCartItem({ medicineId: g.medicineId, quantity: g.quantity });
          }
          localStorage.removeItem(GUEST_STORAGE_KEY);
        }

        const remote = await orderApi.getCart();
        if (!cancelled) {
          const mapped: LocalCartItem[] = (remote.items ?? []).map((item) => ({
            medicineId: item.medicineId,
            medicineCode: item.medicineCode,
            medicineName: item.medicineName,
            unit: item.unit,
            price: Number(item.price),
            imageUrl: item.imageUrl,
            prescriptionType: item.prescriptionType,
            quantity: item.quantity,
          }));
          setItems(mapped);
          writeStorage(storageKey, mapped);
        }
      } catch {
        // Giữ lại giỏ local nếu order-service tạm chưa bật
      } finally {
        if (!cancelled) {
          setIsSyncing(false);
        }
      }
    };

    void syncOnLogin();

    return () => {
      cancelled = true;
    };
  }, [isCustomer, storageKey]);

  const persistLocal = useCallback(
    (next: LocalCartItem[]) => {
      setItems(next);
      writeStorage(storageKey, next);
    },
    [storageKey],
  );

  const getItemQuantity = useCallback(
    (medicineId: string) => items.find((i) => i.medicineId === medicineId)?.quantity ?? 0,
    [items],
  );

  const addItem = useCallback(
    async (input: AddCartItemInput, maxAvailable?: number | null): Promise<boolean> => {
      const addQty = Math.max(1, input.quantity ?? 1);
      const existing = items.find((i) => i.medicineId === input.medicineId);
      const currentQty = existing?.quantity ?? 0;
      const targetQty = currentQty + addQty;

      if (typeof maxAvailable === 'number' && maxAvailable >= 0) {
        if (maxAvailable === 0) {
          showToast({
            variant: 'warning',
            title: 'Sản phẩm đã hết hàng',
            description: `${input.medicineName} hiện tạm hết tồn kho khả dụng.`,
          });
          return false;
        }
        if (targetQty > maxAvailable) {
          showToast({
            variant: 'warning',
            title: 'Vượt quá số lượng tồn kho',
            description: `Bạn đã có ${currentQty} trong giỏ. Tồn kho khả dụng chỉ còn ${maxAvailable}.`,
          });
          return false;
        }
      }

      const nextItems = existing
        ? items.map((i) =>
            i.medicineId === input.medicineId ? { ...i, quantity: targetQty } : i,
          )
        : [
            ...items,
            {
              medicineId: input.medicineId,
              medicineCode: input.medicineCode || 'MED',
              medicineName: input.medicineName,
              unit: input.unit || 'Hộp',
              price: Number(input.price),
              imageUrl: input.imageUrl ?? '/images/medicines/default-medicine.svg',
              prescriptionType: input.prescriptionType,
              quantity: addQty,
            },
          ];

      persistLocal(nextItems);

      if (isCustomer) {
        try {
          await orderApi.addCartItem({ medicineId: input.medicineId, quantity: addQty });
        } catch {
          // Đã lưu cục bộ, sẽ đồng bộ lại trước bước Checkout
        }
      }

      showToast({
        variant: 'success',
        title: 'Đã thêm vào giỏ hàng',
        description: `${input.medicineName} (x${addQty})`,
      });
      return true;
    },
    [isCustomer, items, persistLocal, showToast],
  );

  const updateQuantity = useCallback(
    async (
      medicineId: string,
      quantity: number,
      maxAvailable?: number | null,
    ): Promise<void> => {
      if (quantity <= 0) {
        const nextItems = items.filter((i) => i.medicineId !== medicineId);
        persistLocal(nextItems);
        if (isCustomer) {
          try {
            await orderApi.removeCartItem(medicineId);
          } catch {
            // Bỏ qua lỗi mạng tạm thời
          }
        }
        return;
      }

      if (typeof maxAvailable === 'number' && maxAvailable >= 0 && quantity > maxAvailable) {
        showToast({
          variant: 'warning',
          title: 'Vượt quá số lượng tồn kho',
          description: `Kho chỉ còn tối đa ${maxAvailable} sản phẩm.`,
        });
        return;
      }

      const nextItems = items.map((i) =>
        i.medicineId === medicineId ? { ...i, quantity } : i,
      );
      persistLocal(nextItems);

      if (isCustomer) {
        try {
          await orderApi.updateCartItem(medicineId, { quantity });
        } catch {
          // Đồng bộ lại trước khi Checkout
        }
      }
    },
    [isCustomer, items, persistLocal, showToast],
  );

  const removeItem = useCallback(
    async (medicineId: string): Promise<void> => {
      const target = items.find((i) => i.medicineId === medicineId);
      const nextItems = items.filter((i) => i.medicineId !== medicineId);
      persistLocal(nextItems);

      if (isCustomer) {
        try {
          await orderApi.removeCartItem(medicineId);
        } catch {
          // Bỏ qua lỗi mạng tạm thời
        }
      }

      if (target) {
        showToast({
          variant: 'info',
          title: 'Đã xóa khỏi giỏ hàng',
          description: target.medicineName,
        });
      }
    },
    [isCustomer, items, persistLocal, showToast],
  );

  const clearCart = useCallback(async (): Promise<void> => {
    persistLocal([]);
    if (isCustomer) {
      try {
        await orderApi.clearCart();
      } catch {
        // Bỏ qua nếu backend đã tự xóa giỏ sau checkout
      }
    }
  }, [isCustomer, persistLocal]);

  /**
   * Đảm bảo Redis cart trên order-service (/api/v1/cart) khớp 100% với danh sách hiện tại
   * trước khi gọi POST /api/v1/orders/checkout.
   */
  const syncCartToBackend = useCallback(async (): Promise<void> => {
    if (!isCustomer) return;
    await orderApi.clearCart();
    for (const item of items) {
      await orderApi.addCartItem({
        medicineId: item.medicineId,
        quantity: item.quantity,
      });
    }
  }, [isCustomer, items]);

  const totalItems = useMemo(
    () => items.reduce((sum, item) => sum + item.quantity, 0),
    [items],
  );

  const totalAmount = useMemo(
    () => items.reduce((sum, item) => sum + item.price * item.quantity, 0),
    [items],
  );

  const hasEtcItems = useMemo(
    () => items.some((item) => item.prescriptionType === 'ETC'),
    [items],
  );

  const value = useMemo<CartContextValue>(
    () => ({
      items,
      totalItems,
      totalAmount,
      hasEtcItems,
      isSyncing,
      getItemQuantity,
      addItem,
      updateQuantity,
      removeItem,
      clearCart,
      syncCartToBackend,
    }),
    [
      items,
      totalItems,
      totalAmount,
      hasEtcItems,
      isSyncing,
      getItemQuantity,
      addItem,
      updateQuantity,
      removeItem,
      clearCart,
      syncCartToBackend,
    ],
  );

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}
