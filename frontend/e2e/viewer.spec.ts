import AxeBuilder from '@axe-core/playwright'
import type { Browser, Page } from '@playwright/test'
import { createVerifiedAccount, expect, test, type DevApi } from './fixtures/auth'
import { uniqueEmail } from './fixtures/testData'

async function accessible(page: Page) {
  const result = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .exclude('a[href="/dev/mailbox"]').analyze()
  expect(result.violations.filter((violation) => ['serious', 'critical'].includes(violation.impact ?? ''))
    .map((violation) => violation.id)).toEqual([])
}

async function readyDocument(page: Page, dev: DevApi) {
  const email = uniqueEmail('viewer')
  await createVerifiedAccount(page, dev, email)
  await page.goto('/library')
  const paragraphs = [
    `Đạo hàm riêng bậc một. ${'Giải thích về biến số và quy tắc tính toán. '.repeat(27)}`,
    `Phần ví dụ. ${'Giải thích về tích phân và công thức. '.repeat(30)}`,
    `Đạo hàm riêng bậc hai. ${'Giải thích về đạo hàm riêng và ứng dụng. '.repeat(28)}`,
  ]
  await page.getByTestId('add-from-text').click()
  await page.getByTestId('paste-title').fill('Giáo trình giải tích')
  await page.getByTestId('paste-text').fill(paragraphs.join('\n\n'))
  await page.getByTestId('paste-submit').click()
  const card = page.getByTestId('document-item').filter({ hasText: 'Giáo trình giải tích' })
  await expect(card).toBeVisible()
  await expect(card.getByTestId('document-status')).toHaveCount(0, { timeout: 60_000 })
  let id: string | undefined
  await expect.poll(async () => {
    const response = await page.request.get('/api/v1/library/documents')
    if (!response.ok()) return false
    id = ((await response.json()) as { id: string; title: string }[]).find((item) => item.title === 'Giáo trình giải tích')?.id
    return Boolean(id)
  }).toBe(true)
  return id!
}

test.beforeEach(async ({ dev }) => { await dev.resetQuota(); await dev.setDocumentProcessing(true) })

test('TC-01/02: mở đoạn theo địa chỉ trang, tìm chữ, chuyển kết quả và xem toàn màn hình', async ({ page, dev }, info) => {
  test.setTimeout(120_000)
  const id = await readyDocument(page, dev)
  await page.goto(`/library?viewer=${id}&kind=PARAGRAPH&number=3&passage=1`)
  const panel = page.getByTestId('side-panel')
  await expect(panel).toBeVisible()
  await expect(panel.getByTestId('document-viewer')).toBeVisible()
  await expect(panel.getByTestId('viewer-page')).toHaveValue('3')
  await expect(panel.getByTestId('viewer-passage').filter({ hasText: 'bậc hai' })).toHaveAttribute('data-highlighted', 'true')
  await page.screenshot({ path: info.outputPath('viewer-passage.png') })
  await panel.getByTestId('viewer-search').fill('đạo hàm riêng')
  await expect(panel.getByRole('search').getByText(/1 \/ \d+/)).toBeVisible()
  await expect(panel.locator('mark').first()).toBeVisible()
  await panel.getByTestId('viewer-next-result').click()
  await expect(panel.getByRole('search').getByText(/2 \/ \d+/)).toBeVisible()
  if (info.project.name === 'desktop-chrome') {
    await panel.getByTestId('side-panel-fullscreen').click()
    await expect(panel).toHaveAttribute('data-fullscreen', 'true')
  } else {
    await expect(panel).toHaveAttribute('data-fullscreen', 'true')
  }
  await expect(panel.getByTestId('viewer-search')).toHaveValue('đạo hàm riêng')
  await expect(panel.getByRole('search').getByText(/2 \/ \d+/)).toBeVisible()
  if (info.project.name === 'mobile-chrome') {
    await panel.getByTestId('viewer-page').fill('1')
    await panel.getByTestId('document-viewer').evaluate((element) => {
      const left = new Touch({ identifier: 1, target: element, clientX: 300, clientY: 450 })
      const right = new Touch({ identifier: 1, target: element, clientX: 100, clientY: 450 })
      element.dispatchEvent(new TouchEvent('touchstart', { bubbles: true, touches: [left] }))
      element.dispatchEvent(new TouchEvent('touchend', { bubbles: true, changedTouches: [right] }))
    })
    await expect(panel.getByTestId('viewer-page')).toHaveValue('2')
  }
  await accessible(page)
  await page.screenshot({ path: info.outputPath('viewer-search.png') })
  await page.goto('/library?view=recent')
  await expect(page.getByTestId('document-card').first()).toContainText('Giáo trình giải tích')
})

test('PDF giữ bố cục tệp gốc và mở đúng trang 12', async ({ page, dev }, info) => {
  test.setTimeout(120_000)
  const email = uniqueEmail('viewer-pdf')
  await createVerifiedAccount(page, dev, email)
  const pdf = await dev.sampleFile('lecture.pdf', { pages: 12 })
  await page.goto('/library')
  await page.getByTestId('upload-input').setInputFiles({ name: 'giao-trinh-12.pdf', mimeType: 'application/pdf', buffer: pdf })
  const card = page.getByTestId('document-item').filter({ hasText: 'giao-trinh-12' })
  await expect(card).toBeVisible()
  await expect(card.getByTestId('document-status')).toHaveCount(0, { timeout: 60_000 })
  const response = await page.request.get('/api/v1/library/documents')
  const id = ((await response.json()) as { id: string; title: string }[]).find((item) => item.title === 'giao-trinh-12')!.id
  await page.goto(`/library?viewer=${id}&kind=PAGE&number=12`)
  const panel = page.getByTestId('side-panel')
  await expect(panel.getByTestId('viewer-page')).toHaveValue('12')
  await expect(panel.getByTestId('pdf-page')).toHaveAttribute('data-rendered', 'true')
  expect(await panel.getByTestId('pdf-page').evaluate((canvas: HTMLCanvasElement) => canvas.width)).toBeGreaterThan(300)
  await expect(panel.getByTestId('viewer-passage').first()).toContainText('Chuong 12')
  await accessible(page)
  await page.screenshot({ path: info.outputPath('viewer-pdf-page12.png') })
})

test('TC-03: tài liệu đang xử lý hiện phần trăm, không hiện nội dung', async ({ page, dev }) => {
  const email = uniqueEmail('viewer-processing')
  await createVerifiedAccount(page, dev, email)
  const seeded = await dev.seedLibrary(email, { documents: [{ title: 'Bản đang xử lý', type: 'TEXT', status: 'PROCESSING', text: 'Nội dung riêng tư' }] })
  await page.goto(`/library?viewer=${seeded.documents[0]}`)
  await expect(page.getByTestId('viewer-processing')).toContainText('Đã xử lý')
  await expect(page.getByTestId('viewer-processing').locator('progress')).toBeVisible()
  await expect(page.getByTestId('document-viewer')).toHaveCount(0)
  await accessible(page)
})

test('TC-04: người học khác mở viewer nhận 404', async ({ page, dev, browser }) => {
  const email = uniqueEmail('viewer-owner')
  await createVerifiedAccount(page, dev, email)
  const id = await dev.sampleDocument(email, 'Tài liệu riêng')
  const other = await otherLearner(browser, dev)
  const denied = await other.request.get(`/api/v1/library/documents/${id}/viewer`)
  expect(denied.status()).toBe(404)
  await other.goto(`/library?viewer=${id}`)
  await expect(other.getByText('Không tìm thấy tài liệu')).toBeVisible()
  await other.context().close()
})

async function otherLearner(browser: Browser, dev: DevApi) {
  const context = await browser.newContext()
  const page = await context.newPage()
  await createVerifiedAccount(page, dev, uniqueEmail('viewer-stranger'))
  return page
}
