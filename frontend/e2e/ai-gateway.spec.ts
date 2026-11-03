import AxeBuilder from '@axe-core/playwright'
import type { Page } from '@playwright/test'
import { createVerifiedAccount, expect, test, type DevApi } from './fixtures/auth'
import { becomeAdmin } from './fixtures/admin'
import { uniqueEmail } from './fixtures/testData'

/**
 * NCL-04-CN-001 — Lớp kết nối dịch vụ trí tuệ nhân tạo thay được nhà cung cấp (Jira LC-25). Bản mô phỏng trả kết quả
 * cố định; công cụ dev cho nó lỗi hoặc treo để thử ngắt mạch và quá hạn. Việc đo mức sử dụng không ghi nội dung được
 * kiểm kỹ ở AiGatewayIT phía máy chủ.
 */

async function expectNoSeriousA11yViolations(page: Page) {
  await page.emulateMedia({ reducedMotion: 'reduce' })
  // Chuột rời nút vừa bấm, để quét màu ở trạng thái thường chứ không phải lúc đang rê chuột.
  await page.mouse.move(0, 0)
  await page.waitForTimeout(50)
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .exclude('a[href="/dev/mailbox"]')
    .analyze()
  const serious = results.violations.filter((violation) =>
    ['serious', 'critical'].includes(violation.impact ?? ''),
  )
  expect(
    serious.map(
      (violation) => `${violation.id}: ${violation.nodes.map((node) => node.target.join(' ')).join(', ')}`,
    ),
  ).toEqual([])
}

async function signUpAdmin(page: Page, dev: DevApi) {
  const email = uniqueEmail('quan-tri-ai')
  await createVerifiedAccount(page, dev, email)
  await becomeAdmin(page, dev, email)
  return email
}

/** Mở trang Dịch vụ AI từ thanh trang của khu quản trị, như quản trị viên thật. */
async function openAiPage(page: Page) {
  await page
    .getByRole('navigation', { name: 'Các trang quản trị' })
    .getByRole('link', { name: 'Dịch vụ AI' })
    .click()
  await expect(page).toHaveURL(/\/admin\/ai$/)
  await expect(page.getByRole('heading', { level: 1, name: 'Dịch vụ trí tuệ nhân tạo' })).toBeVisible()
  await expect(page.getByTestId('ai-route-TEXT_GENERATION')).toBeVisible()
}

async function runTestCall(page: Page) {
  await page.getByRole('button', { name: 'Gọi thử' }).click()
  await expect(page.getByRole('button', { name: 'Gọi thử' })).toBeEnabled({ timeout: 20_000 })
}

test.beforeEach(async ({ dev }) => {
  await dev.resetAiGateway()
})

test.afterEach(async ({ dev }) => {
  await dev.resetAiGateway()
})

test('TC-01: lời gọi thử trả theo luồng qua nhà cung cấp chính và được đo không kèm nội dung', async ({
  page,
  dev,
}) => {
  await signUpAdmin(page, dev)
  await openAiPage(page)
  await expectNoSeriousA11yViolations(page)

  const route = page.getByTestId('ai-route-TEXT_GENERATION')
  await expect(route.getByTestId('ai-circuit').first()).toHaveText('Hoạt động bình thường')

  await page.getByLabel('Lời nhắn').fill('Định luật Ohm là gì?')
  await runTestCall(page)
  await expect(page.getByTestId('ai-test-output')).toContainText('câu trả lời mô phỏng từ mock-primary')
  await expect(page.getByTestId('ai-test-done')).toContainText('mock-primary đã trả lời')

  const recent = page.getByTestId('ai-recent-calls')
  await expect(recent).toContainText('ADMIN_TEST')
  await expect(recent).toContainText('Thành công')
  await expect(recent).not.toContainText('Định luật Ohm')
  await expect(page.getByTestId('ai-usage-calls')).not.toHaveText('0')
})

test('TC-02: năm lỗi liên tiếp ngắt mạch, chuyển dự phòng và cảnh báo quản trị viên', async ({
  page,
  dev,
}) => {
  const email = await signUpAdmin(page, dev)
  await openAiPage(page)
  await dev.setAiMockMode('mock-primary', 'FAIL')

  // Mỗi lời gọi thử lại một lần: hai lời gọi đầu là bốn lỗi liên tiếp, nhận lỗi tạm thời.
  for (let call = 0; call < 2; call += 1) {
    await runTestCall(page)
    await expect(page.getByTestId('ai-test-error')).toContainText('đang tạm gián đoạn')
  }
  // Lỗi thứ năm ngắt mạch: lời gọi chuyển sang dự phòng.
  await runTestCall(page)
  await expect(page.getByTestId('ai-test-done')).toContainText('mock-backup đã trả lời')

  const alert = page.getByTestId('ai-alert')
  await expect(alert).toContainText('mock-primary lỗi liên tiếp khi Sinh văn bản')
  await expect(alert).toContainText('chuyển sang mock-backup')
  const [primary] = await page.getByTestId('ai-route-TEXT_GENERATION').getByTestId('ai-circuit').all()
  await expect(primary).toContainText('Đã ngắt mạch')
  await expectNoSeriousA11yViolations(page)

  const mail = await dev.waitForMail(email, 'AI_CIRCUIT_OPEN')
  expect(mail.subject).toContain('ngắt mạch')

  await alert.getByRole('button', { name: 'Đã xem' }).click()
  await expect(page.getByTestId('ai-alerts')).toBeHidden()
})

test('TC-03: nhà cung cấp treo thì lớp kết nối dừng chờ, thử lại một lần rồi báo lỗi tạm thời', async ({
  page,
  dev,
}) => {
  await signUpAdmin(page, dev)
  await openAiPage(page)

  await page.getByRole('button', { name: 'Sửa định tuyến Sinh văn bản' }).click()
  const dialog = page.getByRole('dialog', { name: 'Định tuyến Sinh văn bản' })
  await dialog.getByLabel('Thời gian chờ (giây)').fill('1')
  await dialog.getByRole('button', { name: 'Lưu định tuyến' }).click()
  await expect(dialog).toBeHidden()
  await expect(page.getByTestId('ai-route-TEXT_GENERATION')).toContainText('1 giây')

  await dev.setAiMockMode('mock-primary', 'HANG')
  const started = Date.now()
  await runTestCall(page)
  await expect(page.getByTestId('ai-test-error')).toContainText('đã thử lại mà vẫn lỗi')
  expect(Date.now() - started).toBeGreaterThanOrEqual(1_900)
  await expect(page.getByTestId('ai-recent-calls')).toContainText('Quá hạn')
  await expect(page.getByTestId('ai-recent-calls')).toContainText('2 lần gọi')
})

test('TC-04: nhà cung cấp chưa xác nhận cam kết không huấn luyện không bật được', async ({ page, dev }) => {
  await signUpAdmin(page, dev)
  await openAiPage(page)

  await page.getByRole('button', { name: 'Thêm nhà cung cấp' }).click()
  const dialog = page.getByRole('dialog', { name: 'Thêm nhà cung cấp' })
  const code = `thu-${Date.now().toString(36)}`
  await dialog.getByLabel('Tên hiển thị').fill('Dịch vụ chưa xác nhận')
  await dialog.getByLabel('Mã', { exact: true }).fill(code)
  await dialog.getByLabel('Địa chỉ dịch vụ').fill('https://ai.example.com/v1')
  await dialog.getByLabel('Mô hình sinh văn bản').fill('model-thu')
  await dialog.getByLabel('Tên khóa trong kho bí mật').fill('LC_AI_E2E_KEY')
  await expectNoSeriousA11yViolations(page)
  await dialog.getByRole('button', { name: 'Thêm nhà cung cấp' }).click()
  await expect(dialog).toBeHidden()

  const row = page.getByTestId(`ai-provider-${code}`)
  await expect(row).toContainText('Chưa xác nhận không huấn luyện')
  await row.getByRole('switch', { name: 'Bật hoặc tắt Dịch vụ chưa xác nhận' }).click()
  const error = row.getByTestId('ai-provider-error')
  await expect(error).toContainText('chưa được xác nhận cam kết không dùng dữ liệu học tập để huấn luyện')
  await expect(error).toContainText('Còn thiếu: xác nhận cam kết không huấn luyện, khóa trong kho bí mật.')
  await expect(row.getByRole('switch')).toHaveAttribute('aria-checked', 'false')
})

test('TC-05: người học mở lớp kết nối bị từ chối', async ({ page, dev }) => {
  await createVerifiedAccount(page, dev, uniqueEmail('hoc-vien-ai'))
  await page.goto('/admin/ai')
  await expect(
    page.getByRole('heading', { level: 1, name: 'Trang này chỉ dành cho quản trị viên' }),
  ).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Dịch vụ trí tuệ nhân tạo' })).toHaveCount(0)
})

test('TC-06: chuyển dự phòng bằng tay được ghi vào nhật ký thao tác', async ({ page, dev }) => {
  const email = await signUpAdmin(page, dev)
  await openAiPage(page)

  const route = page.getByTestId('ai-route-OCR')
  await route.getByRole('button', { name: 'Chuyển Nhận dạng chữ sang dự phòng' }).click()
  const confirm = page.getByRole('dialog', { name: 'Chuyển Nhận dạng chữ sang dự phòng?' })
  await expect(confirm).toContainText('Bản mô phỏng dự phòng sẽ thành nhà cung cấp chính')
  await confirm.getByRole('button', { name: 'Chuyển ngay' }).click()
  await expect(page.getByText('Đã chuyển Nhận dạng chữ sang Bản mô phỏng dự phòng.')).toBeVisible()
  const [primary] = await route.getByTestId('ai-route-endpoint').all()
  await expect(primary).toContainText('Bản mô phỏng dự phòng')

  await page.goto(
    `/admin/audit?category=ADMIN&action=AI_FAILOVER_SWITCHED&account=${encodeURIComponent(email)}`,
  )
  const rows = page.getByTestId('audit-row')
  await expect(rows).toHaveCount(1)
  await expect(rows.first()).toContainText('Chuyển dự phòng AI')
  await expect(rows.first()).toContainText(email)
})
