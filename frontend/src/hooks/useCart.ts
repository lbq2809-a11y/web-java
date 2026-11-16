import { useContext } from 'react';
import { CartContext, type CartContextValue } from '@/contexts/CartContext';

export function useCart(): CartContextValue {
  const ctx = useContext(CartContext);
  if (!ctx) {
    throw new Error('useCart phải được sử dụng bên trong CartProvider');
  }
  return ctx;
}
