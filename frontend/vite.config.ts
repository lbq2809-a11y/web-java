import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': '/src',
    },
  },
  server: {
    port: 5173,
    proxy: {
      // Cấu hình tạm thời trong lúc api-gateway (8080) chưa mở route cho inventory-service (8087)
      '/api/v1/stocks': {
        target: 'http://localhost:8087',
        changeOrigin: true,
      },
      // Cấu hình tạm thời trong lúc api-gateway (8080) chưa mở route cho consultation-service (8088)
      '/api/v1/consultations': {
        target: 'http://localhost:8088',
        changeOrigin: true,
      },
      // Cấu hình tạm thời trong lúc api-gateway (8080) chưa mở route cho recommendation-service (8089)
      '/api/v1/recommendations': {
        target: 'http://localhost:8089',
        changeOrigin: true,
      },
      // Các API còn lại (auth, medicine, prescription, cart, orders, ...) đi qua api-gateway
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
