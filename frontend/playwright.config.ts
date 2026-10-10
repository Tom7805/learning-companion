import { readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { defineConfig, devices } from '@playwright/test'

// Đổi được cổng để chạy song song với máy chủ đang dùng để phát triển, ví dụ E2E_BASE_URL=http://localhost:5174.
const FRONTEND_URL = process.env.E2E_BASE_URL ?? 'http://localhost:5173'
const API_URL = process.env.E2E_API_URL ?? 'http://127.0.0.1:8080'
const BACKEND_HEALTH = `${API_URL}/actuator/health`

// Bộ khung có sẵn các tệp kiểm thử rỗng cho story sau, chỉ chạy tệp đã có nội dung.
const specs = readdirSync('e2e')
  .filter((file) => file.endsWith('.spec.ts') && statSync(join('e2e', file)).size > 0)

/**
 * Kiểm thử đầu cuối chạy trên máy chủ thật (hồ sơ dev: thư mô phỏng, đồng hồ tua được) và PostgreSQL
 * từ docker compose. Đồng hồ máy chủ là trạng thái dùng chung nên chạy tuần tự một luồng.
 */
export default defineConfig({
  testDir: 'e2e',
  testMatch: specs,
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    // Đường dẫn trong thư trỏ về localhost, dùng cùng tên miền để cookie phiên khớp.
    baseURL: FRONTEND_URL,
    locale: 'vi-VN',
    timezoneId: 'Asia/Ho_Chi_Minh',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'desktop-chrome', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } } },
    { name: 'mobile-chrome', use: { ...devices['Pixel 7'] } },
  ],
  webServer: [
    {
      command: process.platform === 'win32' ? '.\\mvnw.cmd -q spring-boot:run' : './mvnw -q spring-boot:run',
      cwd: '../backend',
      url: BACKEND_HEALTH,
      reuseExistingServer: true,
      timeout: 180_000,
    },
    {
      command: 'npm run dev',
      url: FRONTEND_URL,
      reuseExistingServer: true,
      timeout: 60_000,
    },
  ],
})
