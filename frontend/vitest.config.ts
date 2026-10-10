import { readdirSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'
import { defineConfig, mergeConfig } from 'vitest/config'
import viteConfig from './vite.config.ts'

/**
 * Bộ khung dự án tạo sẵn các tệp kiểm thử rỗng cho story sau; Vitest báo lỗi với tệp không có bài kiểm thử.
 * Chỉ chạy những tệp đã có nội dung, tệp mới tự được đưa vào ngay khi có nội dung.
 */
function nonEmptyTestFiles(dir: string): string[] {
  return readdirSync(dir, { recursive: true, encoding: 'utf8' })
    .filter((file) => /\.test\.tsx?$/.test(file))
    .map((file) => join(dir, file))
    .filter((file) => statSync(file).size > 0)
    .map((file) => relative(process.cwd(), file).replaceAll('\\', '/'))
}

export default defineConfig((env) =>
  mergeConfig(viteConfig(env), {
    test: {
      globals: true,
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      include: nonEmptyTestFiles('src'),
      css: false,
      restoreMocks: true,
    },
  }),
)
