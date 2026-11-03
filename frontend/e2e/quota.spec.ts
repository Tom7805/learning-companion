import AxeBuilder from '@axe-core/playwright'
import type { Browser, Page } from '@playwright/test'
import { createVerifiedAccount, expect, loginThroughUi, test, type DevApi } from './fixtures/auth'
import { becomeAdmin } from './fixtures/admin'
import { uniqueEmail } from './fixtures/testData'

/**
 * NCL-04-CN-003 — Hạn mức công bằng cho trí tuệ nhân tạo và dung lượng lưu trữ (Jira LC-27). Chưa có chức năng học
 * tập nào tốn lượt, nên tác vụ tốn lượt được chạy qua công cụ dev: tác vụ thật đi qua sổ lượt và lớp kết nối (bản mô
 * phỏng), giống hệt chức năng sau này. Nội dung sổ lượt và nhật ký được kiểm kỹ ở QuotaIT phía máy chủ.
 */

async function expectNoSeriousA11yViolations(page: Page) {
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.mouse.move(0, 0)
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

/** Đồng hồ lượt đang hiện: thanh bên trên máy tính, thanh trên cùng trên điện thoại. */
const meter = (page: Page) => page.locator('[data-testid="quota-meter"]:visible')

async function signUpLearner(page: Page, dev: DevApi, prefix: string) {
  const email = uniqueEmail(prefix)
  await createVerifiedAccount(page, dev, email)
  await page.goto('/')
  await expect(meter(page)).toBeVisible()
  return email
}

async function signUpAdmin(browser: Browser, dev: DevApi) {
  const page = await (await browser.newContext()).newPage()
  const email = uniqueEmail('han-muc-qtv')
  await createVerifiedAccount(page, dev, email)
  await becomeAdmin(page, dev, email)
  return page
}

async function xsrf(page: Page) {
  return (await page.context().cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')?.value ?? ''
}

/** Tra nhật ký theo loại thao tác (không dựa vào 50 dòng mới nhất: test tua đồng hồ để lại dòng ở năm sau). */
async function searchAudit(page: Page, filters: { account?: string; category?: string; action?: string; object?: string }) {
  await page.getByRole('navigation', { name: 'Các trang quản trị' }).getByRole('link', { name: 'Nhật ký thao tác' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Nhật ký thao tác' })).toBeVisible()
  // Trang nhật ký giữ kết quả tra cứu trong 30 giây; tải lại để máy chủ tra cứu mới.
  await page.reload()
  const form = page.getByRole('search', { name: 'Lọc nhật ký' })
  await form.getByLabel('Tài khoản', { exact: true }).fill(filters.account ?? '')
  await form.getByLabel('Nhóm thao tác').selectOption(filters.category ?? '')
  await form.getByLabel('Loại thao tác').selectOption(filters.action ?? '')
  await form.getByLabel('Loại đối tượng').selectOption(filters.object ?? '')
  await form.getByRole('button', { name: 'Tra cứu' }).click()
  return page.getByTestId('audit-list')
}

test.beforeEach(async ({ dev }) => {
  await dev.resetAiGateway()
  await dev.resetQuota()
})

test.afterEach(async ({ dev }) => {
  await dev.resetAiGateway()
  await dev.resetQuota()
})

test('TC-01: còn mười lượt, tác vụ tốn một lượt chạy xong thì đồng hồ còn chín lượt', async ({ page, dev }) => {
  const email = await signUpLearner(page, dev, 'han-muc-tru')
  await dev.setQuotaUsage(email, 40)
  await page.reload()
  await expect(meter(page)).toHaveAttribute('data-remaining', '10')
  await expect(meter(page)).toHaveAccessibleName(/Lượt AI hôm nay: còn 10 trên 50\. Hồi lại lúc 00:00/)

  const run = await dev.runQuotaTask(email, 'ASSISTANT_QUESTION')
  expect(run.ok()).toBeTruthy()
  expect((await run.json()).outcome).toBe('COMMITTED')

  // Đồng hồ hỏi lại khi quay lại thẻ; tải lại để không phải chờ chu kỳ một phút.
  await page.reload()
  await expect(meter(page)).toHaveAttribute('data-remaining', '9')
  await meter(page).click()
  await expect(page).toHaveURL(/\/usage$/)
  await expect(page.getByRole('heading', { level: 1, name: 'Lượt AI và dung lượng' })).toBeVisible()
  await expect(page.getByTestId('quota-ai-remaining')).toHaveText('Còn 9 / 50 lượt')
  await expect(page.getByTestId('quota-ai-used')).toHaveText('41')
  await expect(page.getByTestId('quota-ai-resets')).toHaveText('lúc 00:00')
  await expect(page.getByTestId('quota-costs').locator('[data-task="ASSISTANT_QUESTION"]')).toContainText('1 lượt')
  await expect(page.getByTestId('quota-costs').locator('[data-task="QUIZ_GENERATION"]')).toContainText('5 lượt')
  await expect(page.getByTestId('quota-free-actions')).toContainText('Ôn các bộ thẻ ghi nhớ đã có')
  await expectNoSeriousA11yViolations(page)
})

test('TC-02: tác vụ giữ năm lượt lỗi do dịch vụ bên ngoài thì được hoàn đủ và người học được báo không bị trừ lượt', async ({
  page,
  dev,
}) => {
  const email = await signUpLearner(page, dev, 'han-muc-hoan')
  await dev.setQuotaUsage(email, 40)
  await dev.setAiMockMode('mock-primary', 'FAIL')
  await dev.setAiMockMode('mock-backup', 'FAIL')

  const run = await dev.runQuotaTask(email, 'QUIZ_GENERATION')
  expect(run.status()).toBe(503)
  const failure = await run.json()
  expect(failure.code).toBe('AI_TEMPORARILY_UNAVAILABLE')
  expect(failure.details).toMatchObject({ quotaRefunded: true, refundedUnits: 5 })

  // Thông báo tới ngay qua kênh trực tiếp; đồng hồ vẫn còn đủ mười lượt.
  const bell = page.getByTestId('notification-bell').first()
  await expect(bell).toHaveAccessibleName('Thông báo, 1 chưa đọc')
  await expect(meter(page)).toHaveAttribute('data-remaining', '10')
  await bell.click()
  const item = page.getByTestId('side-panel').getByTestId('notification-item').first()
  await expect(item).toContainText('Không bị trừ lượt')
  await expect(item).toContainText('Sinh bài kiểm tra gặp lỗi từ dịch vụ bên ngoài. 5 lượt đã giữ chỗ được hoàn lại đầy đủ.')
  await expectNoSeriousA11yViolations(page)
  await item.click()
  await expect(page).toHaveURL(/\/usage$/)
  await expect(page.getByTestId('quota-ai-remaining')).toHaveText('Còn 10 / 50 lượt')
  await expect(page.getByTestId('quota-ai-used')).toHaveText('40')

  // Tác vụ bị hủy cũng được hoàn lại.
  await dev.setAiMockMode('mock-primary', 'OK')
  const cancelled = await dev.runQuotaTask(email, 'STUDY_GUIDE', true)
  expect((await cancelled.json()).outcome).toBe('CANCELLED')
  await page.reload()
  await expect(page.getByTestId('quota-ai-remaining')).toHaveText('Còn 10 / 50 lượt')
})

test('TC-03: hết lượt thì thao tác đọc vẫn chạy, chỉ tác vụ mới tốn lượt bị chặn kèm thời điểm hồi lại', async ({
  page,
  dev,
}) => {
  const email = await signUpLearner(page, dev, 'han-muc-het')
  await dev.sampleDocument(email, 'Định luật Ohm')
  await dev.setQuotaUsage(email, 50)
  await page.reload()
  await expect(meter(page)).toHaveAttribute('data-level', 'empty')
  await expect(meter(page)).toHaveAttribute('data-remaining', '0')

  const blocked = await dev.runQuotaTask(email, 'ASSISTANT_QUESTION')
  expect(blocked.status()).toBe(429)
  const body = await blocked.json()
  expect(body.code).toBe('AI_QUOTA_EXHAUSTED')
  expect(body.details.timeZone).toBe('Asia/Ho_Chi_Minh')
  const resetsAt = new Date(body.details.resetsAt)
  expect(
    new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' })
      .format(resetsAt),
  ).toBe('00:00')

  // Trang lượt dùng báo hết lượt, giờ hồi lại và cách học không tốn lượt.
  await meter(page).click()
  const notice = page.getByTestId('quota-exhausted')
  await expect(notice).toContainText('Bạn đã dùng hết lượt AI hôm nay')
  await expect(notice.getByTestId('quota-resets-at')).toHaveText('Lượt hồi đủ lúc 00:00')
  await expect(notice.getByTestId('quota-free-ways')).toContainText('Ôn các bộ thẻ ghi nhớ đã có')
  await expectNoSeriousA11yViolations(page)

  // Mọi thao tác đọc vẫn chạy: mở kho tài liệu và mở tài liệu.
  await notice.getByRole('link', { name: 'Mở kho tài liệu và đọc lại bài' }).click()
  await expect(page).toHaveURL(/\/library$/)
  await page.getByRole('link', { name: /Định luật Ohm/ }).first().click()
  await expect(page.getByRole('heading', { level: 1, name: 'Định luật Ohm' })).toBeVisible()
  await expect(meter(page)).toHaveAttribute('data-remaining', '0')
})

test('TC-04: người học ở múi giờ Việt Nam hết lượt, qua nửa đêm giờ Việt Nam thì lượt hồi đủ', async ({
  page,
  dev,
}) => {
  const email = await signUpLearner(page, dev, 'han-muc-nua-dem')
  await dev.setQuotaUsage(email, 50)
  const status = await (await page.request.get('/api/v1/quota')).json()
  expect(status.ai.timeZone).toBe('Asia/Ho_Chi_Minh')
  expect(status.ai.remainingUnits).toBe(0)

  // Một phút trước nửa đêm vẫn hết lượt.
  const untilMidnight = Math.floor((Date.parse(status.ai.resetsAt) - (await dev.serverNow())) / 1000)
  await dev.advanceClock(untilMidnight - 60)
  // Tua tới gần nửa đêm có thể vượt hạn phiên đăng nhập: đăng nhập lại như người học quay lại sau nhiều giờ.
  await page.context().clearCookies()
  await loginThroughUi(page, email)
  await expect(meter(page)).toHaveAttribute('data-remaining', '0')
  expect((await dev.runQuotaTask(email, 'ASSISTANT_QUESTION')).status()).toBe(429)

  // Qua nửa đêm giờ Việt Nam: hồi đủ năm mươi lượt và chạy được tác vụ mới.
  await dev.advanceClock(120)
  await page.reload()
  await expect(meter(page)).toHaveAttribute('data-remaining', '50')
  await expect(meter(page)).toHaveAttribute('data-level', 'plenty')
  expect((await dev.runQuotaTask(email, 'ASSISTANT_QUESTION')).ok()).toBeTruthy()
  await page.reload()
  await expect(meter(page)).toHaveAttribute('data-remaining', '49')
})

test('TC-05: người học gửi yêu cầu đổi hạn mức hay bảng quy đổi bị từ chối, ghi nhật ký, đồng hồ vẫn bình thường', async ({
  page,
  browser,
  dev,
}) => {
  const email = await signUpLearner(page, dev, 'han-muc-tu-choi')
  const headers = { 'X-XSRF-TOKEN': await xsrf(page) }
  const policy = await page.request.put('/api/v1/admin/quotas/policy', {
    headers,
    data: { dailyAiUnits: 9999, storageLimitMb: 99999 },
  })
  expect(policy.status()).toBe(403)
  expect((await policy.json()).code).toBe('FORBIDDEN')
  const costs = await page.request.put('/api/v1/admin/quotas/costs', {
    headers,
    data: { costs: [{ task: 'QUIZ_GENERATION', units: 1 }] },
  })
  expect(costs.status()).toBe(403)

  // Trang quản trị hạn mức cũng bị chặn; đồng hồ lượt vẫn hiển thị bình thường.
  await page.goto('/admin/quotas')
  await expect(page.getByRole('heading', { level: 1, name: 'Trang này chỉ dành cho quản trị viên' })).toBeVisible()
  await expect(meter(page)).toHaveAttribute('data-remaining', '50')
  await expectNoSeriousA11yViolations(page)

  const adminPage = await signUpAdmin(browser, dev)
  const list = await searchAudit(adminPage, { account: email, category: 'ACCESS', action: 'ROLE_ACCESS_DENIED' })
  await expect(list.getByTestId('audit-row')).toHaveCount(3)
  await expect(list.getByTestId('audit-row').first()).toContainText('Bị chặn vào khu quản trị')
  await expect(list.getByTestId('audit-row').first().getByTestId('audit-outcome')).toHaveText('Bị từ chối')
  // Hạn mức không đổi.
  const current = await (await adminPage.request.get('/api/v1/admin/quotas')).json()
  expect(current.policy.dailyAiUnits).toBe(50)
  await adminPage.context().close()
})

test('TC-06: đổi hạn mức và bảng quy đổi phải xác nhận, nhật ký ghi người thực hiện, nội dung và thời điểm', async ({
  page,
  browser,
  dev,
}) => {
  const learnerEmail = await signUpLearner(page, dev, 'han-muc-ap-dung')
  const adminPage = await signUpAdmin(browser, dev)
  await adminPage
    .getByRole('navigation', { name: 'Các trang quản trị' })
    .getByRole('link', { name: 'Hạn mức' })
    .click()
  await expect(adminPage).toHaveURL(/\/admin\/quotas$/)
  await expect(adminPage.getByRole('heading', { level: 1, name: 'Hạn mức công bằng' })).toBeVisible()
  const policy = adminPage.getByTestId('admin-quota-policy')
  await expect(policy).toContainText('Đang dùng giá trị mặc định lúc cài đặt.')
  await expectNoSeriousA11yViolations(adminPage)

  await policy.getByLabel('Lượt AI mỗi ngày').fill('20')
  await policy.getByLabel('Dung lượng mỗi người học (MB)').fill('500')
  await policy.getByRole('button', { name: 'Lưu hạn mức' }).click()
  const confirmPolicy = adminPage.getByRole('dialog', { name: 'Xác nhận đổi hạn mức' })
  await expect(confirmPolicy.getByTestId('admin-quota-changes')).toContainText('Lượt AI mỗi ngày: 50 → 20')
  await expect(confirmPolicy.getByTestId('admin-quota-changes')).toContainText('Dung lượng mỗi người học (MB): 1024 → 500')
  await expectNoSeriousA11yViolations(adminPage)
  await confirmPolicy.getByRole('button', { name: 'Áp dụng' }).click()
  await expect(adminPage.getByText('Đã lưu hạn mức mới.')).toBeVisible()
  await expect(policy.getByTestId('admin-quota-last-change')).toContainText('Sửa lần cuối bởi')

  const costs = adminPage.getByTestId('admin-quota-costs')
  await costs.getByLabel('Số lượt: Sinh bài kiểm tra').fill('8')
  await costs.getByRole('button', { name: 'Lưu bảng quy đổi' }).click()
  const confirmCosts = adminPage.getByRole('dialog', { name: 'Xác nhận đổi bảng quy đổi' })
  await expect(confirmCosts).toContainText('Sinh bài kiểm tra: 5 → 8')
  await confirmCosts.getByRole('button', { name: 'Áp dụng' }).click()
  await expect(adminPage.getByText('Đã lưu bảng quy đổi mới.')).toBeVisible()
  await expect(costs.getByTestId('admin-quota-costs-last-change')).toContainText('Sửa lần cuối bởi')

  // Hạn mức mới áp dụng ngay cho người học.
  await page.goto('/usage')
  await expect(page.getByTestId('quota-ai-remaining')).toHaveText('Còn 20 / 20 lượt')
  await expect(page.getByTestId('quota-costs').locator('[data-task="QUIZ_GENERATION"]')).toContainText('8 lượt')
  await expect(page.getByTestId('quota-storage')).toContainText('trên 500 MB')
  expect(learnerEmail).toBeTruthy()

  // Nhật ký: lọc theo loại đối tượng "Hạn mức".
  const list = await searchAudit(adminPage, { object: 'QUOTA_POLICY' })
  const policyRow = list.locator('[data-testid="audit-row"][data-action="QUOTA_POLICY_CHANGED"]').first()
  const costsRow = list.locator('[data-testid="audit-row"][data-action="QUOTA_TASK_COSTS_CHANGED"]').first()
  await expect(policyRow).toContainText('Đổi hạn mức chung')
  await expect(costsRow).toContainText('Đổi bảng quy đổi lượt')
  await policyRow.getByRole('button', { name: /Xem chi tiết/ }).click()
  const changes = policyRow.getByTestId('audit-changes')
  await expect(changes).toContainText('dailyAiUnits')
  await expect(changes).toContainText('50 → 20')
  await expect(changes).toContainText('storageLimitMb')
  await expect(changes).toContainText('1024 → 500')
  await costsRow.getByRole('button', { name: /Xem chi tiết/ }).click()
  await expect(costsRow.getByTestId('audit-changes')).toContainText('QUIZ_GENERATION')
  await expect(costsRow.getByTestId('audit-changes')).toContainText('5 → 8')
  await adminPage.context().close()
})
