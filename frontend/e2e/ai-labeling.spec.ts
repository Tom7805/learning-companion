import AxeBuilder from '@axe-core/playwright'
import type { Page } from '@playwright/test'
import { readFile } from 'node:fs/promises'
import { createVerifiedAccount, expect, test, type DevApi } from './fixtures/auth'
import { uniqueEmail } from './fixtures/testData'

/**
 * NCL-04-CN-005 — Gắn nhãn nội dung do trí tuệ nhân tạo sinh ra (Jira LC-37): thông báo lần đầu mở trợ lý, nhãn trên
 * câu trả lời cũ, tệp Markdown tải về có dấu nhận biết trong siêu dữ liệu, và chặn tải khi bộ đính dấu lỗi.
 */

const TREE = [
  'Cây nhị phân là cấu trúc dữ liệu mà mỗi nút có tối đa hai con.',
  'Duyệt cây theo thứ tự giữa thăm cây con trái, rồi tới nút gốc, rồi tới cây con phải.',
].join('\n\n')

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

/** Tài khoản mới mở trợ lý: thông báo hiện trước câu hỏi đầu tiên; bấm đã hiểu rồi hỏi một câu có trích dẫn. */
async function firstAnswer(page: Page, dev: DevApi, tag: string) {
  await createVerifiedAccount(page, dev, uniqueEmail(tag))
  await page.goto('/library')
  await expect(page.getByTestId('library-main')).toBeVisible()
  const created = await page.request.post('/api/v1/library/sources/text', {
    data: { title: 'Bài giảng cây nhị phân', text: TREE, folderId: null },
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

  await page.goto('/chat')
  const disclosure = page.getByRole('dialog', { name: 'Bạn đang trò chuyện với hệ thống trí tuệ nhân tạo' })
  await expect(disclosure).toBeVisible()
  await disclosure.getByTestId('ai-disclosure-accept').click()
  await expect(disclosure).toBeHidden()

  await page.getByTestId('composer-attach').click()
  const picker = page.getByRole('dialog', { name: 'Đính kèm tài liệu' })
  await picker.getByTestId('picker-option').filter({ hasText: 'Bài giảng cây nhị phân' }).getByRole('checkbox').check()
  await picker.getByTestId('picker-apply').click()
  await page.getByTestId('chat-input').fill('Cách duyệt cây theo thứ tự giữa là gì?')
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

test.describe('NCL-04-CN-005 · Gắn nhãn nội dung do trí tuệ nhân tạo sinh ra', () => {
  test('TC-01: lần đầu mở trợ lý, thông báo đang trò chuyện với hệ thống trí tuệ nhân tạo hiện trước câu hỏi đầu tiên', async ({
    page,
    dev,
  }, info) => {
    await createVerifiedAccount(page, dev, uniqueEmail('ai-disclosure'))
    await page.goto('/chat')

    const disclosure = page.getByRole('dialog', { name: 'Bạn đang trò chuyện với hệ thống trí tuệ nhân tạo' })
    await expect(disclosure).toBeVisible()
    await expect(disclosure.getByTestId('ai-disclosure')).toContainText('có thể sai hoặc thiếu')
    await expect(page.getByTestId('assistant-message')).toHaveCount(0)
    await expectNoSeriousA11yViolations(page)
    await page.screenshot({ path: info.outputPath('ai-disclosure.png') })

    await disclosure.getByTestId('ai-disclosure-accept').click()
    await expect(disclosure).toBeHidden()
    await expect(page.getByTestId('chat-input')).toBeVisible()

    // Đã đọc rồi thì mở lại trợ lý không hiện nữa, kể cả sau khi tải lại trang.
    await page.reload()
    await expect(page.getByTestId('chat-greeting')).toBeVisible()
    await expect(disclosure).toHaveCount(0)
  })

  test('TC-02/03: câu trả lời có nhãn do AI sinh, vẫn còn khi mở lại hội thoại; tệp tải về có dấu nhận biết', async ({
    page,
    dev,
  }, info) => {
    test.setTimeout(120_000)
    const answer = await firstAnswer(page, dev, 'ai-label')
    await expect(answer.getByTestId('ai-label')).toHaveText(/Do AI sinh/)
    await expect(page.getByTestId('user-message').getByTestId('ai-label')).toHaveCount(0)

    await page.reload()
    const reloaded = page.getByTestId('assistant-message').last()
    await expect(reloaded.getByTestId('ai-label')).toBeVisible()
    await expectNoSeriousA11yViolations(page)
    await page.screenshot({ path: info.outputPath('ai-label.png') })

    const [download] = await Promise.all([page.waitForEvent('download'), reloaded.getByTestId('answer-download').click()])
    expect(download.suggestedFilename()).toMatch(/\.md$/)
    const file = await readFile((await download.path())!, 'utf-8')
    expect(file.startsWith('---\n')).toBeTruthy()
    const meta = file.slice(4, file.indexOf('\n---\n'))
    expect(meta).toContain('ai_generated: true')
    expect(meta).toContain('ai_feature: ASSISTANT_CHAT')
    expect(meta).toMatch(/content_sha256: [0-9a-f]{64}/)
    expect(file).toContain('> Nội dung do trí tuệ nhân tạo sinh ra')
    expect(file).toContain('1. Bài giảng cây nhị phân')
  })

  test('TC-04: bộ đính dấu nhận biết lỗi thì chặn tải và báo lỗi tạm thời thay vì xuất tệp không có dấu', async ({
    page,
    dev,
  }, info) => {
    test.setTimeout(120_000)
    const answer = await firstAnswer(page, dev, 'ai-label-fail')
    await dev.failNextAiLabel(-1)

    let downloaded = false
    page.on('download', () => {
      downloaded = true
    })
    await answer.getByTestId('answer-download').click()
    await expect(page.getByText(/Chưa gắn được dấu nhận biết/)).toBeVisible()
    await page.screenshot({ path: info.outputPath('ai-label-failed.png') })
    expect(downloaded).toBeFalsy()

    // Bộ đính dấu chạy lại được thì tải bình thường.
    await dev.failNextAiLabel(0)
    const [download] = await Promise.all([page.waitForEvent('download'), answer.getByTestId('answer-download').click()])
    expect(await readFile((await download.path())!, 'utf-8')).toContain('ai_generated: true')
  })
})
