import AxeBuilder from '@axe-core/playwright'
import type { Locator, Page } from '@playwright/test'
import { acknowledgeAiDisclosure, createVerifiedAccount, expect, test, type DevApi } from './fixtures/auth'
import { openSidebar } from './fixtures/shell'
import { uniqueEmail } from './fixtures/testData'

/**
 * NCL-07-CN-003 — Lịch sử hội thoại ở thanh bên (Jira LC-40): nhóm theo ngày với hội thoại đã ghim trên cùng, mở lại
 * hội thoại cũ và hỏi nối tiếp, tìm theo tên và nội dung, chuyển vào không gian học, đổi tên, tải về, xóa và chặn người
 * lạ. Trợ lý chạy qua bản mô phỏng: câu trả lời nối tiếp nhắc lại câu hỏi trước khi nhận được các lượt trước.
 */

const TREE = [
  'Cây nhị phân là cấu trúc dữ liệu mà mỗi nút có tối đa hai con.',
  'Duyệt cây theo thứ tự giữa thăm cây con trái, rồi tới nút gốc, rồi tới cây con phải.',
  'Duyệt theo thứ tự trước thăm nút gốc trước rồi mới tới hai cây con.',
].join('\n\n')

const HOUR = 60 * 60 * 1000

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

async function signUp(page: Page, dev: DevApi, tag: string) {
  const email = uniqueEmail(tag)
  await createVerifiedAccount(page, dev, email)
  await acknowledgeAiDisclosure(page)
  await page.goto('/library')
  await expect(page.getByTestId('library-main')).toBeVisible()
  return email
}

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

async function createWorkspace(page: Page, name: string, documentIds: string[]) {
  const created = await page.request.post('/api/v1/workspaces', { data: { name }, headers: await xsrf(page) })
  const id = ((await created.json()) as { id: string }).id
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

/** Số giờ từ lúc {@code at} (giờ máy) tới bây giờ, làm tròn xuống. */
function hoursSince(at: Date) {
  return Math.floor((Date.now() - at.getTime()) / HOUR)
}

/** Trưa của ngày cách hôm nay {@code days} ngày, theo lịch của máy chạy kiểm thử (trùng với trình duyệt). */
function noonDaysAgo(days: number) {
  const now = new Date()
  return new Date(now.getFullYear(), now.getMonth(), now.getDate() - days, 12)
}

/** Lịch sử hội thoại trên thanh bên (mở ngăn kéo trên điện thoại). */
async function history(page: Page) {
  // Ngăn kéo trên điện thoại vẫn mở sau thao tác không đổi trang (đổi tên, ghim, xóa).
  const drawer = page.getByRole('dialog', { name: 'Thanh bên' })
  const sidebar = (await drawer.isVisible()) ? drawer : await openSidebar(page)
  const section = sidebar.getByTestId('sidebar-conversations')
  await expect(section).toBeVisible()
  return section
}

async function openMenu(section: Locator, title: string) {
  await section.getByRole('button', { name: `Thao tác với “${title}”` }).click()
  return section.page().getByRole('menu', { name: `Thao tác với “${title}”` })
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

test.describe('NCL-07-CN-003 · Lịch sử hội thoại ở thanh bên', () => {
  test('TC-01: hội thoại chia nhóm hôm nay, hôm qua, 7 ngày qua, cũ hơn; hội thoại đã ghim nằm trên cùng', async ({
    page,
    dev,
  }, info) => {
    const email = await signUp(page, dev, 'ls-tc01')
    await dev.seedConversations(email, [
      { question: 'Ôn thi cuối kỳ cấu trúc dữ liệu', hoursAgo: hoursSince(noonDaysAgo(40)), pinned: true },
      { question: 'Giới hạn của dãy số', hoursAgo: hoursSince(noonDaysAgo(20)) },
      { question: 'Đạo hàm riêng là gì?', hoursAgo: hoursSince(noonDaysAgo(3)) },
      { question: 'Cây nhị phân tìm kiếm', hoursAgo: hoursSince(noonDaysAgo(1)) },
      { question: 'Duyệt cây theo thứ tự giữa', hoursAgo: 0 },
    ])

    await page.goto('/chat')
    const section = await history(page)
    const groups = section.getByTestId('conversation-group')
    await expect(groups).toHaveCount(5)
    await expect(groups.getByRole('heading')).toHaveText(['Đã ghim', 'Hôm nay', 'Hôm qua', '7 ngày qua', 'Cũ hơn'])
    await expect(section.getByTestId('sidebar-conversation-title')).toHaveText([
      'Ôn thi cuối kỳ cấu trúc dữ liệu',
      'Duyệt cây theo thứ tự giữa',
      'Cây nhị phân tìm kiếm',
      'Đạo hàm riêng là gì?',
      'Giới hạn của dãy số',
    ])
    await expectNoSeriousA11yViolations(page)
    await page.screenshot({ path: info.outputPath('conversation-history-groups.png') })

    // Ghim thêm một hội thoại: nó lên nhóm Đã ghim, ghim sau đứng trước.
    const menu = await openMenu(section, 'Giới hạn của dãy số')
    await expect(menu.getByRole('menuitem')).toHaveText(['Đổi tên', 'Ghim lên đầu', 'Chuyển vào không gian học', 'Tải về (.md)', 'Xóa'])
    await expectNoSeriousA11yViolations(page)
    await page.screenshot({ path: info.outputPath('conversation-menu.png') })
    await menu.getByRole('menuitem', { name: 'Ghim lên đầu' }).click()
    await expect(page.getByText('Đã ghim hội thoại lên đầu.')).toBeVisible()
    await expect(groups.first().getByTestId('sidebar-conversation-title')).toHaveText([
      'Giới hạn của dãy số',
      'Ôn thi cuối kỳ cấu trúc dữ liệu',
    ])
    await expect(groups.getByRole('heading')).toHaveText(['Đã ghim', 'Hôm nay', 'Hôm qua', '7 ngày qua'])
  })

  test('TC-02: mở lại hội thoại cũ từ thanh bên rồi hỏi nối tiếp; trợ lý dùng ngữ cảnh các lượt trước', async ({
    page,
    dev,
  }, info) => {
    test.setTimeout(120_000)
    await signUp(page, dev, 'ls-tc02')
    await pasteDocument(page, 'Bài giảng cây nhị phân', TREE)

    await page.goto('/chat')
    await page.getByTestId('composer-attach').click()
    const picker = page.getByRole('dialog', { name: 'Đính kèm tài liệu' })
    await picker.getByTestId('picker-option').filter({ hasText: 'Bài giảng cây nhị phân' }).getByRole('checkbox').check()
    await picker.getByTestId('picker-apply').click()
    const first = await ask(page, 'Cách duyệt cây theo thứ tự giữa là gì?')
    await expect(first).toHaveAttribute('data-status', 'COMPLETE')
    await expect(page).toHaveURL(/\/chat\/[0-9a-f-]{36}$/)
    const conversationUrl = page.url()

    // Rời đi rồi quay lại bằng thanh bên.
    await page.goto('/library')
    await expect(page.getByTestId('library-main')).toBeVisible()
    const section = await history(page)
    await section.getByRole('link', { name: 'Cách duyệt cây theo thứ tự giữa là gì?' }).click()
    await expect(page).toHaveURL(conversationUrl)
    await expect(page.getByTestId('assistant-message')).toHaveCount(1)
    await expect(page.getByTestId('source-scope-summary')).toHaveText('Đang dùng 1/1 tài liệu')

    const follow = await ask(page, 'Còn duyệt theo thứ tự trước thì sao?')
    await expect(follow).toHaveAttribute('data-status', 'COMPLETE')
    await expect(follow.getByTestId('answer-text')).toContainText(
      'Nối tiếp câu hỏi trước (“Cách duyệt cây theo thứ tự giữa là gì?”)',
    )
    await expect(follow.getByTestId('answer-text')).toContainText('thứ tự trước')
    await expectNoSeriousA11yViolations(page)
    await page.screenshot({ path: info.outputPath('conversation-follow-up.png'), fullPage: true })
  })

  test('TC-03: tìm hội thoại theo từ khóa trong tên và trong nội dung câu trả lời', async ({ page, dev }, info) => {
    const email = await signUp(page, dev, 'ls-tc03')
    await dev.seedConversations(email, [
      { question: 'Ôn tập đồ thị', answer: 'Đồ thị gồm các đỉnh và các cạnh.', hoursAgo: 5 },
      { question: 'Tìm kiếm theo chiều rộng', answer: 'Thuật toán này dùng hàng đợi để thăm từng lớp.', hoursAgo: 4 },
      { question: 'Lịch sử Việt Nam', answer: 'Câu trả lời mẫu.', hoursAgo: 3 },
    ])

    await page.goto('/chat')
    const section = await history(page)
    await expect(section.getByTestId('sidebar-conversation')).toHaveCount(3)
    await section.getByRole('button', { name: 'Tìm hội thoại' }).click()
    const search = section.getByRole('searchbox', { name: 'Tìm hội thoại' })
    await expect(search).toBeFocused()

    await search.fill('đồ thị')
    await expect(section.getByTestId('sidebar-conversation-title')).toHaveText(['Ôn tập đồ thị'])
    // "hàng đợi" chỉ có trong câu trả lời.
    await search.fill('HÀNG ĐỢI')
    await expect(section.getByTestId('sidebar-conversation-title')).toHaveText(['Tìm kiếm theo chiều rộng'])
    await expectNoSeriousA11yViolations(page)
    await page.screenshot({ path: info.outputPath('conversation-search.png') })
    await search.fill('vật lý lượng tử')
    await expect(section.getByText('Không có hội thoại nào khớp “vật lý lượng tử”.')).toBeVisible()

    await search.press('Escape')
    await expect(section.getByTestId('sidebar-conversation')).toHaveCount(3)
  })

  test('TC-04: chuyển hội thoại riêng vào không gian học; câu hỏi tiếp theo dùng tài liệu của không gian học', async ({
    page,
    dev,
  }, info) => {
    test.setTimeout(120_000)
    const email = await signUp(page, dev, 'ls-tc04')
    const lecture = await pasteDocument(page, 'Bài giảng cây nhị phân', TREE)
    await createWorkspace(page, 'Cấu trúc dữ liệu', [lecture])
    const [conversation] = await dev.seedConversations(email, [{ question: 'Hỏi nhanh về cây', hoursAgo: 1 }])

    await page.goto(`/chat/${conversation}`)
    await expect(page.getByRole('heading', { name: 'Hỏi nhanh về cây' })).toBeVisible()
    const section = await history(page)
    const menu = await openMenu(section, 'Hỏi nhanh về cây')
    await menu.getByRole('menuitem', { name: 'Chuyển vào không gian học' }).click()
    const dialog = page.getByRole('dialog', { name: 'Chuyển “Hỏi nhanh về cây” vào không gian học' })
    await expect(dialog.getByTestId('conversation-move-target')).toHaveValue(/.+/)
    await expectNoSeriousA11yViolations(page)
    await page.screenshot({ path: info.outputPath('conversation-move.png') })
    await dialog.getByTestId('conversation-move-confirm').click()

    await expect(page.getByText('Đã chuyển hội thoại vào “Cấu trúc dữ liệu”.')).toBeVisible()
    await expect(page).toHaveURL(new RegExp(`/workspaces/[0-9a-f-]{36}\\?tab=assistant&conversation=${conversation}$`))
    await expect(page.getByTestId('source-scope-summary')).toHaveText('Đang dùng 1/1 tài liệu')
    const answer = await ask(page, 'Duyệt cây theo thứ tự giữa là gì?')
    await expect(answer).toHaveAttribute('data-status', 'COMPLETE')
    await expect(answer.getByTestId('answer-sources')).toContainText('Bài giảng cây nhị phân')

    // Không còn trong lịch sử hội thoại riêng.
    await expect((await history(page)).getByTestId('sidebar-conversation')).toHaveCount(0)
  })

  test('TC-05: người lạ mở hội thoại bằng mã thì thấy không tìm thấy; chủ hội thoại đổi tên, tải về và xóa được', async ({
    page,
    dev,
    browser,
  }) => {
    const email = await signUp(page, dev, 'ls-tc05')
    const [conversation] = await dev.seedConversations(email, [
      { question: 'Ghi chú riêng tư', answer: 'Nội dung chỉ chủ hội thoại được xem.', hoursAgo: 2 },
    ])

    const other = await browser.newPage({ viewport: page.viewportSize() ?? undefined })
    await signUp(other, dev, 'ls-tc05-la')
    await other.goto(`/chat/${conversation}`)
    await expect(other.getByTestId('conversation-not-found')).toBeVisible()
    await expect(other.getByText('Nội dung chỉ chủ hội thoại được xem.')).toHaveCount(0)
    const denied = await other.request.get(`/api/v1/assistant/conversations/${conversation}/export`)
    expect(denied.status()).toBe(404)
    await other.close()

    await page.goto('/chat')
    let section = await history(page)
    let menu = await openMenu(section, 'Ghi chú riêng tư')
    await menu.getByRole('menuitem', { name: 'Đổi tên' }).click()
    const rename = page.getByRole('dialog', { name: 'Đổi tên hội thoại' })
    await rename.getByTestId('conversation-title-input').fill('Ghi chú ôn tập')
    await rename.getByTestId('conversation-rename-save').click()
    await expect(page.getByText('Đã đổi tên hội thoại.')).toBeVisible()
    section = await history(page)
    await expect(section.getByTestId('sidebar-conversation-title')).toHaveText(['Ghi chú ôn tập'])

    menu = await openMenu(section, 'Ghi chú ôn tập')
    const downloading = page.waitForEvent('download')
    await menu.getByRole('menuitem', { name: 'Tải về (.md)' }).click()
    const download = await downloading
    expect(download.suggestedFilename()).toBe('Ghi chú ôn tập.md')

    section = await history(page)
    menu = await openMenu(section, 'Ghi chú ôn tập')
    await menu.getByRole('menuitem', { name: 'Xóa' }).click()
    await page.getByRole('button', { name: 'Xóa hội thoại' }).click()
    await expect(page.getByText('Đã xóa hội thoại.')).toBeVisible()
    await expect((await history(page)).getByText('Chưa có hội thoại nào. Hỏi trợ lý để bắt đầu.')).toBeVisible()
    await page.goto(`/chat/${conversation}`)
    await expect(page.getByTestId('conversation-not-found')).toBeVisible()
  })
})
