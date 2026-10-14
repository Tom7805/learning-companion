import AxeBuilder from '@axe-core/playwright'
import type { Browser, Page } from '@playwright/test'
import { createVerifiedAccount, expect, loginThroughUi, test, type DevApi } from './fixtures/auth'
import { openSidebar } from './fixtures/shell'
import { uniqueEmail } from './fixtures/testData'

/**
 * NCL-02-CN-002 — Trung tâm thông báo trong ứng dụng (Jira LC-24). Thông báo đến từ sự kiện thật: đăng nhập từ
 * thiết bị mới và gói dữ liệu đã sẵn sàng. Chống gửi trùng (TC-02) và nhật ký từ chối (TC-04) được kiểm kỹ ở
 * NotificationIT phía máy chủ.
 */

async function expectNoSeriousA11yViolations(page: Page) {
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.waitForTimeout(50)
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .exclude('a[href="/dev/mailbox"]')
    .analyze()
  const serious = results.violations.filter((violation) => ['serious', 'critical'].includes(violation.impact ?? ''))
  expect(
    serious.map((violation) => `${violation.id}: ${violation.nodes.map((node) => node.target.join(' ')).join(', ')}`),
  ).toEqual([])
}

async function signUp(page: Page, dev: DevApi, tag: string) {
  const email = uniqueEmail(tag)
  await createVerifiedAccount(page, dev, email)
  await page.goto('/')
  await expect(page.getByTestId('home-greeting')).toBeVisible()
  return email
}

/** Yêu cầu xuất dữ liệu qua API của chính phiên đang mở và chờ gói sẵn sàng; trả về mã gói. */
async function exportReady(page: Page) {
  const xsrf = (await page.context().cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')?.value ?? ''
  const response = await page.request.post('/api/v1/exports', { headers: { 'X-XSRF-TOKEN': xsrf } })
  expect(response.status()).toBe(202)
  const id = (await response.json()).latest.id as string
  await expect
    .poll(async () => (await (await page.request.get(`/api/v1/exports/${id}`)).json()).status, { timeout: 30_000 })
    .toBe('READY')
  return id
}

/** Đăng nhập cùng tài khoản trên một trình duyệt khác: chủ tài khoản nhận cảnh báo thiết bị mới. */
async function signInElsewhere(browser: Browser, email: string) {
  const laptop = await (await browser.newContext()).newPage()
  await loginThroughUi(laptop, email)
  await expect(laptop.getByTestId('home-greeting')).toBeVisible()
  await laptop.context().close()
}

const bell = (page: Page) => page.getByTestId('notification-bell').first()

test.describe('NCL-02-CN-002 · Trung tâm thông báo', () => {
  test('TC-01: hai thông báo chưa đọc hiện ngay không cần tải lại; mở thông báo thứ nhất thì tới đúng gói dữ liệu, còn một chưa đọc', async ({
    page,
    browser,
    dev,
  }) => {
    const email = await signUp(page, dev, 'thong-bao')
    await expect(bell(page)).toHaveAccessibleName('Thông báo')

    // Hai sự kiện xảy ra khi trang đang mở: số chưa đọc cập nhật ngay qua kênh trực tiếp.
    await signInElsewhere(browser, email)
    await expect(bell(page)).toHaveAccessibleName('Thông báo, 1 chưa đọc')
    const exportId = await exportReady(page)
    await expect(bell(page)).toHaveAccessibleName('Thông báo, 2 chưa đọc')
    await expect(page.getByTestId('notification-badge').first()).toHaveText('2')

    await bell(page).click()
    const panel = page.getByTestId('side-panel')
    await expect(panel).toBeVisible()
    await expect(panel.getByTestId('notification-summary')).toHaveText('2 thông báo chưa đọc')
    const items = panel.getByTestId('notification-item')
    await expect(items).toHaveCount(2)
    await expect(items.nth(0)).toContainText('Gói dữ liệu của bạn đã sẵn sàng')
    await expect(items.nth(1)).toContainText('Đăng nhập từ thiết bị mới')
    await expectNoSeriousA11yViolations(page)

    await items.nth(0).click()
    await expect(page).toHaveURL(new RegExp(`/exports/${exportId}$`))
    await expect(page.getByRole('heading', { level: 1, name: 'Tải gói dữ liệu của bạn' })).toBeVisible()
    await expect(bell(page)).toHaveAccessibleName('Thông báo, 1 chưa đọc')

    // Cảnh báo thiết bị mới mở trang thiết bị; đã đọc hết thì không còn huy hiệu.
    if (!(await panel.isVisible())) await bell(page).click()
    await panel.getByTestId('notification-item').nth(1).click()
    await expect(page).toHaveURL(/\/devices$/)
    await expect(bell(page)).toHaveAccessibleName('Thông báo')
    await expect(page.getByTestId('notification-badge')).toHaveCount(0)
  })

  test('TC-03: thông báo trỏ tới gói dữ liệu đã bị thay thì mở trang nội dung không còn thay vì báo lỗi', async ({
    page,
    dev,
  }) => {
    test.setTimeout(120_000)
    const email = await signUp(page, dev, 'da-xoa')
    await exportReady(page)

    // Hôm sau xuất gói mới: gói cũ bị thay và xóa khỏi kho.
    await dev.advanceClock(24 * 3600 + 60)
    await loginThroughUi(page, email)
    await expect(page.getByTestId('home-greeting')).toBeVisible()
    await exportReady(page)

    await bell(page).click()
    const items = page.getByTestId('side-panel').getByTestId('notification-item')
    await expect(items).toHaveCount(2)
    await expect(items.nth(1)).toContainText('Gói dữ liệu của bạn đã sẵn sàng')
    await items.nth(1).click()

    await expect(page).toHaveURL(/\/gone$/)
    const gone = page.getByTestId('content-gone')
    await expect(gone.getByRole('heading', { level: 1 })).toHaveText('Nội dung này không còn nữa')
    await expectNoSeriousA11yViolations(page)
    if (await page.getByTestId('side-panel').isVisible()) await page.keyboard.press('Escape')
    await gone.getByRole('button', { name: 'Xem thông báo' }).click()
    await expect(page.getByTestId('side-panel').getByTestId('notification-item')).toHaveCount(2)
  })

  test('TC-04: người học khác mở thông báo bằng đường dẫn trực tiếp thì nhận không tìm thấy', async ({
    page,
    browser,
    dev,
  }) => {
    const email = await signUp(page, dev, 'chu')
    await dev.sampleNotifications(email, 1)
    const mine = (await (await page.request.get('/api/v1/notifications')).json()).items[0].id as string

    const stranger = await (await browser.newContext()).newPage()
    await createVerifiedAccount(stranger, dev, uniqueEmail('nguoi-la'))
    await stranger.goto(`/notifications/${mine}`)

    const notFound = stranger.getByTestId('notification-not-found')
    await expect(notFound.getByRole('heading', { level: 1 })).toHaveText('Không tìm thấy thông báo')
    await expectNoSeriousA11yViolations(stranger)
    await stranger.context().close()

    // Chủ thông báo mở bằng đường dẫn trực tiếp thì đi thẳng tới đối tượng (ở đây đã không còn).
    await page.goto(`/notifications/${mine}`)
    await expect(page).toHaveURL(/\/gone$/)
    await expect(bell(page)).toHaveAccessibleName('Thông báo')
  })

  test('nhiều thông báo: xem thêm trang cũ hơn và đánh dấu tất cả đã đọc', async ({ page, dev }) => {
    const email = await signUp(page, dev, 'nhieu')
    expect(await dev.sampleNotifications(email, 25)).toBe(25)
    await page.reload()
    await expect(bell(page)).toHaveAccessibleName('Thông báo, 25 chưa đọc')

    await bell(page).click()
    const panel = page.getByTestId('side-panel')
    await expect(panel.getByTestId('notification-item')).toHaveCount(20)
    await panel.getByRole('button', { name: 'Xem thông báo cũ hơn' }).click()
    await expect(panel.getByTestId('notification-item')).toHaveCount(25)
    await expect(panel.getByRole('button', { name: 'Xem thông báo cũ hơn' })).toHaveCount(0)

    await panel.getByRole('button', { name: 'Đánh dấu tất cả đã đọc' }).click()
    await expect(panel.getByTestId('notification-summary')).toHaveText('Bạn đã đọc hết thông báo.')
    await expect(panel.locator('[data-testid="notification-item"][data-unread]')).toHaveCount(0)
    await expect(bell(page)).toHaveAccessibleName('Thông báo')
    await expectNoSeriousA11yViolations(page)

    await page.reload()
    await expect(bell(page)).toHaveAccessibleName('Thông báo')
  })

  test('chưa có thông báo thì ngăn nói rõ sẽ có gì ở đây', async ({ page, dev }) => {
    await signUp(page, dev, 'trong')
    const sidebar = await openSidebar(page)
    await sidebar.getByTestId('notification-bell').click()

    const panel = page.getByTestId('side-panel')
    await expect(panel).toContainText('Chưa có thông báo nào')
    await expect(panel.getByRole('button', { name: 'Đánh dấu tất cả đã đọc' })).toHaveCount(0)
    expect(await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)).toBe(
      false,
    )
    await expectNoSeriousA11yViolations(page)
  })
})
