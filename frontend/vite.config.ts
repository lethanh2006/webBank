import { fileURLToPath, URL } from 'node:url'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

const backendUrl = process.env.BACKEND_URL ?? 'http://localhost:8081'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    // Same-origin in dev: the browser only talks to Vite, so no CORS setup is needed.
    proxy: {
      '/api': { target: backendUrl, changeOrigin: true },
    },
  },
})
