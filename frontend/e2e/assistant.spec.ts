import AxeBuilder from '@axe-core/playwright'
import type { Browser, Page } from '@playwright/test'
import { createVerifiedAccount, expect, test, type DevApi } from './fixtures/auth'
import { uniqueEmail } from './fixtures/testData'

/**
 * NCL-07-CN-001 — Hỏi đáp có căn cứ trên tài liệu đã chọn (Jira LC-35). Trợ lý chạy qua bản mô phỏng của lớp kết nối:
 * câu trả lời lấy câu khớp nhất của đoạn nguồn và ghi số trích dẫn, nên kết quả ổn định.
 */

const TREE = [
  'Cây nhị phân là cấu trúc dữ liệu mà mỗi nút có tối đa hai con.',
  'Duyệt cây theo thứ tự giữa thăm cây con trái, rồi tới nút gốc, rồi tới cây con phải. Với cây nhị phân tìm kiếm, duyệt theo thứ tự giữa cho ra các khóa theo thứ tự tăng dần.',
  'Duyệt theo thứ tự trước thăm nút gốc trước rồi mới tới hai cây con.',
].join('\n\n')
const CHAPTER_ONE = 'Chương một: Giới hạn của dãy số.\n\nMột dãy số hội tụ khi các số hạng tiến dần về một giá trị gọi là giới hạn.'
const CHAPTER_TWO = 'Chương hai: Đạo hàm.\n\nĐạo hàm riêng của hàm nhiều biến là đạo hàm theo một biến khi giữ các biến còn lại cố định.'

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

async function xsrf(page: Page): Promise<Record<string, string>> {
  const token = (await page.context().cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')?.value
  return token ? { 'X-XSRF-TOKEN': token } : {}
}

/** Thêm tài liệu văn bản dán vào kho qua API của chính người học và chờ xử lý xong. */
async function pasteDocument(page: Page, title: string, text: string) {
  const response = await page.request.post('/api/v1/library/sources/text', {
    data: { title, text, folderId: null },
    headers: await xsrf(page),
  })
  expect(response.status()).toBe(201)
  const id = ((await response.json()) as { id: string }).id
  await expect
    .poll(
      async () =>
        ((await (await page.request.get(`/api/v1/library/documents/${id}?track=false`)).json()) as { status: string })
          .status,
      { timeout: 60_000 },
    )
    .toBe('READY')
  return id
}

async function signUp(page: Page, dev: DevApi, tag: string) {
  const email = uniqueEmail(tag)
  await createVerifiedAccount(page, dev, email)
  // Mở một trang của ứng dụng để có cookie chống giả mạo yêu cầu.
  await page.goto('/library')
  await expect(page.getByTestId('library-main')).toBeVisible()
  return email
}

async function createWorkspace(page: Page, name: string, documentIds: string[]) {
  await page.goto('/workspaces?new=1')
  const dialog = page.getByRole('dialog', { name: 'Tạo không gian học' })
  await dialog.getByTestId('workspace-name').fill(name)
  await dialog.getByTestId('workspace-save').click()
  await expect(page.getByTestId('workspace-title')).toHaveText(name)
  const id = page.url().split('/workspaces/')[1].split('?')[0]
  const attached = await page.request.post(`/api/v1/workspaces/${id}/documents`, {
    data: { documentIds },
    headers: await xsrf(page),
  })
  expect(attached.ok()).toBeTruthy()
  return id
}

async function ask(page: Page, question: string) {
  await page.getByTestId('chat-input').fill(question)
  await page.getByTestId('chat-send').click()
  const answer = page.getByTestId('assistant-message').last()
  await expect(answer).not.toHaveAttribute('data-status', 'STREAMING', { timeout: 30_000 })
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

test.describe('NCL-07-CN-001 · Hỏi đáp có căn cứ trên tài liệu đã chọn', () => {
  test('TC-01: hỏi trong không gian Cấu trúc dữ liệu, câu trả lời hiện dần có trích dẫn và ba câu hỏi gợi ý', async ({
    page,
    dev,
  }, info) => {
    test.setTimeout(120_000)
    await signUp(page, dev, 'hd-tc01')
    const lecture = await pasteDocument(page, 'Bài giảng cây nhị phân', TREE)
    const workspace = await createWorkspace(page, 'Cấu trúc dữ liệu', [lecture])

    await page.goto(`/workspaces/${workspace}?tab=assistant`)
    await expect(page.getByTestId('workspace-chat-greeting')).toBeVisible()
    await expect(page.getByTestId('source-scope-summary')).toHaveText('Đang dùng 1/1 tài liệu')
    await expectNoSeriousA11yViolations(page)

    const started = Date.now()
    await page.getByTestId('chat-input').fill('Cách duyệt cây theo thứ tự giữa là gì?')
    await page.getByTestId('chat-send').click()
    await expect(page.getByTestId('answer-text').last()).toContainText('Theo', { timeout: 3_000 })
    expect(Date.now() - started).toBeLessThan(3_000)
    const answer = page.getByTestId('assistant-message').last()
    await expect(answer).toHaveAttribute('data-status', 'COMPLETE', { timeout: 30_000 })
    await expect(answer.getByTestId('answer-text')).toContainText('thứ tự giữa')
    await expect(answer.getByTestId('citation-marker')).toHaveCount(1)
    await expect(answer.getByTestId('answer-sources')).toContainText('Bài giảng cây nhị phân')
    await expect(answer.getByTestId('suggested-question')).toHaveCount(3)
    await expect(page.getByTestId('workspace-conversations').getByRole('button')).toHaveCount(1)
    await expectNoSeriousA11yViolations(page)
    await page.screenshot({ path: info.outputPath('assistant-workspace-answer.png'), fullPage: true })

    // Số trích dẫn mở đúng đoạn ở khung xem bên.
    await answer.getByTestId('citation-marker').click()
    await expect(page).toHaveURL(/viewer=.*kind=PARAGRAPH/)
    await expect(page.getByTestId('side-panel')).toBeVisible()
    await expect(page.getByTestId('viewer-passage').filter({ hasText: 'thứ tự giữa' }).first()).toHaveAttribute(
      'data-highlighted',
      'true',
    )
    await page.screenshot({ path: info.outputPath('assistant-citation-viewer.png') })
  })

  test('TC-02: hội thoại mới, đính kèm hai tài liệu từ kho rồi hỏi; chỉ trích đúng hai tài liệu đó', async ({
    page,
    dev,
  }, info) => {
    test.setTimeout(120_000)
    await signUp(page, dev, 'hd-tc02')
    await pasteDocument(page, 'Bài giảng cây nhị phân', TREE)
    await pasteDocument(page, 'Giải tích chương hai', CHAPTER_TWO)
    await pasteDocument(page, 'Ghi chép không chọn', 'Đạo hàm riêng là chủ đề của tài liệu không được chọn.')

    await page.goto('/chat')
    await expect(page.getByTestId('chat-greeting')).toBeVisible()
    await expect(page.getByTestId('chat-input')).toBeFocused()
    await page.getByTestId('composer-attach').click()
    const picker = page.getByRole('dialog', { name: 'Đính kèm tài liệu' })
    await picker.getByTestId('picker-option').filter({ hasText: 'Bài giảng cây nhị phân' }).getByRole('checkbox').check()
    await picker.getByTestId('picker-search').fill('giai tich')
    await picker.getByTestId('picker-option').filter({ hasText: 'Giải tích chương hai' }).getByRole('checkbox').check()
    await expectNoSeriousA11yViolations(page)
    await picker.getByTestId('picker-apply').click()
    await expect(page.getByTestId('source-scope-summary')).toHaveText('Đang dùng 2/2 tài liệu')
    await expectNoSeriousA11yViolations(page)
    await page.screenshot({ path: info.outputPath('assistant-new-chat.png') })

    const answer = await ask(page, 'Đạo hàm riêng là gì?')
    await expect(answer).toHaveAttribute('data-status', 'COMPLETE')
    await expect(answer.getByTestId('answer-sources')).toContainText('Giải tích chương hai')
    await expect(answer.getByTestId('answer-sources')).not.toContainText('Ghi chép không chọn')
    await expect(page).toHaveURL(/\/chat\/[0-9a-f-]{36}$/)
    await page.screenshot({ path: info.outputPath('assistant-new-chat-answer.png'), fullPage: true })

    // Tải lại trang: hội thoại còn nguyên cùng phạm vi tài liệu.
    await page.reload()
    await expect(page.getByTestId('assistant-message')).toHaveAttribute('data-status', 'COMPLETE')
    await expect(page.getByTestId('source-scope-summary')).toHaveText('Đang dùng 2/2 tài liệu')
  })

  test('TC-03: tài liệu không nói về mạng nơ ron thì trả lời không tìm thấy, không tự bịa', async ({ page, dev }) => {
    test.setTimeout(120_000)
    await signUp(page, dev, 'hd-tc03')
    const lecture = await pasteDocument(page, 'Bài giảng cây nhị phân', TREE)
    const workspace = await createWorkspace(page, 'Cấu trúc dữ liệu', [lecture])
    await page.goto(`/workspaces/${workspace}?tab=assistant`)

    const answer = await ask(page, 'Mạng nơ ron hoạt động thế nào?')
    await expect(answer).toHaveAttribute('data-status', 'NOT_FOUND')
    await expect(answer.getByTestId('answer-not-found')).toContainText('Không tìm thấy thông tin này')
    await expect(answer.getByTestId('citation-marker')).toHaveCount(0)
    await expectNoSeriousA11yViolations(page)
  })

  test('TC-04: chỉ chọn chương một, hỏi nội dung chương hai thì không dùng chương hai và gợi ý chọn thêm', async ({
    page,
    dev,
  }) => {
    test.setTimeout(120_000)
    await signUp(page, dev, 'hd-tc04')
    const one = await pasteDocument(page, 'Giải tích chương một', CHAPTER_ONE)
    const two = await pasteDocument(page, 'Giải tích chương hai', CHAPTER_TWO)
    const workspace = await createWorkspace(page, 'Giải tích', [one, two])
    await page.goto(`/workspaces/${workspace}?tab=assistant`)
    await page.getByTestId('source-chip').filter({ hasText: 'Giải tích chương hai' }).getByRole('switch').click()
    await expect(page.getByTestId('source-scope-summary')).toHaveText('Đang dùng 1/2 tài liệu')

    const answer = await ask(page, 'Đạo hàm riêng là gì?')
    await expect(answer).toHaveAttribute('data-status', 'NOT_FOUND')
    await answer.getByRole('button', { name: 'Chọn thêm “Giải tích chương hai”' }).click()
    await expect(page.getByTestId('source-scope-summary')).toHaveText('Đang dùng 2/2 tài liệu')
    const again = await ask(page, 'Đạo hàm riêng là gì?')
    await expect(again).toHaveAttribute('data-status', 'COMPLETE')
    await expect(again.getByTestId('answer-sources')).toContainText('Giải tích chương hai')
  })

  test('TC-05: dịch vụ mô hình lỗi giữa chừng: giữ phần đã hiện, báo lỗi, thử lại được và lượt được hoàn', async ({
    page,
    dev,
  }, info) => {
    test.setTimeout(120_000)
    await signUp(page, dev, 'hd-tc05')
    const lecture = await pasteDocument(page, 'Bài giảng cây nhị phân', TREE)
    const workspace = await createWorkspace(page, 'Cấu trúc dữ liệu', [lecture])
    await page.goto(`/workspaces/${workspace}?tab=assistant`)
    await dev.setAiMockMode('mock-primary', 'BREAK_MIDWAY')

    const answer = await ask(page, 'Cách duyệt cây theo thứ tự giữa là gì?')
    await expect(answer).toHaveAttribute('data-status', 'FAILED')
    await expect(answer.getByTestId('answer-text')).toContainText('Theo tài liệu')
    await expect(answer.getByTestId('answer-failed')).toContainText('Lượt của câu hỏi này đã được hoàn')
    await expectNoSeriousA11yViolations(page)
    await page.screenshot({ path: info.outputPath('assistant-failed.png') })
    await expect
      .poll(async () => ((await (await page.request.get('/api/v1/quota')).json()) as { ai: { usedUnits: number } }).ai.usedUnits)
      .toBe(0)

    await dev.setAiMockMode('mock-primary', 'OK')
    await answer.getByTestId('answer-retry').click()
    await expect(page.getByTestId('assistant-message').last()).toHaveAttribute('data-status', 'COMPLETE', {
      timeout: 30_000,
    })
  })

  test('TC-06: người học khác gửi câu hỏi vào không gian học bằng mã thì không tìm thấy', async ({
    page,
    dev,
    browser,
  }) => {
    test.setTimeout(120_000)
    await signUp(page, dev, 'hd-owner')
    const lecture = await pasteDocument(page, 'Bài giảng cây nhị phân', TREE)
    const workspace = await createWorkspace(page, 'Cấu trúc dữ liệu', [lecture])
    await page.goto(`/workspaces/${workspace}?tab=assistant`)
    await ask(page, 'Cây nhị phân là gì?')
    await expect(page).toHaveURL(/conversation=/)
    const conversation = new URL(page.url()).searchParams.get('conversation')

    const other = await otherLearner(browser, dev)
    const denied = await other.request.post('/api/v1/assistant/ask', {
      data: { workspaceId: workspace, question: 'Cây nhị phân là gì?' },
      headers: await xsrf(other),
    })
    expect(denied.status()).toBe(404)
    await other.goto(`/chat/${conversation}`)
    await expect(other.getByRole('heading', { level: 1, name: 'Không tìm thấy hội thoại' })).toBeVisible()
    await other.context().close()
  })

  test('Dừng trả lời giữ phần đã hiện, rồi trả lời lại', async ({ page, dev }) => {
    test.setTimeout(120_000)
    await signUp(page, dev, 'hd-stop')
    const lecture = await pasteDocument(page, 'Bài giảng cây nhị phân', TREE)
    const workspace = await createWorkspace(page, 'Cấu trúc dữ liệu', [lecture])
    await page.goto(`/workspaces/${workspace}?tab=assistant`)
    await page.getByTestId('chat-input').fill('Cách duyệt cây theo thứ tự giữa là gì?')
    await page.getByTestId('chat-send').click()
    await expect(page.getByTestId('answer-text').last()).toContainText('Theo')
    await page.getByTestId('chat-stop').click()
    const answer = page.getByTestId('assistant-message').last()
    await expect(answer).toHaveAttribute('data-status', 'STOPPED', { timeout: 30_000 })
    await expect(answer.getByTestId('answer-stopped')).toBeVisible()
    await answer.getByTestId('answer-regenerate').click()
    await expect(page.getByTestId('assistant-message').last()).toHaveAttribute('data-status', 'COMPLETE', {
      timeout: 30_000,
    })
  })
})

async function otherLearner(browser: Browser, dev: DevApi) {
  const context = await browser.newContext()
  const page = await context.newPage()
  await createVerifiedAccount(page, dev, uniqueEmail('hd-stranger'))
  await page.goto('/library')
  return page
}
