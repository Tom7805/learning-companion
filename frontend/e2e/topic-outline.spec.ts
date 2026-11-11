import AxeBuilder from '@axe-core/playwright'
import type { Browser, Page } from '@playwright/test'
import { createVerifiedAccount, expect, test, type DevApi } from './fixtures/auth'
import { uniqueEmail } from './fixtures/testData'

/**
 * NCL-06-CN-002 — Dàn ý chủ đề và mục tiêu học của không gian học (Jira LC-39): trợ lý đề xuất dàn ý có mục tiêu và
 * trích dẫn, tự lập dàn ý không tốn lượt, đề xuất lại giữ chủ đề đã sửa, tối đa ba cấp và kiểm tra chủ sở hữu.
 */

const CELL = [
  'Màng tế bào bao quanh tế bào chất và điều khiển chất đi vào, đi ra.',
  'Ti thể là nơi diễn ra hô hấp tế bào, tạo năng lượng cho tế bào.',
  'Nhân tế bào chứa vật chất di truyền và điều khiển mọi hoạt động sống.',
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

async function usedUnits(page: Page) {
  const quota = (await (await page.request.get('/api/v1/quota')).json()) as { ai: { usedUnits: number } }
  return quota.ai.usedUnits
}

/** Người học có một không gian học gắn ngần ấy bài học đã xử lý xong; trả về mã không gian học. */
async function studySpace(page: Page, dev: DevApi, tag: string, lessons: string[]) {
  await createVerifiedAccount(page, dev, uniqueEmail(tag))
  await page.goto('/library')
  await expect(page.getByTestId('library-main')).toBeVisible()
  const ids: string[] = []
  for (const title of lessons) {
    const response = await page.request.post('/api/v1/library/sources/text', {
      data: { title, text: `${CELL}\n\n${title}.`, folderId: null },
      headers: await xsrf(page),
    })
    ids.push(((await response.json()) as { id: string }).id)
  }
  for (const id of ids) {
    await expect
      .poll(
        async () =>
          ((await (await page.request.get(`/api/v1/library/documents/${id}?track=false`)).json()) as { status: string })
            .status,
        { timeout: 60_000 },
      )
      .toBe('READY')
  }
  const created = await page.request.post('/api/v1/workspaces', { data: { name: 'Sinh học tế bào' }, headers: await xsrf(page) })
  const workspaceId = ((await created.json()) as { id: string }).id
  if (ids.length > 0) {
    const attached = await page.request.post(`/api/v1/workspaces/${workspaceId}/documents`, {
      data: { documentIds: ids },
      headers: await xsrf(page),
    })
    expect(attached.ok()).toBeTruthy()
  }
  await page.goto(`/workspaces/${workspaceId}?tab=outline`)
  await expect(page.getByTestId('topic-outline')).toBeVisible()
  return workspaceId
}

/** Mục danh sách của đúng chủ đề có tên này (không lấy chủ đề cha chứa nó). */
function topicItem(page: Page, title: string) {
  return page.getByTestId('topic-title').filter({ hasText: title }).first().locator('xpath=ancestor::li[1]')
}

/** Mở trình đơn thao tác của một chủ đề rồi chọn một mục. */
async function topicAction(page: Page, title: string, action: string) {
  await page.getByRole('button', { name: `Thao tác với “${title}”` }).first().click()
  await page.getByRole('menuitem', { name: action }).click()
}

test.beforeEach(async ({ dev }) => {
  await dev.resetQuota()
  await dev.resetAiGateway()
  await dev.resetAiConfigs()
  await dev.setDocumentProcessing(true)
})

test.describe('NCL-06-CN-002 · Dàn ý chủ đề và mục tiêu học', () => {
  test('TC-01/03: đề xuất dàn ý hai cấp có mục tiêu và trích dẫn; đề xuất lại giữ chủ đề đã sửa tay', async ({
    page,
    dev,
  }, info) => {
    test.setTimeout(150_000)
    await studySpace(page, dev, 'outline-ai', ['Bài 1 Màng tế bào', 'Bài 2 Ti thể', 'Bài 3 Nhân tế bào'])
    const before = await usedUnits(page)

    await page.getByTestId('topic-suggest').click()
    const roots = page.locator('[data-testid="topic"][data-depth="1"]')
    await expect(roots).toHaveCount(3, { timeout: 30_000 })
    await expect(page.locator('[data-testid="topic"][data-depth="2"]').first()).toBeVisible()
    const first = roots.first()
    await expect(first.getByTestId('topic-goal').first()).toContainText('Nắm được')
    await expect(first.getByTestId('ai-label').first()).toHaveText(/Do AI sinh/)
    await expect(first.getByTestId('topic-source-count').first()).toHaveText(/1 nguồn/)
    expect(await usedUnits(page)).toBeGreaterThan(before)
    await expectNoSeriousA11yViolations(page)
    await page.screenshot({ path: info.outputPath('outline-suggested.png'), fullPage: true })

    // Trích dẫn của chủ đề mở đúng đoạn ở khung xem bên.
    await first.getByTestId('topic-source-count').first().click()
    await first.getByTestId('topic-source').first().click()
    await expect(page).toHaveURL(/viewer=.*kind=PARAGRAPH&number=\d+&passage=\d+/)
    if (isMobile(page)) {
      await page.keyboard.press('Escape')
    }
    await page.goto(page.url().replace(/&?viewer=[^&]*|&kind=[^&]*|&number=[^&]*|&passage=[^&]*/g, ''))
    await expect(page.getByTestId('topic-outline')).toBeVisible()

    // Sửa tay tên hai chủ đề rồi đề xuất lại (TC-03).
    for (const [from, to] of [
      ['Bài 1 Màng tế bào', 'Màng sinh chất (tự sửa)'],
      ['Bài 2 Ti thể', 'Năng lượng (tự sửa)'],
    ]) {
      await topicAction(page, from, 'Sửa tên và mục tiêu')
      const dialog = page.getByRole('dialog', { name: 'Sửa chủ đề' })
      await dialog.getByTestId('topic-title-input').fill(to)
      await dialog.getByTestId('topic-save').click()
      await expect(dialog).toBeHidden()
    }
    await page.getByTestId('topic-suggest').click()
    await expect(page.getByText(/Đã thêm \d+ chủ đề mới vào dàn ý/)).toBeVisible({ timeout: 30_000 })
    const titles = page.locator('[data-testid="topic"][data-depth="1"] > [data-testid="topic-card"] [data-testid="topic-title"]')
    await expect(titles).toContainText(['Màng sinh chất (tự sửa)', 'Năng lượng (tự sửa)'])
    await expect(titles).toContainText(['Bài 1 Màng tế bào'])
    const edited = roots.filter({ hasText: 'Màng sinh chất (tự sửa)' }).first()
    await expect(edited.getByTestId('ai-label').first()).toContainText('Đã chỉnh sửa')
  })

  test('TC-02/04: tự lập dàn ý không tốn lượt; đưa chủ đề xuống cấp thứ tư thì bị từ chối và giữ chỗ cũ', async ({
    page,
    dev,
  }, info) => {
    test.setTimeout(120_000)
    await studySpace(page, dev, 'outline-manual', [])
    const before = await usedUnits(page)
    await expect(page.getByTestId('topic-suggest')).toBeDisabled()

    await page.getByTestId('topic-add').click()
    const dialog = page.getByRole('dialog', { name: 'Thêm chủ đề' })
    await dialog.getByTestId('topic-title-input').fill('Cấp 1')
    await dialog.getByTestId('topic-goal-input').fill('Nắm được khái niệm cơ bản')
    await dialog.getByTestId('topic-save').click()
    await expect(dialog).toBeHidden()
    for (const [parent, title] of [
      ['Cấp 1', 'Cấp 2'],
      ['Cấp 2', 'Cấp 3'],
    ]) {
      await topicAction(page, parent, 'Thêm chủ đề con')
      const child = page.getByRole('dialog', { name: `Thêm chủ đề con vào “${parent}”` })
      await child.getByTestId('topic-title-input').fill(title)
      await child.getByTestId('topic-save').click()
      await expect(child).toBeHidden()
    }
    await page.getByTestId('topic-add').click()
    await page.getByRole('dialog', { name: 'Thêm chủ đề' }).getByTestId('topic-title-input').fill('Chủ đề rời')
    await page.getByRole('dialog', { name: 'Thêm chủ đề' }).getByTestId('topic-save').click()
    await expect(page.getByTestId('topic')).toHaveCount(4)
    expect(await usedUnits(page)).toBe(before)

    // Đưa "Chủ đề rời" vào dưới "Cấp 3": máy chủ từ chối, cây giữ nguyên.
    if (isMobile(page)) {
      await topicAction(page, 'Chủ đề rời', 'Thụt vào thành chủ đề con')
      // Thụt vào chỉ đưa xuống cấp hai (dưới "Cấp 1"); thử tiếp từ đó xuống cấp ba rồi cấp bốn.
      await expect(page.locator('[data-testid="topic"][data-depth="2"]')).toHaveCount(2)
      await topicAction(page, 'Chủ đề rời', 'Thụt vào thành chủ đề con')
      await expect(page.locator('[data-testid="topic"][data-depth="3"]')).toHaveCount(2)
      await topicAction(page, 'Chủ đề rời', 'Thụt vào thành chủ đề con')
      await expect(page.getByText(/Dàn ý chủ đề có tối đa ba cấp/)).toBeVisible()
      await expect(topicItem(page, 'Chủ đề rời')).toHaveAttribute('data-depth', '3')
    } else {
      const loose = page.getByTestId('topic-card').filter({ hasText: 'Chủ đề rời' })
      const deepest = page.getByTestId('topic-card').filter({ hasText: 'Cấp 3' })
      await loose.dragTo(deepest)
      await expect(page.getByText(/Dàn ý chủ đề có tối đa ba cấp/)).toBeVisible()
      await expect(topicItem(page, 'Chủ đề rời')).toHaveAttribute('data-depth', '1')
    }
    await expect(page.locator('[data-testid="topic"][data-depth="3"]')).toHaveCount(isMobile(page) ? 2 : 1)
    await expectNoSeriousA11yViolations(page)
    await page.screenshot({ path: info.outputPath('outline-manual.png'), fullPage: true })
  })

  test('TC-05: người học khác sửa dàn ý của không gian học bằng mã thì nhận không tìm thấy', async ({ page, dev, browser }) => {
    test.setTimeout(120_000)
    const workspaceId = await studySpace(page, dev, 'outline-owner', [])
    const created = await page.request.post(`/api/v1/workspaces/${workspaceId}/topics`, {
      data: { title: 'Riêng tư' },
      headers: await xsrf(page),
    })
    expect(created.ok()).toBeTruthy()

    const other = await otherLearner(browser, dev)
    const denied = await other.request.post(`/api/v1/workspaces/${workspaceId}/topics`, {
      data: { title: 'Chen vào' },
      headers: await xsrf(other),
    })
    expect(denied.status()).toBe(404)
    const read = await other.request.get(`/api/v1/workspaces/${workspaceId}/topics`)
    expect(read.status()).toBe(404)
    expect(await read.text()).not.toContain('Riêng tư')
    await other.context().close()
  })
})

async function otherLearner(browser: Browser, dev: DevApi) {
  const context = await browser.newContext()
  const page = await context.newPage()
  await createVerifiedAccount(page, dev, uniqueEmail('outline-stranger'))
  await page.goto('/library')
  return page
}
