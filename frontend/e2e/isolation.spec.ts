import AxeBuilder from '@axe-core/playwright'
import type { Browser, Page } from '@playwright/test'
import { createVerifiedAccount, expect, test } from './fixtures/auth'
import { uniqueEmail } from './fixtures/testData'
import { navigateTo } from './fixtures/shell'

/**
 * NCL-01-CN-004 — Cách ly dữ liệu học tập theo chủ sở hữu (Jira LC-21).
 * Mỗi người học là một ngữ cảnh trình duyệt riêng (cookie riêng).
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

async function otherLearner(browser: Browser, dev: Parameters<typeof createVerifiedAccount>[1], tag: string) {
  const page = await (await browser.newContext()).newPage()
  const email = uniqueEmail(tag)
  await createVerifiedAccount(page, dev, email)
  return { page, email }
}

async function accountId(page: Page) {
  const session = await (await page.request.get('/api/v1/auth/session')).json()
  return session.account.id as string
}

test.describe('NCL-01-CN-004 · Cách ly dữ liệu học tập theo chủ sở hữu', () => {
  test('TC-01: người học mở bản ghi mẫu của mình từ thư viện và thấy đủ nội dung', async ({ page, dev }) => {
    const email = uniqueEmail('thu-vien')
    await createVerifiedAccount(page, dev, email)
    await dev.sampleDocument(email, 'Ghi chú: Định luật Ohm')

    await page.goto('/')
    await navigateTo(page, 'Thư viện')
    await expect(page.getByRole('heading', { level: 1, name: 'Thư viện của tôi' })).toBeVisible()
    await expect(page.getByTestId('privacy-note')).toContainText('không bao giờ thấy nội dung')
    await expect(page.getByTestId('library-summary')).toContainText('1 tài liệu')
    await expectNoSeriousA11yViolations(page)

    await page.getByTestId('document-card').click()
    await expect(page.getByRole('heading', { level: 1, name: 'Ghi chú: Định luật Ohm' })).toBeVisible()
    await expect(page.getByTestId('document-content')).toContainText('I = U / R')
    await expect(page.getByTestId('document-content')).toContainText('1/R = 1/R1 + 1/R2')
    await expect(page.getByText('Chỉ mình bạn xem được')).toBeVisible()
    await expectNoSeriousA11yViolations(page)

    await page.getByTestId('preview-file').click()
    await expect(page.getByTestId('file-preview')).toContainText('I = U / R')

    await page.getByRole('link', { name: 'Về thư viện' }).click()
    await expect(page).toHaveURL(/\/library$/)
  })

  test('TC-02: đường dẫn ký quá sáu phút bị kho tệp từ chối và giao diện tự xin đường dẫn mới', async ({
    page,
    dev,
  }) => {
    const email = uniqueEmail('het-han')
    await createVerifiedAccount(page, dev, email)
    const id = await dev.sampleDocument(email)
    const fileResponses: number[] = []
    page.on('response', (response) => {
      if (response.url().includes('/api/v1/files/')) fileResponses.push(response.status())
    })

    await page.goto(`/library/${id}`)
    await page.getByTestId('preview-file').click()
    await expect(page.getByTestId('file-preview')).toContainText('I = U / R')
    expect(fileResponses).toEqual([200])

    await dev.advanceClock(6 * 60)
    await page.getByRole('button', { name: 'Tải lại tệp' }).click()

    await expect(page.getByTestId('link-renewed')).toContainText('hệ thống đã tự lấy đường dẫn mới')
    await expect(page.getByTestId('file-preview')).toContainText('I = U / R')
    expect(fileResponses).toEqual([200, 410, 200])
  })

  test('TC-04: biết mã tài liệu của người khác vẫn chỉ thấy "không tìm thấy", như mã không tồn tại', async ({
    page,
    browser,
    dev,
  }) => {
    const ownerEmail = uniqueEmail('chu')
    await createVerifiedAccount(page, dev, ownerEmail)
    const id = await dev.sampleDocument(ownerEmail, 'Nhật ký riêng của Lan')

    const { page: stranger } = await otherLearner(browser, dev, 'la')
    const apiResponse = stranger.waitForResponse((response) => response.url().endsWith(`/library/documents/${id}`))
    await stranger.goto(`/library/${id}`)
    expect((await apiResponse).status()).toBe(404)
    await expect(stranger.getByTestId('document-not-found')).toContainText('Không tìm thấy tài liệu')
    await expect(stranger.locator('body')).not.toContainText('Nhật ký riêng')
    await expectNoSeriousA11yViolations(stranger)

    // Mã không tồn tại cho đúng màn hình đó.
    await stranger.goto('/library/00000000-0000-0000-0000-000000000000')
    await expect(stranger.getByTestId('document-not-found')).toContainText('Không tìm thấy tài liệu')

    // Thư viện của người lạ không có tài liệu của chủ sở hữu, chủ sở hữu vẫn mở bình thường.
    await stranger.getByRole('link', { name: 'Về thư viện' }).click()
    await expect(stranger.getByTestId('library-empty')).toBeVisible()
    await page.goto(`/library/${id}`)
    await expect(page.getByRole('heading', { level: 1, name: 'Nhật ký riêng của Lan' })).toBeVisible()
  })

  test('TC-03: quản trị viên xem tài khoản người học chỉ nhận số bản ghi và dung lượng, không có nội dung', async ({
    page,
    browser,
    dev,
  }) => {
    const learnerEmail = uniqueEmail('hoc-vien')
    await createVerifiedAccount(page, dev, learnerEmail)
    await dev.sampleDocument(learnerEmail, 'Bài tập riêng tư')
    await dev.sampleDocument(learnerEmail, 'Ghi chú thứ hai')
    const learnerId = await accountId(page)

    // Người học không gọi được điểm cuối quản trị.
    expect((await page.request.get(`/api/v1/admin/users/${learnerId}`)).status()).toBe(403)

    const { page: adminPage, email: adminEmail } = await otherLearner(browser, dev, 'quan-tri')
    // Vai trò được đọc lại ở mỗi yêu cầu nên phiên đang mở có quyền quản trị ngay.
    await dev.setRole(adminEmail, 'ADMIN')

    const response = await adminPage.request.get(
      `/api/v1/admin/users/${learnerId}?include=content&fields=contentText,title`,
    )
    expect(response.status()).toBe(200)
    const summary = await response.json()
    expect(Object.keys(summary).sort()).toEqual(
      ['createdAt', 'displayName', 'email', 'emailVerifiedAt', 'id', 'learningRecords', 'role', 'status',
        'storageBytesUsed'].sort(),
    )
    expect(summary.learningRecords).toBe(2)
    expect(summary.storageBytesUsed).toBeGreaterThan(0)
    expect(JSON.stringify(summary)).not.toContain('Bài tập riêng tư')
  })
})
