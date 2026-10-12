import AxeBuilder from '@axe-core/playwright'
import type { Page } from '@playwright/test'
import { createVerifiedAccount, expect, loginThroughUi, test } from './fixtures/auth'
import { uniqueEmail } from './fixtures/testData'

/**
 * NCL-01-CN-006 — Điều khoản, chính sách quyền riêng tư và đồng ý theo mục đích (Jira LC-23).
 * Trang Quyền riêng tư mở từ hộp thoại Cài đặt.
 */

/** Phiên bản thử có hiệu lực sau mười năm rồi tua đồng hồ tới, nên dữ liệu thật trên máy không bị ảnh hưởng. */
const TEN_YEARS = 3650 * 86_400

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

async function openPrivacy(page: Page) {
  await page.getByRole('button', { name: /Trình đơn tài khoản/ }).first().click()
  await page.getByRole('menuitem', { name: 'Cài đặt' }).click()
  await page.getByRole('dialog', { name: 'Cài đặt' }).getByRole('link', { name: 'Quyền riêng tư' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Quyền riêng tư' })).toBeVisible()
}

test.describe('NCL-01-CN-006 · Đồng ý theo mục đích', () => {
  test('TC-01: bật thống kê ẩn danh thì đồng ý được ghi kèm phiên bản chính sách, thống kê bắt đầu từ lần thao tác kế tiếp', async ({
    page,
    dev,
  }) => {
    await createVerifiedAccount(page, dev, uniqueEmail('thong-ke'))
    const events: boolean[] = []
    page.on('response', async (response) => {
      if (response.url().endsWith('/api/v1/analytics/events')) events.push((await response.json()).recorded)
    })

    await openPrivacy(page)
    const analytics = page.getByRole('switch', { name: 'Thống kê sử dụng ẩn danh' })
    await expect(analytics).toHaveAttribute('aria-checked', 'false')
    await expect(page.getByRole('switch', { name: 'Thư giới thiệu tính năng mới' })).toHaveAttribute('aria-checked', 'false')
    await expectNoSeriousA11yViolations(page)
    expect(events).toEqual([])

    await analytics.click()
    await expect(analytics).toHaveAttribute('aria-checked', 'true')
    await expect(page.getByTestId('purpose-ANONYMOUS_USAGE_ANALYTICS').getByTestId('purpose-meta')).toContainText(
      'chính sách 2026-10-01',
    )
    await expect(page.getByTestId('history-entry').first()).toContainText('Đồng ý thống kê sử dụng ẩn danh')
    await expect(page.getByTestId('history-entry').first()).toContainText('phiên bản 2026-10-01')
    expect(events).toEqual([])

    // Thao tác kế tiếp: chuyển sang trang khác thì sự kiện ẩn danh đầu tiên được ghi.
    await page.getByRole('link', { name: 'Thư viện' }).first().click()
    await expect.poll(() => events).toEqual([true])
  })

  test('TC-02: tắt thư giới thiệu tính năng mới thì ngừng gửi ngay và ghi lần rút lại vào lịch sử', async ({
    page,
    dev,
  }) => {
    const email = uniqueEmail('ban-tin')
    await createVerifiedAccount(page, dev, email)
    await openPrivacy(page)
    const news = page.getByRole('switch', { name: 'Thư giới thiệu tính năng mới' })

    await news.click()
    await expect(news).toHaveAttribute('aria-checked', 'true')
    await dev.sendProductNews('Sổ tay học tập mới')
    const mail = await dev.waitForMail(email, 'PRODUCT_NEWS')
    expect(mail.subject).toBe('Có gì mới ở Learning Companion')

    await news.click()
    await expect(news).toHaveAttribute('aria-checked', 'false')
    await dev.sendProductNews('Thẻ ghi nhớ mới')
    await page.waitForTimeout(800)
    expect((await dev.mails(email)).filter((item) => item.template === 'PRODUCT_NEWS')).toHaveLength(1)

    await expect(page.getByTestId('history-entry').first()).toContainText('Rút lại đồng ý thư giới thiệu tính năng mới')
    await expect(page.getByTestId('history-entry').nth(1)).toContainText('Đồng ý thư giới thiệu tính năng mới')
  })

  test('TC-03: điều khoản vừa lên phiên bản mới thì khi đăng nhập phải đồng ý trước khi dùng tiếp', async ({
    page,
    dev,
  }) => {
    const email = uniqueEmail('dieu-khoan')
    await createVerifiedAccount(page, dev, email)
    await dev.sampleDocument(email, 'Ghi chú vẫn còn nguyên')
    const version = `e2e-${Date.now().toString(36)}`
    await dev.publishTerms(version, ['Thêm điều khoản về trợ lý học tập.', 'Làm rõ quyền xóa dữ liệu.'], TEN_YEARS)
    await dev.advanceClock(TEN_YEARS + 1)

    await loginThroughUi(page, email)

    const dialog = page.getByRole('dialog', { name: 'Điều khoản đã được cập nhật' })
    await expect(dialog).toBeVisible()
    await expect(dialog.getByTestId('terms-update-terms')).toContainText(`Phiên bản ${version}`)
    await expect(dialog.getByTestId('terms-update-terms')).toContainText('Thêm điều khoản về trợ lý học tập.')
    await expect(page.getByTestId('terms-pending')).toBeVisible()
    await expectNoSeriousA11yViolations(page)
    // Chưa đồng ý thì không dùng được chức năng nào, dù mở thẳng đường dẫn.
    expect((await page.request.get('/api/v1/library/documents')).status()).toBe(403)
    await page.keyboard.press('Escape')
    await expect(dialog).toBeVisible()

    await dialog.getByRole('button', { name: 'Đồng ý và tiếp tục' }).click()
    await expect(dialog).toBeHidden()
    await expect(page.getByTestId('home-greeting')).toBeVisible()
    await page.goto('/library')
    await expect(page.getByTestId('document-card')).toContainText('Ghi chú vẫn còn nguyên')
    await openPrivacy(page)
    await expect(page.getByTestId('required-terms')).toContainText(`Bạn đã đồng ý phiên bản ${version}`)
  })

  test('TC-04: người học khác biết mã tài khoản gửi yêu cầu đổi đồng ý thì nhận không tìm thấy', async ({
    page,
    browser,
    dev,
  }) => {
    await createVerifiedAccount(page, dev, uniqueEmail('chu-tai-khoan'))
    const ownerId = (await (await page.request.get('/api/v1/auth/session')).json()).account.id as string

    const stranger = await (await browser.newContext()).newPage()
    await createVerifiedAccount(stranger, dev, uniqueEmail('nguoi-la'))
    const xsrf = (await stranger.context().cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')?.value ?? ''
    const response = await stranger.request.put(`/api/v1/accounts/${ownerId}/consents/PRODUCT_NEWS_EMAIL`, {
      data: { granted: true },
      headers: { 'X-XSRF-TOKEN': xsrf },
    })
    expect(response.status()).toBe(404)
    expect((await response.json()).code).toBe('NOT_FOUND')

    await openPrivacy(page)
    await expect(page.getByRole('switch', { name: 'Thư giới thiệu tính năng mới' })).toHaveAttribute('aria-checked', 'false')
  })

  test('Trang điều khoản công khai nêu các điểm chính bằng lời dễ hiểu', async ({ page }) => {
    await page.goto('/legal/privacy')
    await expect(page.getByTestId('legal-highlights')).toContainText('chỉ bạn mở được')
    await expectNoSeriousA11yViolations(page)
  })
})
