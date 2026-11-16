import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}

export function formatCurrencyVnd(amount: number): string {
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(amount);
}

export function formatDateTimeVi(isoString: string): string {
  const date = new Date(isoString);
  if (Number.isNaN(date.getTime())) {
    return isoString;
  }
  return new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(date);
}

export function formatTimeVi(isoString: string): string {
  const date = new Date(isoString);
  if (Number.isNaN(date.getTime())) {
    return '';
  }
  return new Intl.DateTimeFormat('vi-VN', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

export function translateRole(role: string): string {
  switch (role) {
    case 'ADMIN':
      return 'Quản trị viên';
    case 'DUOC_SI':
      return 'Dược sĩ';
    case 'KHACH_HANG':
      return 'Khách hàng';
    default:
      return role;
  }
}

export function translateKnowledgeSection(section: string): string {
  switch (section) {
    case 'indications':
      return 'Chỉ định';
    case 'contraindications':
      return 'Chống chỉ định';
    case 'dosage':
      return 'Liều dùng & Cách dùng';
    case 'activeIngredient':
      return 'Hoạt chất';
    case 'overview':
      return 'Tổng quan';
    default:
      return section;
  }
}
