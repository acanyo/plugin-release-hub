import { fileURLToPath, URL } from 'node:url'

import { viteConfig } from '@halo-dev/ui-plugin-bundler-kit/vite'
import { svelte } from '@sveltejs/vite-plugin-svelte'

// For more info,
// please see https://github.com/halo-dev/halo/tree/main/ui/packages/ui-plugin-bundler-kit
export default viteConfig({
  vite: {
    plugins: [svelte()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
  },
})
