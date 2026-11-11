import AxeBuilder from '@axe-core/playwright'
import type { Page } from '@playwright/test'
import { acknowledgeAiDisclosure, createVerifiedAccount, expect, test, type DevApi } from './fixtures/auth'
import { uniqueEmail } from './fixtures/testData'

/**
 * NCL-02-CN-003 — Hiển thị công thức toán, mã nguồn và bảng trong nội dung (Jira LC-38): câu trả lời của trợ lý hiển
 * thị qua bộ hiển thị dùng chung; bảng rộng cuộn trong khung trên điện thoại, mã độc bị loại, công thức lỗi không làm
 * vỡ trang.
 */

const COLUMNS = ['Thuật toán', 'Tốt nhất', 'Trung bình', 'Xấu nhất', 'Bộ nhớ', 'Ổn định', 'Tại chỗ', 'Ghi chú']

const REPLY = [
  'Tích phân của $x^2$ trên đoạn từ 0 tới 1:',
  '',
  '$$',
  '\\int_0^1 x^2 \\, dx = \\frac{1}{3}',
  '$$',
  '',
  '```python',
  'def area(n):',
  '    return sum((i / n) ** 2 for i in range(n)) / n',
  '```',
  '',
  `| ${COLUMNS.join(' | ')} |`,
  `|${' --- |'.repeat(COLUMNS.length)}`,
  '| Sắp xếp nhanh | n log n | n log n | n² | log n | Không | Có | Chọn chốt ngẫu nhiên để tránh trường hợp xấu |',
  '| Sắp xếp trộn | n log n | n log n | n log n | n | Có | Không | Hợp với danh sách liên kết và dữ liệu lớn |',
  '',
  '<script>window.__hacked = true</script>',
  '',
  'Công thức lỗi $$\\frac{1}{2$$ không làm vỡ phần còn lại.',
  '',
  '- Ý chính',
  '  - Ý phụ',
].join('\n')

const TREE = 'Cây nhị phân là cấu trúc dữ liệu mà mỗi nút có tối đa hai con. Tích phân và sắp xếp được nhắc tới ở đây.'

const isMobile = (page: Page) => (page.viewportSize()?.width ?? 1440) < 768

async function xsrf(page: Page): Promise<Record<string, string>> {
  const token = (await page.context().cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')?.value
  return token ? { 'X-XSRF-TOKEN': token } : {}
}

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

/** Hỏi một câu trên một tài liệu đã xử lý xong; bản mô phỏng trả lời bằng nội dung có định dạng ở trên. */
async function formattedAnswer(page: Page, dev: DevApi) {
  await createVerifiedAccount(page, dev, uniqueEmail('rich'))
  await acknowledgeAiDisclosure(page)
  await page.goto('/library')
  await expect(page.getByTestId('library-main')).toBeVisible()
  const created = await page.request.post('/api/v1/library/sources/text', {
    data: { title: 'Ghi chép giải tích', text: TREE, folderId: null },
    headers: await xsrf(page),
  })
  const id = ((await created.json()) as { id: string }).id
  await expect
    .poll(
      async () =>
        ((await (await page.request.get(`/api/v1/library/documents/${id}?track=false`)).json()) as { status: string })
          .status,
      { timeout: 60_000 },
    )
    .toBe('READY')
  await dev.setAiMockReply(REPLY)

  await page.goto('/chat')
  await page.getByTestId('composer-attach').click()
  const picker = page.getByRole('dialog', { name: 'Đính kèm tài liệu' })
  await picker.getByTestId('picker-option').filter({ hasText: 'Ghi chép giải tích' }).getByRole('checkbox').check()
  await picker.getByTestId('picker-apply').click()
  await page.getByTestId('chat-input').fill('Tích phân và sắp xếp trong cây nhị phân?')
  await page.getByTestId('chat-send').click()
  const answer = page.getByTestId('assistant-message').last()
  await expect(answer).toHaveAttribute('data-status', 'COMPLETE', { timeout: 30_000 })
  return answer
}

test.beforeEach(async ({ dev }) => {
  await dev.resetQuota()
  await dev.resetAiGateway()
  await dev.resetAiConfigs()
  await dev.setDocumentProcessing(true)
})

test.afterEach(async ({ dev }) => {
  await dev.resetAiGateway()
})

test.describe('NCL-02-CN-003 · Hiển thị công thức toán, mã nguồn và bảng', () => {
  test('TC-01..04: công thức, khối mã sao chép được, bảng tám cột cuộn trong khung, mã độc bị loại, công thức lỗi không làm vỡ trang', async ({
    page,
    dev,
    context,
  }, info) => {
    test.setTimeout(120_000)
    await context.grantPermissions(['clipboard-read', 'clipboard-write'])
    const answer = await formattedAnswer(page, dev)

    // TC-01: ký hiệu toán và mã có tô màu, sao chép nguyên văn.
    await expect(answer.getByTestId('math-display').locator('.katex-html')).toContainText('∫')
    await expect(answer.getByTestId('math-inline').first().locator('.katex')).toBeVisible()
    const code = answer.getByTestId('code-block')
    await expect(code.locator('.hljs-keyword').first()).toHaveText('def')
    await code.getByTestId('code-copy').click()
    await expect(code.getByTestId('code-copy')).toHaveText(/Đã chép/)
    if (!isMobile(page)) {
      expect(await page.evaluate(() => navigator.clipboard.readText())).toContain('def area(n):')
    }

    // TC-02: bảng tám cột cuộn trong khung; trang không cuộn ngang.
    const table = answer.getByRole('region', { name: 'Bảng, cuộn ngang để xem đủ các cột' })
    await expect(table.getByRole('columnheader')).toHaveCount(8)
    const widths = await table.evaluate((element) => ({ scroll: element.scrollWidth, client: element.clientWidth }))
    const page_ = await page.evaluate(() => ({
      scroll: document.documentElement.scrollWidth,
      client: document.documentElement.clientWidth,
    }))
    expect(page_.scroll).toBeLessThanOrEqual(page_.client)
    if (isMobile(page)) expect(widths.scroll).toBeGreaterThan(widths.client)

    // TC-03: thẻ chạy mã bị loại, không chạy.
    expect(await answer.locator('script').count()).toBe(0)
    expect(await page.evaluate(() => (window as unknown as { __hacked?: boolean }).__hacked)).toBeUndefined()
    await expect(answer.getByTestId('answer-text')).not.toContainText('__hacked')

    // TC-04: công thức lỗi hiện nguyên văn kèm cảnh báo; phần sau vẫn hiển thị.
    await expect(answer.getByTestId('math-error')).toContainText('\\frac{1}{2')
    await expect(answer.getByText('không làm vỡ phần còn lại.')).toBeVisible()
    await expect(answer.locator('ul ul li')).toHaveText('Ý phụ')

    await expectNoSeriousA11yViolations(page)
    await table.scrollIntoViewIfNeeded()
    await page.screenshot({ path: info.outputPath('rich-answer.png'), fullPage: true })

    // Mở lại hội thoại: nội dung đã lưu cũng hiển thị qua bộ hiển thị.
    await page.reload()
    await expect(page.getByTestId('assistant-message').last().getByTestId('code-block')).toBeVisible()
  })
})
