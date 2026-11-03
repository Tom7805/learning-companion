import AxeBuilder from '@axe-core/playwright'
import type { Page } from '@playwright/test'
import { createVerifiedAccount, expect, test, type DevApi } from './fixtures/auth'
import { becomeAdmin } from './fixtures/admin'
import { uniqueEmail } from './fixtures/testData'

/**
 * NCL-04-CN-002 — Cấu hình mô hình và lời nhắc hệ thống theo phiên bản (Jira LC-26). Lời gọi thử đi qua bản mô phỏng
 * của lớp kết nối nên câu trả lời cố định. Chuỗi phiên bản bốn lên năm, lời gọi mới chạy theo phiên bản vừa phát hành
 * và nội dung nhật ký được kiểm kỹ ở AiConfigIT phía máy chủ.
 */

async function expectNoSeriousA11yViolations(page: Page) {
  await page.emulateMedia({ reducedMotion: 'reduce' })
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
  const email = uniqueEmail('cau-hinh-ai')
  await createVerifiedAccount(page, dev, email)
  await becomeAdmin(page, dev, email)
  return email
}

/** Mở trang cấu hình từ thanh trang của khu quản trị, như quản trị viên thật. */
async function openConfigPage(page: Page) {
  await page
    .getByRole('navigation', { name: 'Các trang quản trị' })
    .getByRole('link', { name: 'Cấu hình AI' })
    .click()
  await expect(page).toHaveURL(/\/admin\/ai-config$/)
  await expect(page.getByRole('heading', { level: 1, name: 'Cấu hình mô hình và lời nhắc' })).toBeVisible()
  await expect(page.getByTestId('ai-config-version-1')).toBeVisible()
}

test.beforeEach(async ({ dev }) => {
  await dev.resetAiGateway()
  await dev.resetAiConfigs()
})

test.afterEach(async ({ dev }) => {
  await dev.resetAiConfigs()
})

test('TC-01, TC-02, TC-05: sửa lời nhắc, thử câu hỏi mẫu, phát hành rồi quay lại phiên bản trước', async ({
  page,
  dev,
}) => {
  await signUpAdmin(page, dev)
  await openConfigPage(page)
  await expectNoSeriousA11yViolations(page)

  const live = page.getByTestId('ai-config-live')
  await expect(live.getByTestId('ai-config-live-version')).toHaveText('Phiên bản 1')
  await expect(page.getByTestId('ai-config-version-1')).toContainText('Lưu bởi Hệ thống')

  // Sửa lời nhắc và nhiệt độ, lưu thành bản nháp 2.
  const editor = page.getByTestId('ai-config-editor')
  await editor.getByLabel('Lời nhắc hệ thống').fill(
    'Bạn là trợ lý học tập. Trả lời bằng ba gạch đầu dòng và ghi số nguồn trong ngoặc vuông.',
  )
  await editor.getByLabel('Nhiệt độ').fill('0.5')
  await editor.getByRole('button', { name: 'Lưu thành bản nháp' }).click()
  await expect(page.getByText('Đã lưu phiên bản nháp 2.')).toBeVisible()
  const two = page.getByTestId('ai-config-version-2')
  await expect(two.getByTestId('ai-config-status')).toHaveText('Nháp')
  await expect(two).toContainText('dựa trên phiên bản 1')

  // Khác biệt giữa bản đang chạy và bản nháp.
  const diff = page.getByTestId('ai-config-diff')
  await expect(diff.getByTestId('ai-config-diff-fields')).toContainText('Nhiệt độ')
  await expect(diff.locator('[data-change="ADDED"]').first()).toContainText('ba gạch đầu dòng')

  // Thử nhanh bản nháp trên bộ nguồn mẫu rồi phát hành ngay từ khung thử.
  const trial = page.getByTestId('ai-config-trial')
  await expect(trial.getByLabel('Phiên bản thử')).toHaveValue('2')
  await trial.getByRole('button', { name: 'Thử phiên bản 2' }).click()
  await expect(trial.getByTestId('ai-config-trial-output')).toContainText('câu trả lời mô phỏng', {
    timeout: 20_000,
  })
  await expect(trial.getByTestId('ai-config-trial-done')).toContainText('Phiên bản 2 trả lời qua mock-primary')
  await trial.getByRole('button', { name: 'Phát hành phiên bản 2' }).click()
  const publish = page.getByRole('dialog', { name: 'Phát hành phiên bản 2?' })
  await expect(publish).toContainText('Phiên bản 1 chuyển sang đã được thay')
  await publish.getByRole('button', { name: 'Phát hành' }).click()
  await expect(page.getByText('Trợ lý hỏi đáp đang chạy phiên bản 2.')).toBeVisible()
  await expect(live.getByTestId('ai-config-live-version')).toHaveText('Phiên bản 2')
  await expect(live.getByTestId('ai-config-live-published')).not.toContainText('Hệ thống')
  await expect(two.getByTestId('ai-config-published-by')).toContainText('Phát hành bởi')
  await expect(page.getByTestId('ai-config-version-1').getByTestId('ai-config-status')).toHaveText(
    'Đã được thay',
  )

  // Bản 2 có sự cố: quay lại bản 1 chỉ bằng một nút và một lần xác nhận.
  await live.getByRole('button', { name: 'Quay lại phiên bản 1' }).click()
  const rollback = page.getByRole('dialog', { name: 'Quay lại phiên bản trước?' })
  await expect(rollback).toContainText('Phiên bản 2 sẽ bị thu hồi')
  await rollback.getByRole('button', { name: 'Quay lại' }).click()
  await expect(page.getByText('Đã quay lại: Trợ lý hỏi đáp đang chạy phiên bản 1.')).toBeVisible()
  await expect(live.getByTestId('ai-config-live-version')).toHaveText('Phiên bản 1')
  await expect(two.getByTestId('ai-config-status')).toHaveText('Đã thu hồi')
  await expect(two).toContainText('Thu hồi bởi')
  await expectNoSeriousA11yViolations(page)

  // Nhật ký thao tác có đủ lần lưu, phát hành và quay lại.
  await page
    .getByRole('navigation', { name: 'Các trang quản trị' })
    .getByRole('link', { name: 'Nhật ký thao tác' })
    .click()
  // Trang nhật ký giữ kết quả tra cứu lúc đăng nhập trong 30 giây; tải lại để máy chủ tra cứu mới.
  await expect(page.getByRole('heading', { level: 1, name: 'Nhật ký thao tác' })).toBeVisible()
  await page.reload()
  const list = page.getByTestId('audit-list')
  for (const action of ['AI_CONFIG_DRAFT_SAVED', 'AI_CONFIG_PUBLISHED', 'AI_CONFIG_ROLLED_BACK']) {
    await expect(list.locator(`[data-testid="audit-row"][data-action="${action}"]`).first()).toBeVisible()
  }
  await expect(list).toContainText('Quay lại cấu hình AI trước')
})

test('TC-03: lời nhắc có địa chỉ thư bị chặn lưu và đoạn chứa dữ liệu cá nhân được đánh dấu', async ({
  page,
  dev,
}) => {
  await signUpAdmin(page, dev)
  await openConfigPage(page)

  const editor = page.getByTestId('ai-config-editor')
  await editor
    .getByLabel('Lời nhắc hệ thống')
    .fill('Bạn là trợ lý học tập. Khi bí, hãy nhắn cô Hạnh qua hanh.tran@truong.edu.vn hoặc 0912 345 678.')
  await editor.getByRole('button', { name: 'Lưu thành bản nháp' }).click()

  const pii = editor.getByTestId('ai-config-pii')
  await expect(pii).toContainText('Chưa lưu được: lời nhắc hệ thống chứa dữ liệu cá nhân.')
  await expect(pii).toContainText('Tìm thấy 2 đoạn')
  const marks = pii.getByTestId('ai-config-pii-mark')
  await expect(marks).toHaveCount(2)
  await expect(marks.nth(0)).toContainText('hanh.tran@truong.edu.vn')
  await expect(marks.nth(1)).toContainText('0912 345 678')
  await expect(editor.getByLabel('Lời nhắc hệ thống')).toHaveAttribute('aria-invalid', 'true')
  await expect(page.getByTestId('ai-config-version-2')).toHaveCount(0)
  await expectNoSeriousA11yViolations(page)

  // Bỏ các đoạn đó thì lưu được.
  await editor
    .getByLabel('Lời nhắc hệ thống')
    .fill('Bạn là trợ lý học tập. Khi bí, hãy gợi ý học sinh hỏi giáo viên trên lớp.')
  await expect(pii).toHaveCount(0)
  await editor.getByRole('button', { name: 'Lưu thành bản nháp' }).click()
  await expect(page.getByTestId('ai-config-version-2')).toBeVisible()
})

test('TC-04: người học mở trang cấu hình bị từ chối', async ({ page, dev }) => {
  await createVerifiedAccount(page, dev, uniqueEmail('nguoi-hoc-cau-hinh'))

  await page.goto('/admin/ai-config')
  await expect(
    page.getByRole('heading', { level: 1, name: 'Trang này chỉ dành cho quản trị viên' }),
  ).toBeVisible()
  await expect(page.getByText('Lần mở trang này đã được ghi vào nhật ký hệ thống.')).toBeVisible()
  await expect(page.getByTestId('ai-config-editor')).toHaveCount(0)
})
