import { fileURLToPath, URL } from 'node:url'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
    },
    server: {
      port: 5173,
      strictPort: true,
      // Cùng nguồn với API để cookie phiên và cookie CSRF hoạt động không cần CORS.
      proxy: {
        '/api': { target: env.VITE_API_PROXY_TARGET ?? 'http://127.0.0.1:8080', changeOrigin: false },
      },
    },
  }
})
