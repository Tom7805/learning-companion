import AxeBuilder from '@axe-core/playwright'
import type { Page } from '@playwright/test'
import { createVerifiedAccount, expect, test } from './fixtures/auth'
import { uniqueEmail } from './fixtures/testData'

/**
 * NCL-02-CN-001 (Jira LC-18) — cài ứng dụng và mở lại khi mạng chập chờn. Chạy trên bản dựng thật (vite preview) vì
 * service worker chỉ được đăng ký ở bản dựng thật.
 */

interface Manifest {
  name: string
  short_name: string
  start_url: string
  scope: string
  display: string
  background_color: string
  theme_color: string
  icons: { src: string; sizes: string; type: string; purpose: string }[]
}

/** Kích thước thật của ảnh PNG đọc từ phần đầu tệp. */
function pngSize(buffer: Buffer) {
  expect(buffer.subarray(1, 4).toString('ascii')).toBe('PNG')
  return `${buffer.readUInt32BE(16)}x${buffer.readUInt32BE(20)}`
}

async function controlledByServiceWorker(page: Page) {
  await page.evaluate(() => navigator.serviceWorker.ready.then(() => undefined))
  if (!(await page.evaluate(() => Boolean(navigator.serviceWorker.controller)))) await page.reload()
  await expect.poll(() => page.evaluate(() => Boolean(navigator.serviceWorker.controller))).toBe(true)
}

test.describe('NCL-02-CN-001 · Cài ứng dụng và mở lại nhanh', () => {
  test('TC-04: cài được lên máy với tên, biểu tượng, cửa sổ riêng và màn hình chờ', async ({ page }) => {
    await page.goto('/login')
    await expect(page.locator('link[rel="apple-touch-icon"]')).toHaveAttribute('href', '/icons/apple-touch-icon.png')
    const href = await page.locator('link[rel="manifest"]').getAttribute('href')
    const manifest = (await (await page.request.get(href!)).json()) as Manifest

    // Cửa sổ riêng (standalone) và màn hình chờ dựng từ tên, màu nền và biểu tượng 512 px.
    expect(manifest).toMatchObject({
      name: 'Learning Companion',
      short_name: 'Learning',
      start_url: '/',
      scope: '/',
      display: 'standalone',
      background_color: '#2a2a2c',
      theme_color: '#2a2a2c',
    })
    const icons = manifest.icons.map((icon) => `${icon.sizes} ${icon.purpose}`)
    expect(icons).toEqual(['192x192 any', '512x512 any', '512x512 maskable'])
    for (const icon of [...manifest.icons, { src: '/icons/apple-touch-icon.png', sizes: '180x180', type: 'image/png' }]) {
      const response = await page.request.get(icon.src)
      expect(response.ok()).toBe(true)
      expect(response.headers()['content-type']).toBe('image/png')
      expect(pngSize(await response.body())).toBe(icon.sizes)
    }

    // Chrome tự kiểm: trang đủ điều kiện cài như ứng dụng (manifest hợp lệ, service worker, HTTPS hoặc localhost).
    await controlledByServiceWorker(page)
    const cdp = await page.context().newCDPSession(page)
    const { errors } = await cdp.send('Page.getAppManifest')
    expect(errors).toEqual([])
    await expect
      .poll(async () => (await cdp.send('Page.getInstallabilityErrors')).installabilityErrors.map((error) => error.errorId))
      .toEqual([])
  })

  test('TC-05: mở lại khi mạng chập chờn thì khung hiện ngay từ bộ đệm kèm khung xương và thông báo đang kết nối lại', async ({
    page,
    context,
    dev,
  }) => {
    await createVerifiedAccount(page, dev, uniqueEmail('mang-yeu'))
    await page.goto('/')
    await expect(page.getByTestId('home-greeting')).toBeVisible()
    await controlledByServiceWorker(page)
    await expect(page.getByTestId('home-greeting')).toBeVisible()

    await context.setOffline(true)
    const started = Date.now()
    await page.reload()
    await expect(page.getByTestId('app-shell-skeleton')).toBeVisible()
    await expect(page.getByTestId('reconnecting')).toHaveText('Đang kết nối lại…')
    // Khung lấy từ bộ đệm nên hiện ngay, không chờ mạng.
    expect(Date.now() - started).toBeLessThan(5_000)
    await page.emulateMedia({ reducedMotion: 'reduce' })
    const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze()
    expect(results.violations.filter((violation) => ['serious', 'critical'].includes(violation.impact ?? ''))).toEqual([])

    // Có mạng trở lại thì tự vào thẳng ứng dụng, không cần tải lại.
    await context.setOffline(false)
    await expect(page.getByTestId('home-greeting')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('reconnecting')).toBeHidden()
  })

  test('mở thẳng một trang con khi mất mạng vẫn có khung, trang đã tải trước đó mở được từ bộ đệm', async ({
    page,
    context,
    dev,
  }) => {
    await createVerifiedAccount(page, dev, uniqueEmail('trang-con'))
    await page.goto('/library')
    await expect(page.getByRole('heading', { level: 1, name: 'Thư viện của tôi' })).toBeVisible()
    await controlledByServiceWorker(page)

    await context.setOffline(true)
    await page.goto('/library')
    await expect(page.getByTestId('reconnecting')).toBeVisible()
    await context.setOffline(false)
    await expect(page.getByRole('heading', { level: 1, name: 'Thư viện của tôi' })).toBeVisible({ timeout: 15_000 })

    // API không bao giờ nằm trong bộ đệm của service worker: dữ liệu học tập chỉ đi thẳng tới máy chủ.
    const cached = await page.evaluate(async () => {
      const urls: string[] = []
      for (const key of await caches.keys()) {
        for (const request of await (await caches.open(key)).keys()) urls.push(new URL(request.url).pathname)
      }
      return urls
    })
    expect(cached).toContain('/')
    expect(cached.some((url) => url.startsWith('/assets/'))).toBe(true)
    expect(cached.filter((url) => url.startsWith('/api/'))).toEqual([])
  })
})
