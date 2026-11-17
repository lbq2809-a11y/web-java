import { Route, Routes } from 'react-router-dom';
import { ProtectedRoute } from '@/components/auth/ProtectedRoute';
import { RoleRoute } from '@/components/auth/RoleRoute';
import { HomePage } from '@/pages/HomePage';
import { LoginPage } from '@/pages/LoginPage';
import { QrConfirmPage } from '@/pages/QrConfirmPage';
import { RegisterPage } from '@/pages/RegisterPage';
import { ProfilePage } from '@/pages/ProfilePage';
import { MedicinesPage } from '@/pages/MedicinesPage';
import { MedicineDetailPage } from '@/pages/MedicineDetailPage';
import { ConsultationPage } from '@/pages/ConsultationPage';
import { CartPage } from '@/pages/CartPage';
import { CheckoutPage } from '@/pages/CheckoutPage';
import { OrdersPage } from '@/pages/OrdersPage';
import { OrderDetailPage } from '@/pages/OrderDetailPage';
import { PrescriptionsPage } from '@/pages/PrescriptionsPage';
import { AdminDashboardPage } from '@/pages/AdminDashboardPage';
import { NotFoundPage } from '@/pages/NotFoundPage';

export function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/qr-confirm" element={<QrConfirmPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/medicines" element={<MedicinesPage />} />
      <Route path="/medicines/:id" element={<MedicineDetailPage />} />
      <Route path="/cart" element={<CartPage />} />

      <Route
        path="/checkout"
        element={
          <ProtectedRoute>
            <CheckoutPage />
          </ProtectedRoute>
        }
      />

      <Route
        path="/orders"
        element={
          <ProtectedRoute>
            <OrdersPage />
          </ProtectedRoute>
        }
      />

      <Route
        path="/orders/:id"
        element={
          <ProtectedRoute>
            <OrderDetailPage />
          </ProtectedRoute>
        }
      />

      <Route
        path="/prescriptions"
        element={
          <RoleRoute allowedRoles={['KHACH_HANG']} redirectTo="/admin" silentRedirect>
            <PrescriptionsPage />
          </RoleRoute>
        }
      />

      <Route
        path="/admin"
        element={
          <RoleRoute allowedRoles={['DUOC_SI', 'ADMIN']}>
            <AdminDashboardPage />
          </RoleRoute>
        }
      />

      <Route
        path="/profile"
        element={
          <ProtectedRoute>
            <ProfilePage />
          </ProtectedRoute>
        }
      />

      <Route
        path="/consultation"
        element={
          <ProtectedRoute>
            <ConsultationPage />
          </ProtectedRoute>
        }
      />

      <Route
        path="/consultation/:conversationId"
        element={
          <ProtectedRoute>
            <ConsultationPage />
          </ProtectedRoute>
        }
      />

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}

export default App;
