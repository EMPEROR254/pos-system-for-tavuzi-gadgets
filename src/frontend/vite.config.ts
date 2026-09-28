/// <reference types="vitest/config" />
// This app lives inside the Spring Boot project (src/frontend). The build goes
// into ../main/resources/static so Spring serves it; in development /api calls
// go to Spring Boot on port 8080.
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  build: { outDir: '../main/resources/static', emptyOutDir: true },
  server: { proxy: { '/api': 'http://localhost:8080' } },
  test: { environment: 'jsdom' },
})
