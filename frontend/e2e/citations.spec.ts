import AxeBuilder from '@axe-core/playwright'
import type { Browser, Page } from '@playwright/test'
import { createVerifiedAccount, expect, test, type DevApi } from './fixtures/auth'
import { uniqueEmail } from './fixtures/testData'

/**
 * NCL-07-CN-002 — Trích dẫn bấm mở đúng đoạn tài liệu (Jira LC-36): khung xem nhanh khi rê chuột, ngăn trượt trên điện
 * thoại, mở khung xem bên đúng đoạn, tài liệu không còn, và kiểm tra chủ sở hữu của điểm cuối đoạn gốc.
 */

const TREE = [
  'Cây nhị phân là cấu trúc dữ liệu mà mỗi nút có tối đa hai con.',
  'Duyệt cây theo thứ tự giữa thăm cây con trái, rồi tới nút gốc, rồi tới cây con phải.',
  'Duyệt theo thứ tự trước thăm nút gốc trước rồi mới tới hai cây con.',
].join('\n\n')

const isMobile = (page: Page) => (page.viewportSize()?.width ?? 1440) < 768

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

/** Người học có một bài giảng đã xử lý xong và một câu trả lời trích dẫn bài giảng đó, trong hội thoại riêng. */
async function answered(page: Page, dev: DevApi, tag: string) {
  await createVerifiedAccount(page, dev, uniqueEmail(tag))
  await page.goto('/library')
  await expect(page.getByTestId('library-main')).toBeVisible()
  const response = await page.request.post('/api/v1/library/sources/text', {
    data: { title: 'Bài giảng cây nhị phân', text: TREE, folderId: null },
    headers: await xsrf(page),
  })
  const id = ((await response.json()) as { id: string }).id
  await expect
    .poll(
      async () =>
        ((await (await page.request.get(`/api/v1/library/documents/${id}?track=false`)).json()) as { status: string })
          .status,
      { timeout: 60_000 },
    )
    .toBe('READY')
  await page.goto('/chat')
  await page.getByTestId('composer-attach').click()
  const picker = page.getByRole('dialog', { name: 'Đính kèm tài liệu' })
  await picker.getByTestId('picker-option').filter({ hasText: 'Bài giảng cây nhị phân' }).getByRole('checkbox').check()
  await picker.getByTestId('picker-apply').click()
  await page.getByTestId('chat-input').fill('Cách duyệt cây theo thứ tự giữa là gì?')
  await page.getByTestId('chat-send').click()
  const answer = page.getByTestId('assistant-message').last()
  await expect(answer).toHaveAttribute('data-status', 'COMPLETE', { timeout: 30_000 })
  return { documentId: id, answer }
}

test.beforeEach(async ({ dev }) => {
  await dev.resetQuota()
  await dev.resetAiGateway()
  await dev.resetAiConfigs()
  await dev.setDocumentProcessing(true)
})

test.describe('NCL-07-CN-002 · Trích dẫn bấm mở đúng đoạn tài liệu', () => {
  test('TC-01/02: xem nhanh đoạn gốc (khung nổi trên máy tính, ngăn trượt trên điện thoại) rồi mở đúng đoạn', async ({
    page,
    dev,
  }, info) => {
    test.setTimeout(120_000)
    const { answer } = await answered(page, dev, 'cit-open')
    const marker = answer.getByTestId('citation-marker').first()

    if (isMobile(page)) {
      await marker.click()
      const sheet = page.getByRole('dialog', { name: 'Trích dẫn 1' })
      await expect(sheet.getByTestId('citation-title')).toHaveText('Bài giảng cây nhị phân')
      await expect(sheet.getByTestId('citation-location')).toHaveText(/Đoạn \d/)
      await expect(sheet.getByTestId('citation-highlight')).toContainText('thứ tự giữa')
      await expectNoSeriousA11yViolations(page)
      await page.screenshot({ path: info.outputPath('citation-sheet.png') })
      await sheet.getByTestId('citation-open').click()
    } else {
      await marker.hover()
      const popover = page.getByTestId('citation-popover')
      await expect(popover.getByTestId('citation-title')).toHaveText('Bài giảng cây nhị phân')
      await expect(popover.getByTestId('citation-highlight')).toContainText('thứ tự giữa')
      await expectNoSeriousA11yViolations(page)
      await page.screenshot({ path: info.outputPath('citation-popover.png') })
      await marker.click()
    }

    await expect(page).toHaveURL(/viewer=.*kind=PARAGRAPH&number=\d+&passage=\d+/)
    const panel = page.getByTestId('side-panel')
    await expect(panel).toBeVisible()
    await expect(panel.getByTestId('viewer-passage').filter({ hasText: 'thứ tự giữa' }).first()).toHaveAttribute(
      'data-highlighted',
      'true',
    )
    await page.screenshot({ path: info.outputPath('citation-viewer.png') })
  })

  test('TC-03: tài liệu được trích đã bị xóa thì trích dẫn hiện tài liệu không còn, không mở khung xem lỗi', async ({
    page,
    dev,
  }, info) => {
    test.setTimeout(120_000)
    const { documentId } = await answered(page, dev, 'cit-gone')
    const deleted = await page.request.delete(`/api/v1/library/documents/${documentId}?confirmInUse=true`, {
      headers: await xsrf(page),
    })
    expect(deleted.ok()).toBeTruthy()

    await page.reload()
    const answer = page.getByTestId('assistant-message').last()
    await expect(answer.getByTestId('answer-source').first()).toContainText('Tài liệu không còn')
    const marker = answer.getByTestId('citation-marker').first()
    await expect(marker).toHaveAttribute('data-available', 'false')
    await marker.click()
    if (isMobile(page)) {
      const sheet = page.getByRole('dialog', { name: 'Trích dẫn 1' })
      await expect(sheet.getByTestId('citation-gone')).toBeVisible()
      await expect(sheet.getByTestId('citation-open')).toHaveCount(0)
    } else {
      await expect(page.getByTestId('citation-gone')).toBeVisible()
    }
    await expect(page).not.toHaveURL(/viewer=/)
    await expect(page.getByTestId('document-viewer')).toHaveCount(0)
    await expectNoSeriousA11yViolations(page)
    await page.screenshot({ path: info.outputPath('citation-gone.png') })
  })

  test('TC-04: người học khác gọi điểm cuối lấy đoạn gốc của trích dẫn thì nhận không tìm thấy', async ({
    page,
    dev,
    browser,
  }) => {
    test.setTimeout(120_000)
    await answered(page, dev, 'cit-owner')
    const conversationId = page.url().split('/chat/')[1]
    const conversation = (await (await page.request.get(`/api/v1/assistant/conversations/${conversationId}`)).json()) as {
      messages: { citations: { id: string }[] }[]
    }
    const citationId = conversation.messages[1].citations[0].id
    expect((await page.request.get(`/api/v1/assistant/citations/${citationId}`)).ok()).toBeTruthy()

    const other = await otherLearner(browser, dev)
    const denied = await other.request.get(`/api/v1/assistant/citations/${citationId}`)
    expect(denied.status()).toBe(404)
    expect(await denied.text()).not.toContain('thứ tự giữa')
    await other.context().close()
  })
})

async function otherLearner(browser: Browser, dev: DevApi) {
  const context = await browser.newContext()
  const page = await context.newPage()
  await createVerifiedAccount(page, dev, uniqueEmail('cit-stranger'))
  return page
}
