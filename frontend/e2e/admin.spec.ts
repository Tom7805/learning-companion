import AxeBuilder from '@axe-core/playwright'
import type { Browser, Page } from '@playwright/test'
import { createVerifiedAccount, expect, loginThroughUi, test, type DevApi } from './fixtures/auth'
import { becomeAdmin } from './fixtures/admin'
import { openAccountMenu } from './fixtures/shell'
import { STRONG_PASSWORD, uniqueEmail } from './fixtures/testData'

/**
 * NCL-03-CN-001 — Ghi và tra cứu nhật ký thao tác (Jira LC-19). Hủy thao tác khi không ghi được nhật ký (TC-02),
 * lọc bỏ trường nội dung (TC-04) và lưu một năm được kiểm kỹ ở AuditLogIT phía máy chủ.
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

/**
 * Quản trị viên duy nhất (NCL-03-CN-002) đăng nhập đủ hai bước, rồi mở khu quản trị như người dùng thật: từ trình đơn
 * tài khoản.
 */
async function signUpAdmin(page: Page, dev: DevApi) {
  const email = await signUp(page, dev, 'quan-tri')
  await becomeAdmin(page, dev, email)
  await page.goto('/admin')
  const menu = await openAccountMenu(page)
  await menu.getByRole('menuitem', { name: 'Quản trị' }).click()
  await expect(page).toHaveURL(/\/admin\/audit$/)
  await expect(page.getByRole('heading', { level: 1, name: 'Nhật ký thao tác' })).toBeVisible()
  return email
}

/** Trên một trình duyệt khác: nhập sai mật khẩu ba lần rồi đăng nhập đúng. */
async function threeWrongThenRight(browser: Browser, email: string) {
  const other = await (await browser.newContext()).newPage()
  for (let attempt = 1; attempt <= 3; attempt += 1) {
    await loginThroughUi(other, email, `${STRONG_PASSWORD} sai ${attempt}`)
    await expect(other.getByText(/Thư điện tử hoặc mật khẩu chưa đúng/)).toBeVisible()
  }
  await loginThroughUi(other, email)
  await expect(other.getByTestId('home-greeting')).toBeVisible()
  await other.context().close()
}

async function search(page: Page, filters: { account?: string; category?: string; action?: string }) {
  const form = page.getByRole('search', { name: 'Lọc nhật ký' })
  await form.getByLabel('Tài khoản', { exact: true }).fill(filters.account ?? '')
  await form.getByLabel('Nhóm thao tác').selectOption(filters.category ?? '')
  await form.getByLabel('Loại thao tác').selectOption(filters.action ?? '')
  await form.getByRole('button', { name: 'Tra cứu' }).click()
}

const rows = (page: Page) => page.getByTestId('audit-row')

async function xsrf(page: Page) {
  return (await page.context().cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')?.value ?? ''
}

test.describe('NCL-03-CN-001 · Nhật ký thao tác', () => {
  test('TC-01, TC-06: lọc theo tài khoản ra bốn dòng đăng nhập kèm thiết bị, thời điểm, kết quả; lần tra cứu được ghi lại', async ({
    page,
    browser,
    dev,
  }) => {
    const learner = uniqueEmail('dang-nhap')
    const learnerPage = await (await browser.newContext()).newPage()
    await createVerifiedAccount(learnerPage, dev, learner)
    await learnerPage.context().close()
    await threeWrongThenRight(browser, learner)

    const admin = await signUpAdmin(page, dev)
    await search(page, { account: learner, category: 'LOGIN' })
    await expect(page).toHaveURL(/category=LOGIN/)

    await expect(rows(page)).toHaveCount(4)
    await expect(page.getByTestId('audit-count')).toHaveText('4 dòng')
    await expect(rows(page).nth(0)).toContainText('Đăng nhập thành công')
    await expect(rows(page).nth(0).getByTestId('audit-outcome')).toHaveText('Thành công')
    for (const index of [1, 2, 3]) {
      await expect(rows(page).nth(index)).toContainText('Đăng nhập thất bại')
      await expect(rows(page).nth(index).getByTestId('audit-outcome')).toHaveText('Thất bại')
      await expect(rows(page).nth(index)).toContainText(learner)
    }
    for (const row of await rows(page).all()) {
      await expect(row).toContainText('Chrome · ')
      await expect(row.locator('time')).toHaveText(/\d{1,2}:\d{2}/)
    }

    // Chi tiết một lần nhập sai: lý do và mã yêu cầu, không có mật khẩu đã gõ.
    const failed = rows(page).nth(1)
    await failed.getByRole('button', { name: 'Xem chi tiết: Đăng nhập thất bại' }).click()
    const details = failed.getByTestId('audit-details')
    await expect(details).toContainText('INVALID_CREDENTIALS')
    await expect(details).toContainText('Mã yêu cầu')
    await expect(details).not.toContainText('sai 1')
    await expectNoSeriousA11yViolations(page)

    // TC-06: lần tra cứu vừa rồi có trong nhật ký, ghi người tra, điều kiện và thời điểm.
    await search(page, { account: admin, category: 'ADMIN', action: 'AUDIT_LOG_SEARCHED' })
    const searched = rows(page).first()
    await expect(searched).toContainText('Tra cứu nhật ký')
    await expect(searched).toContainText(admin)
    await searched.getByRole('button', { name: /^Xem chi tiết/ }).click()
    await expect(searched.getByTestId('audit-details')).toContainText('ADMIN')
  })

  test('TC-05, TC-03: người học mở thẳng trang nhật ký bị từ chối và bị ghi lại; sửa hay xóa nhật ký bị từ chối và bị ghi lại', async ({
    page,
    browser,
    dev,
  }) => {
    const learner = await signUp(page, dev, 'nguoi-hoc')
    const menu = await openAccountMenu(page)
    await expect(menu.getByRole('menuitem', { name: 'Quản trị' })).toHaveCount(0)
    await page.keyboard.press('Escape')

    await page.goto('/admin/audit')
    await expect(page.getByRole('heading', { level: 1, name: 'Trang này chỉ dành cho quản trị viên' })).toBeVisible()
    await expect(page.getByTestId('audit-filters')).toHaveCount(0)
    await expectNoSeriousA11yViolations(page)

    const adminPage = await (await browser.newContext()).newPage()
    const admin = await signUpAdmin(adminPage, dev)
    await search(adminPage, { account: learner, category: 'ACCESS' })
    await expect(rows(adminPage)).toHaveCount(1)
    await expect(rows(adminPage).first()).toContainText('Bị chặn vào khu quản trị')
    await expect(rows(adminPage).first().getByTestId('audit-outcome')).toHaveText('Bị từ chối')

    // TC-03: gửi yêu cầu xóa một dòng nhật ký bằng phiên quản trị.
    const latest = await (await adminPage.request.get('/api/v1/admin/audit-logs?limit=1')).json()
    const rowId = latest.items[0].id as string
    const removal = await adminPage.request.delete(`/api/v1/admin/audit-logs/${rowId}`, {
      headers: { 'X-XSRF-TOKEN': await xsrf(adminPage) },
    })
    expect(removal.status()).toBe(403)
    expect((await removal.json()).code).toBe('AUDIT_LOG_IMMUTABLE')

    await search(adminPage, { account: admin, category: 'ACCESS', action: 'AUDIT_LOG_TAMPER_ATTEMPTED' })
    await expect(rows(adminPage)).toHaveCount(1)
    await expect(rows(adminPage).first()).toContainText('Cố sửa hoặc xóa nhật ký')
    await expect(rows(adminPage).first().getByTestId('audit-outcome')).toHaveText('Bị từ chối')
    // Dòng bị nhắm tới vẫn còn nguyên.
    const still = await (await adminPage.request.get('/api/v1/admin/audit-logs?limit=100')).json()
    expect(still.items.map((item: { id: string }) => item.id)).toContain(rowId)
    await adminPage.context().close()
  })
})
