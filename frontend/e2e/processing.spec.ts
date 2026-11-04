import AxeBuilder from '@axe-core/playwright'
import type { Browser, Locator, Page } from '@playwright/test'
import { createVerifiedAccount, expect, test, type DevApi } from './fixtures/auth'
import type { UploadFile } from './fixtures/files'
import { uniqueEmail } from './fixtures/testData'

/**
 * NCL-05-CN-003 — Trích văn bản, nhận dạng chữ, chia đoạn và lập chỉ mục (Jira LC-31). Tài liệu tải lên đi từ chờ xử
 * lý sang sẵn sàng hoặc lỗi kèm lý do; người học nhận thông báo khi xong. Nhận dạng chữ và vector dùng bản mô phỏng của
 * lớp kết nối.
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

const isMobile = (page: Page) => (page.viewportSize()?.width ?? 1440) < 1024

/** Đăng ký người học có sẵn thư mục "Giải tích" và mở thư mục đó. */
async function openFolder(page: Page, dev: DevApi, tag: string) {
  const email = uniqueEmail(tag)
  await createVerifiedAccount(page, dev, email)
  const seeded = await dev.seedLibrary(email, { folders: ['Giải tích'] })
  await page.goto(`/library?folder=${seeded.folders['Giải tích']}`)
  await expect(page.getByTestId('library-view-title')).toHaveText('Giải tích')
  return email
}

async function addFiles(page: Page, files: UploadFile[]) {
  if (isMobile(page)) {
    const chooser = page.waitForEvent('filechooser')
    await page.getByRole('button', { name: 'Chọn tệp' }).click()
    await (await chooser).setFiles(files)
    return
  }
  const target = page.getByTestId('library-main')
  await expect(page.getByTestId('upload-dropzone')).toBeVisible()
  const dataTransfer = await page.evaluateHandle(
    (items) => {
      const transfer = new DataTransfer()
      for (const item of items) transfer.items.add(new File([new Uint8Array(item.bytes)], item.name, { type: item.type }))
      return transfer
    },
    files.map((file) => ({ name: file.name, type: file.mimeType, bytes: Array.from(file.buffer) })),
  )
  await target.dispatchEvent('dragover', { dataTransfer })
  await target.dispatchEvent('drop', { dataTransfer })
}

function documentItem(page: Page, title: string): Locator {
  return page.getByTestId('document-item').filter({ hasText: title })
}

async function documentId(page: Page, title: string): Promise<string> {
  const response = await page.request.get('/api/v1/library/documents')
  expect(response.ok()).toBeTruthy()
  const found = ((await response.json()) as { id: string; title: string }[]).find((doc) => doc.title === title)
  expect(found).toBeTruthy()
  return found!.id
}

interface Chunk {
  location: { kind: string; number: number; end: number }
  heading?: string
  content: string
  source: string
}

async function chunks(page: Page, id: string): Promise<Chunk[]> {
  const response = await page.request.get(`/api/v1/library/documents/${id}/chunks`)
  expect(response.ok()).toBeTruthy()
  return response.json()
}

async function otherLearner(browser: Browser, dev: DevApi) {
  const context = await browser.newContext()
  const page = await context.newPage()
  await createVerifiedAccount(page, dev, uniqueEmail('xu-ly-la'))
  return page
}

test.beforeEach(async ({ dev }) => {
  await dev.resetQuota()
  await dev.resetAiGateway()
  await dev.setDocumentProcessing(true)
})

test.afterEach(async ({ dev }) => {
  await dev.failNextOcr(0)
})

test.describe('NCL-05-CN-003 · Trích văn bản, nhận dạng chữ, chia đoạn và lập chỉ mục', () => {
  test('TC-01: bài giảng năm mươi trang sẵn sàng trong sáu mươi giây, mỗi đoạn gắn số trang, có thông báo', async ({
    page,
    dev,
  }) => {
    test.setTimeout(120_000)
    await openFolder(page, dev, 'xu-ly-50-trang')
    const lecture = await dev.sampleFile('lecture.pdf', { pages: 50, scannedPage: 12 })
    const started = Date.now()
    await addFiles(page, [{ name: 'Giải tích 1.pdf', mimeType: 'application/pdf', buffer: lecture }])

    const item = documentItem(page, 'Giải tích 1')
    await expect(item).toBeVisible()
    // Sẵn sàng thì thẻ không còn nhãn trạng thái; danh sách tự cập nhật, không cần tải lại trang.
    await expect(item.getByTestId('document-status')).toHaveCount(0, { timeout: 60_000 })
    expect(Date.now() - started).toBeLessThan(60_000)
    await expectNoSeriousA11yViolations(page)

    // Mỗi đoạn gắn số trang; trang quét 12 đi qua nhận dạng chữ.
    const id = await documentId(page, 'Giải tích 1')
    const passages = await chunks(page, id)
    expect(new Set(passages.map((chunk) => chunk.location.kind))).toEqual(new Set(['PAGE']))
    expect(new Set(passages.map((chunk) => chunk.location.number)).size).toBe(50)
    expect(passages.filter((chunk) => chunk.location.number === 12).map((chunk) => chunk.source)).toEqual(['OCR'])

    // Người học nhận thông báo; bấm thông báo mở trang tài liệu với tóm tắt kết quả xử lý.
    const bell = page.getByTestId('notification-bell').first()
    await expect(page.getByTestId('notification-badge').first()).toHaveText('1')
    await bell.click()
    const notification = page.getByTestId('notification-item').filter({ hasText: 'Tài liệu đã sẵn sàng' })
    await expect(notification).toContainText('“Giải tích 1” đã được trích văn bản và lập chỉ mục')
    await notification.click()
    await expect(page).toHaveURL(new RegExp(`/library/${id}$`))
    await expect(page.getByTestId('processing-summary')).toHaveText(`50 trang · ${passages.length} đoạn đã lập chỉ mục`)
    await expect(page.getByTestId('document-content')).toContainText('Chuong 50')
    await expectNoSeriousA11yViolations(page)
  })

  test('TC-02: hai ảnh chụp vở: văn bản chia đoạn theo từng ảnh, mỗi đoạn trỏ về đúng ảnh gốc', async ({ page, dev }) => {
    await openFolder(page, dev, 'xu-ly-anh-vo')
    const first = await dev.sampleFile('notebook.png', { text: '# Đạo hàm\n\nĐạo hàm của x bình phương là 2x.' })
    const second = await dev.sampleFile('notebook.png', { text: '# Tích phân\n\nTích phân là phép toán ngược của đạo hàm.' })
    await addFiles(page, [
      { name: 'vo-trang-1.png', mimeType: 'image/png', buffer: first },
      { name: 'vo-trang-2.png', mimeType: 'image/png', buffer: second },
    ])

    for (const title of ['vo-trang-1', 'vo-trang-2']) {
      await expect(documentItem(page, title)).toBeVisible()
      await expect(documentItem(page, title).getByTestId('document-status')).toHaveCount(0, { timeout: 30_000 })
    }
    const one = await chunks(page, await documentId(page, 'vo-trang-1'))
    const two = await chunks(page, await documentId(page, 'vo-trang-2'))
    for (const passages of [one, two]) {
      expect(passages.length).toBeGreaterThan(0)
      expect(passages.every((chunk) => chunk.location.kind === 'IMAGE' && chunk.source === 'OCR')).toBe(true)
    }
    expect(one.map((chunk) => chunk.content).join(' ')).toContain('2x')
    expect(one.every((chunk) => chunk.heading === 'Đạo hàm')).toBe(true)
    expect(two.map((chunk) => chunk.content).join(' ')).toContain('phép toán ngược')
    expect(two.map((chunk) => chunk.content).join(' ')).not.toContain('2x')

    await page.getByTestId('document-card').filter({ hasText: 'vo-trang-1' }).click()
    await expect(page.getByTestId('processing-summary')).toHaveText(/^1 ảnh · \d+ đoạn đã lập chỉ mục$/)
  })

  test('TC-03: nhận dạng chữ lỗi ở trang 12: báo lỗi kèm lý do và nút thử lại; thử lại thì sẵn sàng', async ({
    page,
    dev,
  }) => {
    test.setTimeout(120_000)
    await openFolder(page, dev, 'xu-ly-loi-trang')
    await dev.failNextOcr(-1)
    const lecture = await dev.sampleFile('lecture.pdf', { pages: 20, scannedPage: 12 })
    await addFiles(page, [{ name: 'Bài giảng quét.pdf', mimeType: 'application/pdf', buffer: lecture }])

    const item = documentItem(page, 'Bài giảng quét')
    await expect(item.getByTestId('document-status')).toHaveText('Xử lý lỗi', { timeout: 60_000 })
    await expect(item.getByTestId('processing-error')).toContainText(
      'Dịch vụ nhận dạng chữ gặp lỗi ở trang 12. Hãy thử lại sau ít phút.',
    )
    await expectNoSeriousA11yViolations(page)

    await dev.failNextOcr(0)
    await item.getByRole('button', { name: 'Thử lại xử lý “Bài giảng quét”' }).click()
    await expect(page.getByText('Đã đưa “Bài giảng quét” vào hàng chờ xử lý lại.')).toBeVisible()
    await expect(item.getByTestId('processing-error')).toHaveCount(0)
    await expect(item.getByTestId('document-status')).toHaveCount(0, { timeout: 60_000 })
  })

  test('TC-04: tệp chỉ có trang trắng: báo không trích được văn bản và gợi ý tải bản rõ hơn', async ({ page, dev }) => {
    await openFolder(page, dev, 'xu-ly-trang-trang')
    const blank = await dev.sampleFile('blank.pdf', { pages: 3 })
    await addFiles(page, [{ name: 'trang-trang.pdf', mimeType: 'application/pdf', buffer: blank }])

    const item = documentItem(page, 'trang-trang')
    await expect(item).toBeVisible()
    await expect(item.getByTestId('document-status')).toHaveText('Xử lý lỗi', { timeout: 30_000 })
    await expect(item.getByTestId('processing-error')).toHaveText(
      'Không trích được văn bản nào từ tài liệu này. Hãy tải lên bản rõ hơn hoặc bản có chữ chọn được.',
    )
    await expect(item.getByTestId('processing-retry')).toHaveCount(0)

    await page.getByTestId('document-card').filter({ hasText: 'trang-trang' }).click()
    const state = page.getByTestId('processing-state')
    await expect(state).toContainText('Chưa xử lý được tài liệu này')
    await expect(state).toContainText('Hãy tải lên bản khác của tài liệu.')
    await expectNoSeriousA11yViolations(page)
  })

  test('TC-05: người học khác biết mã tài liệu: lấy đoạn văn trả về không tìm thấy', async ({ page, dev, browser }) => {
    await openFolder(page, dev, 'xu-ly-chu')
    const lecture = await dev.sampleFile('lecture.pdf', { pages: 2 })
    await addFiles(page, [{ name: 'Riêng tư.pdf', mimeType: 'application/pdf', buffer: lecture }])
    await expect(documentItem(page, 'Riêng tư')).toBeVisible()
    await expect(documentItem(page, 'Riêng tư').getByTestId('document-status')).toHaveCount(0, { timeout: 30_000 })
    const id = await documentId(page, 'Riêng tư')

    const stranger = await otherLearner(browser, dev)
    const denied = await stranger.request.get(`/api/v1/library/documents/${id}/chunks`)
    expect(denied.status()).toBe(404)
    expect((await denied.json()).code).toBe('NOT_FOUND')
    await stranger.goto('/library')
    const xsrf = (await stranger.context().cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')?.value ?? ''
    const retry = await stranger.request.post(`/api/v1/library/documents/${id}/reprocess`, {
      headers: { 'X-XSRF-TOKEN': xsrf },
    })
    expect(retry.status()).toBe(404)
    expect((await chunks(page, id)).length).toBeGreaterThan(0)
    await stranger.context().close()
  })
})
