import AxeBuilder from '@axe-core/playwright'
import type { Browser, Locator, Page } from '@playwright/test'
import { createVerifiedAccount, expect, test, type DevApi } from './fixtures/auth'
import { uniqueEmail } from './fixtures/testData'

/**
 * NCL-05-CN-004 — Thêm tài liệu từ trang web và văn bản dán (Jira LC-32). Trang web lấy qua bản mô phỏng của lớp kết
 * nối (QTN-04); phần chống SSRF và luồng trích văn bản, chia đoạn, lập chỉ mục chạy như môi trường thật.
 */

const ARTICLE = 'https://bai-viet.example/thuat-toan-sap-xep'
const LOGIN_WALL = 'https://thanh-vien.example/bai-viet-rieng'
const ARTICLE_TITLE = 'Thuật toán sắp xếp: nổi bọt, chèn và nhanh'

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

/** Đăng ký người học có sẵn thư mục "Cấu trúc dữ liệu" và mở thư mục đó. */
async function openFolder(page: Page, dev: DevApi, tag: string) {
  const email = uniqueEmail(tag)
  await createVerifiedAccount(page, dev, email)
  const seeded = await dev.seedLibrary(email, { folders: ['Cấu trúc dữ liệu'] })
  const folderId = seeded.folders['Cấu trúc dữ liệu']
  await page.goto(`/library?folder=${folderId}`)
  await expect(page.getByTestId('library-view-title')).toHaveText('Cấu trúc dữ liệu')
  return folderId
}

function documentItem(page: Page, title: string): Locator {
  return page.getByTestId('document-item').filter({ hasText: title })
}

async function documents(page: Page): Promise<{ id: string; title: string; type: string; sourceUrl?: string }[]> {
  const response = await page.request.get('/api/v1/library/documents')
  expect(response.ok()).toBeTruthy()
  return response.json()
}

async function chunks(page: Page, id: string): Promise<{ content: string; location: { kind: string; number: number } }[]> {
  const response = await page.request.get(`/api/v1/library/documents/${id}/chunks`)
  expect(response.ok()).toBeTruthy()
  return response.json()
}

async function otherLearner(browser: Browser, dev: DevApi) {
  const context = await browser.newContext()
  const page = await context.newPage()
  await createVerifiedAccount(page, dev, uniqueEmail('trang-web-la'))
  return page
}

test.beforeEach(async ({ dev }) => {
  await dev.resetQuota()
  await dev.setDocumentProcessing(true)
})

test.describe('NCL-05-CN-004 · Thêm tài liệu từ trang web và văn bản dán', () => {
  test('TC-01: dán đường dẫn bài viết vào thư mục: tiêu đề bài, nội dung chính không quảng cáo, chuyển sang sẵn sàng', async ({
    page,
    dev,
  }, testInfo) => {
    test.setTimeout(90_000)
    const folderId = await openFolder(page, dev, 'trang-web-bai-viet')

    await page.getByTestId('add-from-web').click()
    const dialog = page.getByTestId('add-source-dialog')
    await expect(dialog).toBeVisible()
    await expect(page.getByTestId('add-source-tab-web')).toHaveAttribute('aria-selected', 'true')
    await expect(page.getByTestId('web-url')).toBeFocused()
    await page.getByTestId('web-url').fill(ARTICLE)
    await expectNoSeriousA11yViolations(page)
    await page.screenshot({ path: testInfo.outputPath('add-web-dialog.png') })
    await page.getByTestId('web-submit').click()

    await expect(dialog).toBeHidden()
    const item = documentItem(page, ARTICLE_TITLE)
    await expect(item).toBeVisible()
    await expect(item.getByTestId('document-status')).toHaveCount(0, { timeout: 60_000 })

    const created = (await documents(page)).find((doc) => doc.title === ARTICLE_TITLE)!
    expect(created.type).toBe('WEB')
    expect(created.sourceUrl).toBe(ARTICLE)
    const text = (await chunks(page, created.id)).map((chunk) => chunk.content).join('\n')
    expect(text).toContain('Sắp xếp nổi bọt')
    expect(text).toContain('O(n log n)')
    for (const noise of ['Quảng cáo', 'Tài trợ', 'Trang chủ', 'Bài liên quan', 'Bản quyền']) expect(text).not.toContain(noise)

    // Trang tài liệu hiển thị nội dung chính đã lưu.
    await page.goto(`/library/${created.id}`)
    await expect(page.getByTestId('document-content')).toContainText('Sắp xếp nhanh chọn một phần tử chốt')
    await expect(page.getByTestId('document-content')).not.toContainText('Quảng cáo')
    await page.goto(`/library?folder=${folderId}`)
    await expect(documentItem(page, ARTICLE_TITLE)).toBeVisible()
    await page.screenshot({ path: testInfo.outputPath('library-with-web-document.png'), fullPage: true })
  })

  test('TC-02: dán ghi chép ba nghìn chữ kèm tiêu đề: tài liệu văn bản chia đoạn đúng thứ tự', async ({ page, dev }, testInfo) => {
    test.setTimeout(90_000)
    await openFolder(page, dev, 'trang-web-van-ban')
    const paragraphs = Array.from({ length: 30 }, (_, i) => `Đoạn ${i + 1}. ${'ghi chép bài giảng cấu trúc dữ liệu '.repeat(20).trim()}`)
    const text = paragraphs.join('\n\n')
    expect(text.split(/\s+/).length).toBeGreaterThanOrEqual(3000)

    await page.getByTestId('add-from-text').click()
    await expect(page.getByTestId('add-source-tab-text')).toHaveAttribute('aria-selected', 'true')
    await page.getByTestId('paste-title').fill('Ghi chép bài giảng tuần 3')
    await page.getByTestId('paste-text').fill(text)
    await expect(page.getByTestId('paste-count')).toContainText('4.860 chữ')
    await expectNoSeriousA11yViolations(page)
    await page.screenshot({ path: testInfo.outputPath('paste-text-dialog.png') })
    await page.getByTestId('paste-submit').click()

    const item = documentItem(page, 'Ghi chép bài giảng tuần 3')
    await expect(item).toBeVisible()
    await expect(item.getByTestId('document-status')).toHaveCount(0, { timeout: 60_000 })
    const created = (await documents(page)).find((doc) => doc.title === 'Ghi chép bài giảng tuần 3')!
    expect(created.type).toBe('TEXT')
    const joined = (await chunks(page, created.id)).map((chunk) => chunk.content).join('\n')
    let last = -1
    for (let i = 1; i <= 30; i++) {
      const at = joined.indexOf(`Đoạn ${i}.`)
      expect(at, `đoạn ${i} đúng thứ tự`).toBeGreaterThan(last)
      last = at
    }
  })

  test('TC-03: đường dẫn mạng nội bộ bị từ chối, không tạo tài liệu', async ({ page, dev }, testInfo) => {
    await openFolder(page, dev, 'trang-web-noi-bo')
    await page.getByTestId('add-from-web').click()
    for (const url of ['http://192.168.1.1/admin', 'http://localhost:8080/actuator', 'http://169.254.169.254/latest/meta-data/']) {
      await page.getByTestId('web-url').fill(url)
      await page.getByTestId('web-submit').click()
      const problem = page.getByTestId('web-problem')
      await expect(problem).toHaveAttribute('data-kind', 'blocked')
      await expect(problem).toContainText('mạng nội bộ')
    }
    await expectNoSeriousA11yViolations(page)
    await page.screenshot({ path: testInfo.outputPath('web-blocked.png') })
    expect(await documents(page)).toEqual([])
  })

  test('TC-04: trang yêu cầu đăng nhập: báo không lấy được và gợi ý dán văn bản thay thế', async ({ page, dev }, testInfo) => {
    await openFolder(page, dev, 'trang-web-dang-nhap')
    await page.getByTestId('add-from-web').click()
    await page.getByTestId('web-url').fill(LOGIN_WALL)
    await page.getByTestId('web-submit').click()
    await expect(page.getByTestId('web-problem')).toContainText('yêu cầu đăng nhập')
    await expectNoSeriousA11yViolations(page)
    await page.screenshot({ path: testInfo.outputPath('web-login-required.png') })

    await page.getByTestId('paste-instead').click()
    await expect(page.getByTestId('add-source-tab-text')).toHaveAttribute('aria-selected', 'true')
    await expect(page.getByTestId('paste-title')).toBeFocused()
    await page.getByTestId('paste-title').fill('Bài viết thành viên')
    await page.getByTestId('paste-text').fill('Phần nội dung tôi tự sao chép từ trang.')
    await page.getByTestId('paste-submit').click()
    await expect(documentItem(page, 'Bài viết thành viên')).toBeVisible()
  })

  test('TC-05: thêm tài liệu vào thư mục của người khác: không tìm thấy', async ({ page, dev, browser }) => {
    const folderId = await openFolder(page, dev, 'trang-web-chu')
    const stranger = await otherLearner(browser, dev)
    const xsrf = (await stranger.context().cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')?.value ?? ''
    for (const [path, data] of [
      ['/api/v1/library/sources/web', { url: ARTICLE, folderId }],
      ['/api/v1/library/sources/text', { title: 'Chen vao', text: 'Nội dung', folderId }],
    ] as const) {
      const response = await stranger.request.post(path, { data, headers: { 'X-XSRF-TOKEN': xsrf } })
      expect(response.status()).toBe(404)
    }
    expect(await documents(stranger)).toEqual([])
    expect(await documents(page)).toEqual([])
    await stranger.context().close()
  })

  test('Bàn phím: mở hộp thoại, chuyển thẻ bằng phím mũi tên, Esc đóng và trả tiêu điểm', async ({ page, dev }) => {
    await openFolder(page, dev, 'trang-web-ban-phim')
    const opener = page.getByTestId('add-from-web')
    await opener.focus()
    await page.keyboard.press('Enter')
    await expect(page.getByTestId('web-url')).toBeFocused()
    await page.getByTestId('add-source-tab-web').focus()
    await page.keyboard.press('ArrowRight')
    await expect(page.getByTestId('add-source-tab-text')).toBeFocused()
    await expect(page.getByTestId('paste-text-form')).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(page.getByTestId('add-source-dialog')).toBeHidden()
    await expect(opener).toBeFocused()
  })
})
