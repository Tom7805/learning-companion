import AxeBuilder from '@axe-core/playwright'
import type { Browser, Page } from '@playwright/test'
import { becomeAdmin, completeEnrollment, currentCode, wrongCode } from './fixtures/admin'
import { createVerifiedAccount, expect, loginThroughUi, test, type DevApi } from './fixtures/auth'
import { openSidebar } from './fixtures/shell'
import { uniqueEmail } from './fixtures/testData'

/**
 * NCL-03-CN-002 — Tài khoản quản trị viên duy nhất có xác thực hai bước (Jira LC-20). Câu lệnh cài đặt trên máy chủ,
 * chỉ mục duy nhất trong cơ sở dữ liệu và hạn tám giờ của phiên được kiểm kỹ ở AdminTwoFactorIT phía máy chủ.
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

/** Quản trị viên mới, đăng nhập lần đầu đủ hai bước trên `page`. */
async function freshAdmin(page: Page, dev: DevApi) {
  const email = uniqueEmail('quan-tri')
  await createVerifiedAccount(page, dev, email)
  return becomeAdmin(page, dev, email)
}

/** Đăng nhập lại trên một trình duyệt mới tới bước nhập mã. */
async function reachCodeStep(browser: Browser, email: string) {
  const page = await (await browser.newContext()).newPage()
  await loginThroughUi(page, email)
  await expect(page).toHaveURL(/\/login\/two-factor$/)
  await expect(page.getByRole('heading', { level: 1, name: 'Nhập mã xác thực' })).toBeVisible()
  return page
}

async function enterCode(page: Page, code: string) {
  await page.getByLabel('Mã sáu chữ số').fill(code)
  await page.getByRole('button', { name: /Xác thực và vào trang quản trị/ }).click()
}

async function searchAudit(page: Page, filters: { account?: string; category?: string; action?: string }) {
  await page.goto('/admin/audit')
  const form = page.getByRole('search', { name: 'Lọc nhật ký' })
  await form.getByLabel('Tài khoản', { exact: true }).fill(filters.account ?? '')
  await form.getByLabel('Nhóm thao tác').selectOption(filters.category ?? '')
  await form.getByLabel('Loại thao tác').selectOption(filters.action ?? '')
  await form.getByRole('button', { name: 'Tra cứu' }).click()
}

test.describe('NCL-03-CN-002 · Quản trị viên duy nhất có xác thực hai bước', () => {
  test('TC-01: lần đầu buộc thiết lập ứng dụng sinh mã; lần sau mật khẩu kèm mã sáu chữ số mở trang quản trị tám giờ', async ({
    page,
    browser,
    dev,
  }) => {
    const email = uniqueEmail('quan-tri')
    await createVerifiedAccount(page, dev, email)
    expect((await dev.setRole(email, 'ADMIN')).ok()).toBeTruthy()

    // Phiên người học đang mở không mang quyền quản trị: phải đăng nhập lại đủ hai bước.
    await page.goto('/admin/audit')
    await expect(page).toHaveURL(/\/login/)
    await loginThroughUi(page, email)
    await expect(page.getByRole('heading', { level: 1, name: 'Thiết lập xác thực hai bước' })).toBeVisible()
    await expect(page.getByTestId('totp-secret')).toHaveText(/^([A-Z2-7]{4} ){7}[A-Z2-7]{4}$/)
    await expect(page.getByRole('link', { name: /Mở trong ứng dụng sinh mã/ })).toHaveAttribute('href', /^otpauth:\/\/totp\//)
    await expectNoSeriousA11yViolations(page)

    // Mã sai khi thiết lập không mở phiên.
    const secret = ((await page.getByTestId('totp-secret').textContent()) ?? '').replace(/\s/g, '')
    await page.getByLabel('Mã sáu chữ số từ ứng dụng').fill(await wrongCode(dev, secret))
    await page.getByRole('button', { name: /Hoàn tất thiết lập/ }).click()
    await expect(page.getByTestId('two-factor-error')).toContainText('Mã chưa khớp')
    expect((await page.request.get('/api/v1/admin/me')).status()).toBe(401)

    await page.getByLabel('Mã sáu chữ số từ ứng dụng').fill(await currentCode(dev, secret))
    await page.getByRole('button', { name: /Hoàn tất thiết lập/ }).click()
    await expect(page.getByRole('heading', { level: 1, name: 'Lưu mười mã khôi phục' })).toBeVisible()
    await expect(page.getByTestId('recovery-code')).toHaveCount(10)
    await expect(page.getByRole('button', { name: /Vào trang quản trị/ })).toBeDisabled()
    await expectNoSeriousA11yViolations(page)
    await page.getByRole('checkbox', { name: 'Tôi đã lưu các mã ở nơi an toàn' }).check()
    await page.getByRole('button', { name: /Vào trang quản trị/ }).click()
    await expect(page.getByRole('heading', { level: 1, name: 'Nhật ký thao tác' })).toBeVisible()

    // Phiên quản trị có hạn cứng tám giờ kể từ lúc đăng nhập.
    const identity = await (await page.request.get('/api/v1/admin/me')).json()
    const hours = (Date.parse(identity.sessionExpiresAt) - (await dev.serverNow())) / 3_600_000
    expect(hours).toBeGreaterThan(7.9)
    expect(hours).toBeLessThanOrEqual(8)
    expect(identity.recoveryCodesRemaining).toBe(10)
    await expect(page.getByTestId('admin-session-expiry')).toContainText('Phiên quản trị hết hạn lúc')

    // Lần sau: mật khẩu rồi mã sáu chữ số (bước 30 giây mới, mã cũ không dùng lại được).
    await dev.advanceClock(31)
    const again = await reachCodeStep(browser, email)
    await expectNoSeriousA11yViolations(again)
    await enterCode(again, await currentCode(dev, secret))
    await expect(again).toHaveURL(/\/admin\/audit$/)
    await expect(again.getByRole('heading', { level: 1, name: 'Nhật ký thao tác' })).toBeVisible()

    // Mọi lần đăng nhập quản trị đều có nhật ký.
    await searchAudit(again, { account: email, category: 'LOGIN', action: 'ADMIN_TWO_FACTOR_ENROLLED' })
    await expect(again.getByTestId('audit-row')).toHaveCount(1)
    await searchAudit(again, { account: email, category: 'LOGIN', action: 'LOGIN_SUCCEEDED' })
    await expect(again.getByTestId('audit-row')).toHaveCount(2)
    await again.context().close()
  })

  test('TC-01: tài khoản quản trị không mở được chức năng học tập; thanh bên chỉ có khu quản trị', async ({
    page,
    dev,
  }) => {
    await freshAdmin(page, dev)

    await page.goto('/')
    await expect(page).toHaveURL(/\/admin\/audit$/)
    await page.goto('/library')
    await expect(page).toHaveURL(/\/admin\/audit$/)
    expect((await page.request.get('/api/v1/library/documents')).status()).toBe(403)

    const sidebar = await openSidebar(page)
    await expect(sidebar.getByRole('link', { name: 'Khu quản trị' })).toBeVisible()
    await expect(sidebar.getByRole('link', { name: 'Thư viện' })).toHaveCount(0)
  })

  test('TC-02: nhập sai mã năm lần liên tiếp thì khóa đăng nhập quản trị mười lăm phút và gửi thư cảnh báo', async ({
    page,
    browser,
    dev,
  }) => {
    const admin = await freshAdmin(page, dev)
    await dev.advanceClock(31)
    const attacker = await reachCodeStep(browser, admin.email)
    for (let attempt = 1; attempt <= 4; attempt += 1) {
      await enterCode(attacker, await wrongCode(dev, admin.secret))
      await expect(attacker.getByTestId('two-factor-error')).toContainText('Mã chưa đúng')
    }
    await expect(attacker.getByTestId('attempts-left')).toContainText('Còn 1 lần thử')
    await enterCode(attacker, await wrongCode(dev, admin.secret))

    const notice = attacker.getByTestId('lockout-notice')
    await expect(notice).toContainText('Đăng nhập quản trị đang tạm khóa')
    await expect(attacker.getByTestId('lockout-countdown')).toHaveText(/^1[45]:\d{2}$/)
    await expect(attacker.getByLabel('Mã sáu chữ số')).toBeDisabled()
    await expectNoSeriousA11yViolations(attacker)

    const mail = await dev.waitForMail(admin.email, 'ADMIN_LOGIN_LOCKED')
    expect(mail.subject).toContain('Đăng nhập quản trị tạm khóa')

    // Đang khóa thì cả bước mật khẩu cũng bị từ chối, kể cả với mã đúng.
    await loginThroughUi(attacker, admin.email)
    await expect(attacker.getByTestId('lockout-notice')).toContainText('Đăng nhập quản trị đang tạm khóa')

    // Hết mười lăm phút thì đăng nhập lại được.
    await dev.advanceClock(15 * 60)
    const later = await reachCodeStep(browser, admin.email)
    await enterCode(later, await currentCode(dev, admin.secret))
    await expect(later).toHaveURL(/\/admin\/audit$/)
    await searchAudit(later, { account: admin.email, category: 'LOGIN', action: 'ADMIN_TWO_FACTOR_LOCKED' })
    await expect(later.getByTestId('audit-row')).toHaveCount(1)
    await attacker.context().close()
    await later.context().close()
  })

  test('TC-03: mất điện thoại sinh mã thì một mã khôi phục cho đăng nhập, bị đánh dấu đã dùng và buộc thiết lập lại', async ({
    page,
    browser,
    dev,
  }) => {
    const admin = await freshAdmin(page, dev)
    await dev.advanceClock(31)
    const lostPhone = await reachCodeStep(browser, admin.email)
    await lostPhone.getByRole('button', { name: /Dùng mã khôi phục/ }).click()
    await expect(lostPhone.getByRole('heading', { level: 1, name: 'Dùng mã khôi phục' })).toBeVisible()
    await expectNoSeriousA11yViolations(lostPhone)
    await lostPhone.getByLabel('Mã khôi phục').fill(admin.recoveryCodes[2].toUpperCase())
    await lostPhone.getByRole('button', { name: /Xác thực/ }).click()

    await expect(lostPhone.getByRole('heading', { level: 1, name: 'Thiết lập lại xác thực hai bước' })).toBeVisible()
    await expect(lostPhone.getByTestId('recovery-used')).toContainText('không dùng lại được')
    const renewed = await completeEnrollment(lostPhone, dev)
    expect(renewed.secret).not.toBe(admin.secret)
    expect(renewed.recoveryCodes).not.toContain(admin.recoveryCodes[2])

    // Mã khôi phục đã dùng và ứng dụng sinh mã cũ đều không mở được phiên nữa.
    await dev.advanceClock(31)
    const thief = await reachCodeStep(browser, admin.email)
    await enterCode(thief, await currentCode(dev, admin.secret))
    await expect(thief.getByTestId('two-factor-error')).toContainText('Mã chưa đúng')
    await thief.getByRole('button', { name: /Dùng mã khôi phục/ }).click()
    await thief.getByLabel('Mã khôi phục').fill(admin.recoveryCodes[2])
    await thief.getByRole('button', { name: /Xác thực/ }).click()
    await expect(thief.getByTestId('two-factor-error')).toContainText('Mã khôi phục chưa đúng hoặc đã được dùng')

    await searchAudit(lostPhone, { account: admin.email, category: 'LOGIN', action: 'ADMIN_RECOVERY_CODE_USED' })
    await expect(lostPhone.getByTestId('audit-row')).toHaveCount(1)
    await lostPhone.context().close()
    await thief.context().close()
  })

  test('TC-04: đã có quản trị viên thì yêu cầu tạo thêm một quản trị viên bị từ chối và ghi nhật ký', async ({
    page,
    browser,
    dev,
  }) => {
    const admin = await freshAdmin(page, dev)
    const other = uniqueEmail('quan-tri-hai')
    const otherPage = await (await browser.newContext()).newPage()
    await createVerifiedAccount(otherPage, dev, other)

    const rejected = await dev.setRole(other, 'ADMIN', false)
    expect(rejected.status()).toBe(409)
    expect((await rejected.json()).code).toBe('ADMIN_ALREADY_EXISTS')

    // Tài khoản kia vẫn là người học: không vào được khu quản trị.
    await otherPage.goto('/admin/audit')
    await expect(otherPage.getByTestId('admin-forbidden')).toBeVisible()
    await otherPage.context().close()

    await searchAudit(page, { category: 'ADMIN', action: 'ADMIN_CREATION_REJECTED' })
    const row = page.getByTestId('audit-row').first()
    await expect(row).toContainText('Từ chối tạo thêm quản trị viên')
    await expect(row.getByTestId('audit-outcome')).toHaveText('Bị từ chối')
    await row.getByRole('button', { name: /^Xem chi tiết/ }).click()
    await expect(row.getByTestId('audit-details')).toContainText(other)
    expect(admin.email).not.toBe(other)
  })

  test('TC-05: người học mở đường dẫn trang quản trị bị từ chối và lần từ chối được ghi nhật ký', async ({
    page,
    browser,
    dev,
  }) => {
    const learner = uniqueEmail('hoc-vien')
    await createVerifiedAccount(page, dev, learner)
    await page.goto('/admin/audit')
    await expect(page.getByRole('heading', { level: 1, name: 'Trang này chỉ dành cho quản trị viên' })).toBeVisible()
    expect((await page.request.get('/api/v1/admin/me')).status()).toBe(403)
    // Người học cũng không tự mở được bước hai của quản trị viên.
    await page.goto('/login/two-factor')
    await expect(page.getByTestId('two-factor-expired')).toBeVisible()

    const adminPage = await (await browser.newContext()).newPage()
    await freshAdmin(adminPage, dev)
    await searchAudit(adminPage, { account: learner, category: 'ACCESS', action: 'ROLE_ACCESS_DENIED' })
    await expect(adminPage.getByTestId('audit-row')).toHaveCount(2)
    await expect(adminPage.getByTestId('audit-row').first().getByTestId('audit-outcome')).toHaveText('Bị từ chối')
    await adminPage.context().close()
  })
})
