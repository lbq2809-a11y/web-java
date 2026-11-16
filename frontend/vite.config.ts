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
      // Cấu hình tạm thời trong lúc api-gateway (8080) chưa mở route cho consultation-service (8088)
      '/api/v1/consultations': {
        target: 'http://localhost:8088',
        changeOrigin: true,
      },
      // Các API khác (auth, medicine, ...) đi qua api-gateway
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
