import { createHash } from 'node:crypto'
import { readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { fileURLToPath, URL } from 'node:url'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv, type Plugin } from 'vite'

/**
 * Điền vào dist/sw.js danh sách tệp cần lưu đệm khi cài và phiên bản bản dựng (NCL-02-CN-001). Phiên bản đổi mỗi khi
 * một tệp đổi nội dung, nên service worker mới thay bộ đệm cũ ngay lần mở sau.
 */
function serviceWorkerPrecache(): Plugin {
  let outDir = 'dist'
  return {
    name: 'learning-companion:service-worker-precache',
    apply: 'build',
    configResolved(config) {
      outDir = resolve(config.root, config.build.outDir)
    },
    closeBundle() {
      const files = readdirSync(outDir, { recursive: true, encoding: 'utf8' })
        .map((file) => file.replaceAll('\\', '/'))
        .filter((file) => /^(assets|icons)\/[^.]/.test(file) || ['favicon.svg', 'manifest.webmanifest'].includes(file))
        .filter((file) => statSync(join(outDir, file)).isFile())
        .sort()
      const hash = createHash('sha256')
      for (const file of ['index.html', ...files]) hash.update(file).update(readFileSync(join(outDir, file)))
      const worker = join(outDir, 'sw.js')
      const source = readFileSync(worker, 'utf8')
        .replace("const BUILD = 'dev'", `const BUILD = '${hash.digest('hex').slice(0, 12)}'`)
        .replace('const PRECACHE = []', `const PRECACHE = ${JSON.stringify(files.map((file) => `/${file}`))}`)
      writeFileSync(worker, source)
    },
  }
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  // Cùng nguồn với API để cookie phiên và cookie CSRF hoạt động không cần CORS.
  const proxy = {
    '/api': { target: env.VITE_API_PROXY_TARGET ?? 'http://127.0.0.1:8080', changeOrigin: false },
  }
  return {
    plugins: [react(), tailwindcss(), serviceWorkerPrecache()],
    resolve: {
      alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
    },
    server: { port: 5173, strictPort: true, proxy },
    // Bản dựng thật chạy thử tại máy (kiểm thử cài ứng dụng và service worker trong e2e/pwa.spec.ts).
    preview: { port: 4173, strictPort: true, proxy },
  }
})
