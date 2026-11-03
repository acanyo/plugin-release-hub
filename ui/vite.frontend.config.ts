import { defineConfig } from 'vite'
import { svelte } from '@sveltejs/vite-plugin-svelte'
import { fileURLToPath } from 'node:url'

export default defineConfig({
  plugins: [svelte()],
  base: '/plugins/release-hub/assets/frontend/',
  server: { host: '127.0.0.1', port: 5179, proxy: { '/apis': 'http://localhost:8098' } },
  build: {
    outDir: 'build/frontend', emptyOutDir: true, cssCodeSplit: false,
    lib: { entry: fileURLToPath(new URL('./src/frontend/main.ts', import.meta.url)), formats: ['es'], fileName: () => 'release-hub.js', cssFileName: 'release-hub' },
  },
})
