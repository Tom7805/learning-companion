import AxeBuilder from '@axe-core/playwright'
import type { Browser, Page } from '@playwright/test'
import { createVerifiedAccount, expect, test, type DevApi } from './fixtures/auth'
import { pdf } from './fixtures/files'
import { openSidebar } from './fixtures/shell'
import { uniqueEmail } from './fixtures/testData'

/**
 * NCL-06-CN-001 — Không gian học không giới hạn gắn tài liệu từ kho (Jira LC-34). Chạy trên máy tính và điện thoại,
 * kèm kiểm tra trợ năng bằng axe.
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
  return email
}

async function createWorkspace(page: Page, name: string) {
  await page.goto('/workspaces?new=1')
  const dialog = page.getByRole('dialog', { name: 'Tạo không gian học' })
  await expect(dialog).toBeVisible()
  await dialog.getByTestId('workspace-name').fill(name)
  await dialog.getByTestId('workspace-save').click()
  await expect(page.getByTestId('workspace-title')).toHaveText(name)
  return page.url().split('/workspaces/')[1].split('?')[0]
}

async function attachFromLibrary(page: Page, title: string) {
  await page.getByTestId('attach-documents').click()
  const dialog = page.getByRole('dialog', { name: 'Gắn tài liệu' })
  await dialog.getByTestId('attach-search').fill(title)
  await dialog.getByTestId('attach-option').filter({ hasText: title }).getByRole('checkbox').check()
  await dialog.getByTestId('attach-submit').click()
  await expect(dialog).toHaveCount(0)
}

async function usedBytes(page: Page) {
  const response = await page.request.get('/api/v1/quota')
  return ((await response.json()) as { storage: { usedBytes: number } }).storage.usedBytes
}

test.beforeEach(async ({ dev }) => {
  await dev.resetQuota()
  await dev.setDocumentProcessing(true)
})

test.describe('NCL-06-CN-001 · Không gian học gắn tài liệu từ kho', () => {
  test('TC-01: đã có hai trăm không gian học, tạo thêm Kinh tế lượng vẫn bình thường', async ({ page, dev }, info) => {
    test.setTimeout(120_000)
    const email = await signUp(page, dev, 'kgh-200')
    await dev.seedWorkspaces(email, 200)

    await page.goto('/workspaces')
    await expect(page.getByTestId('workspaces-summary')).toHaveText('200 không gian học')
    await expectNoSeriousA11yViolations(page)

    await page.getByTestId('create-workspace').click()
    const dialog = page.getByRole('dialog', { name: 'Tạo không gian học' })
    await expect(dialog.getByTestId('workspace-name')).toBeFocused()
    await dialog.getByTestId('workspace-name').fill('Kinh tế lượng')
    await dialog.getByRole('radio', { name: 'Biểu đồ' }).check({ force: true })
    await dialog.getByRole('radio', { name: 'Xanh trời' }).check({ force: true })
    await dialog.getByTestId('workspace-description').fill('Ôn thi cuối kỳ')
    await expectNoSeriousA11yViolations(page)
    await page.screenshot({ path: info.outputPath('workspace-create.png') })
    await dialog.getByTestId('workspace-save').click()

    await expect(page.getByTestId('workspace-title')).toHaveText('Kinh tế lượng')
    await expect(page.getByTestId('workspace-guide')).toBeVisible()
    await expectNoSeriousA11yViolations(page)
    await page.screenshot({ path: info.outputPath('workspace-guide.png') })

    // Thanh bên đưa không gian học vừa tạo lên đầu, kèm đường dẫn tới đủ 201 không gian.
    const sidebar = await openSidebar(page)
    await expect(sidebar.getByTestId('sidebar-workspace').first()).toHaveText('Kinh tế lượng')
    await expect(sidebar.getByTestId('sidebar-all-workspaces')).toHaveText('Xem tất cả 201 không gian học')
  })

  test('TC-02: một giáo trình dùng ở hai không gian học, không tải lại và dung lượng không tăng', async ({
    page,
    dev,
  }, info) => {
    test.setTimeout(120_000)
    const email = await signUp(page, dev, 'kgh-share')
    await dev.seedLibrary(email, {
      folders: ['Học kỳ một'],
      documents: [
        { title: 'Giáo trình Thống kê', type: 'PDF', folder: 'Học kỳ một' },
        { title: 'Đại số tuyến tính', type: 'DOCX' },
      ],
    })
    await createWorkspace(page, 'Xác suất')
    await attachFromLibrary(page, 'Giáo trình Thống kê')
    await expect(page.getByTestId('workspace-stat-total')).toContainText('1')
    const before = await usedBytes(page)

    await createWorkspace(page, 'Kinh tế lượng')
    await page.getByTestId('attach-documents').click()
    const dialog = page.getByRole('dialog', { name: 'Gắn tài liệu' })
    if ((page.viewportSize()?.width ?? 1440) < 640) {
      await dialog.getByTestId('attach-folder-select').selectOption({ label: '  Học kỳ một' })
    } else {
      await dialog.getByTestId('attach-folders').getByText('Học kỳ một').click()
    }
    await expect(dialog.getByTestId('attach-option')).toHaveCount(1)
    await dialog.getByTestId('attach-option').getByRole('checkbox').check()
    await expectNoSeriousA11yViolations(page)
    await page.screenshot({ path: info.outputPath('workspace-attach.png') })
    await dialog.getByTestId('attach-submit').click()
    await expect(page.getByText('Đã gắn 1 tài liệu vào “Kinh tế lượng”.')).toBeVisible()

    await page.getByTestId('workspace-tab-sources').click()
    await expect(page.getByTestId('workspace-document')).toHaveText(/Giáo trình Thống kê/)
    expect(await usedBytes(page)).toBe(before)

    // Mở tài liệu ở khung xem bên ngay trong không gian học.
    await page.getByTestId('workspace-document-open').click()
    await expect(page.getByTestId('side-panel')).toBeVisible()
    await expect(page).toHaveURL(/viewer=/)
    await expectNoSeriousA11yViolations(page)
    await page.screenshot({ path: info.outputPath('workspace-sources-viewer.png') })

    // Khung thông tin tài liệu trong kho thấy cả hai không gian học.
    await page.goto('/library')
    await page.getByTestId('document-item').filter({ hasText: 'Giáo trình Thống kê' }).getByTestId('document-info-button').click()
    const usage = page.getByTestId('document-usage')
    await expect(usage.getByRole('link')).toHaveText(['Kinh tế lượng', 'Xác suất'])
  })

  test('Tải tệp mới ngay tại không gian học: tệp vào thư mục mặc định trong kho và được gắn luôn', async ({ page, dev }) => {
    test.setTimeout(120_000)
    await signUp(page, dev, 'kgh-upload')
    await createWorkspace(page, 'Vật lý')
    await page.getByTestId('guide-attach-upload').click()
    const dialog = page.getByRole('dialog', { name: 'Gắn tài liệu' })
    await dialog.getByTestId('upload-input').setInputFiles(pdf('co-hoc.pdf', 'Co hoc Newton'))
    await expect(dialog.getByTestId('upload-item')).toHaveAttribute('data-state', 'done', { timeout: 30_000 })
    await dialog.getByRole('button', { name: 'Xong', exact: true }).click()
    await expect(page.getByTestId('workspace-stat-total')).toContainText('1')

    await page.goto('/library')
    await expect(page.getByRole('navigation', { name: 'Thư mục và lối tắt' }).or(page.getByTestId('open-folder-tree')).first()).toBeVisible()
    const response = await page.request.get('/api/v1/library/folders')
    const tree = (await response.json()) as { folders: { name: string; documentCount: number }[] }
    expect(tree.folders).toContainEqual(expect.objectContaining({ name: 'Vật lý', documentCount: 1 }))
  })

  test('TC-03: xóa tài liệu đang được ba không gian học dùng thì báo trước tên cả ba, xác nhận mới xóa', async ({
    page,
    dev,
  }, info) => {
    test.setTimeout(150_000)
    const email = await signUp(page, dev, 'kgh-delete')
    await dev.seedLibrary(email, { documents: [{ title: 'Tài liệu dùng chung', type: 'PDF' }] })
    for (const name of ['Xác suất', 'Kinh tế lượng', 'Thống kê']) {
      await createWorkspace(page, name)
      await attachFromLibrary(page, 'Tài liệu dùng chung')
    }

    await page.goto('/library')
    const item = page.getByTestId('document-item').filter({ hasText: 'Tài liệu dùng chung' })
    await item.getByTestId('select-document').check({ force: true })
    await page.getByTestId('bulk-bar').getByRole('button', { name: /Xóa/ }).click()
    const confirm = page.getByRole('dialog', { name: 'Xóa 1 tài liệu?' })
    await expect(confirm.getByTestId('delete-impact-workspace')).toHaveText(['Kinh tế lượng', 'Thống kê', 'Xác suất'])
    await expectNoSeriousA11yViolations(page)
    await page.screenshot({ path: info.outputPath('workspace-delete-impact.png') })

    await confirm.getByRole('button', { name: 'Chuyển vào thùng rác' }).click()
    await expect(page.getByText('Đã chuyển 1 tài liệu vào thùng rác.')).toBeVisible()
    await page.goto('/workspaces')
    await expect(page.getByTestId('workspace-card-count')).toHaveText(['Chưa gắn tài liệu', 'Chưa gắn tài liệu', 'Chưa gắn tài liệu'])
  })

  test('TC-04: gỡ tài liệu khỏi không gian học, mở lại kho thì tài liệu vẫn còn nguyên', async ({ page, dev }) => {
    test.setTimeout(120_000)
    const email = await signUp(page, dev, 'kgh-detach')
    await dev.seedLibrary(email, { documents: [{ title: 'Giáo trình Thống kê', type: 'PDF' }] })
    await createWorkspace(page, 'Xác suất')
    await attachFromLibrary(page, 'Giáo trình Thống kê')
    await page.getByTestId('workspace-tab-sources').click()
    await page.getByTestId('workspace-document-detach').click()
    const confirm = page.getByRole('dialog', { name: 'Gỡ tài liệu khỏi không gian học?' })
    await expect(confirm).toContainText('Tài liệu vẫn nguyên trong kho')
    await confirm.getByRole('button', { name: 'Gỡ khỏi không gian học' }).click()
    await expect(page.getByText('Đã gỡ “Giáo trình Thống kê”. Tài liệu vẫn ở trong kho.')).toBeVisible()
    await expect(page.getByTestId('workspace-document')).toHaveCount(0)
    await expectNoSeriousA11yViolations(page)

    await page.goto('/library')
    await expect(page.getByTestId('document-item').filter({ hasText: 'Giáo trình Thống kê' })).toBeVisible()
  })

  test('TC-05: người học khác mở không gian học bằng đường dẫn trực tiếp thì không tìm thấy', async ({
    page,
    dev,
    browser,
  }) => {
    await signUp(page, dev, 'kgh-owner')
    const id = await createWorkspace(page, 'Riêng tư')
    const other = await otherLearner(browser, dev)
    const denied = await other.request.get(`/api/v1/workspaces/${id}`)
    expect(denied.status()).toBe(404)
    await other.goto(`/workspaces/${id}`)
    await expect(other.getByRole('heading', { level: 1, name: 'Không tìm thấy không gian học' })).toBeVisible()
    await expectNoSeriousA11yViolations(other)
    await other.context().close()
  })

  test('Xóa không gian học: vào thùng rác, tài liệu vẫn ở kho', async ({ page, dev }) => {
    test.setTimeout(120_000)
    const email = await signUp(page, dev, 'kgh-trash')
    await dev.seedLibrary(email, { documents: [{ title: 'Bài giảng tuần 1', type: 'PDF' }] })
    await createWorkspace(page, 'Giải tích')
    await attachFromLibrary(page, 'Bài giảng tuần 1')
    await page.getByTestId('delete-workspace').click()
    const confirm = page.getByRole('dialog', { name: 'Xóa không gian học “Giải tích”?' })
    await confirm.getByRole('button', { name: 'Chuyển vào thùng rác' }).click()
    await expect(page).toHaveURL(/\/workspaces$/)
    await expect(page.getByText('Chưa có không gian học nào', { exact: true })).toBeVisible()
    await page.goto('/library')
    await expect(page.getByTestId('document-item').filter({ hasText: 'Bài giảng tuần 1' })).toBeVisible()
  })
})

async function otherLearner(browser: Browser, dev: DevApi) {
  const context = await browser.newContext()
  const page = await context.newPage()
  await createVerifiedAccount(page, dev, uniqueEmail('kgh-stranger'))
  return page
}
