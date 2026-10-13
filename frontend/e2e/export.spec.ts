import AxeBuilder from '@axe-core/playwright'
import type { Download, Page } from '@playwright/test'
import { readFile } from 'node:fs/promises'
import { createVerifiedAccount, expect, loginThroughUi, test } from './fixtures/auth'
import { STRONG_PASSWORD, uniqueEmail } from './fixtures/testData'

/**
 * NCL-01-CN-008 — Xuất toàn bộ dữ liệu học tập bằng định dạng mở (Jira LC-79).
 * Yêu cầu xuất từ mục Tài khoản trong cài đặt; gói dựng ở luồng nền, thư báo có đường dẫn tải sống bảy ngày,
 * mỗi lần tải phải xác nhận lại danh tính.
 */

const SEVEN_DAYS = 7 * 86_400

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

async function openAccountSettings(page: Page) {
  await page.getByRole('button', { name: /Trình đơn tài khoản/ }).first().click()
  await page.getByRole('menuitem', { name: 'Cài đặt' }).click()
  const dialog = page.getByRole('dialog', { name: 'Cài đặt' })
  await dialog.getByRole('button', { name: 'Tài khoản', exact: true }).click()
  const section = dialog.getByRole('region', { name: 'Xuất toàn bộ dữ liệu' })
  await expect(section).toBeVisible()
  return { dialog, section }
}

/** Yêu cầu xuất trong cài đặt rồi chờ gói sẵn sàng; trả về đường dẫn trong thư báo. */
async function requestExport(page: Page, dev: { exportLink: (to: string) => Promise<string> }, email: string) {
  const { dialog, section } = await openAccountSettings(page)
  await section.getByRole('button', { name: /Yêu cầu xuất dữ liệu/ }).click()
  await expect(section.getByTestId('export-ready')).toBeVisible({ timeout: 30_000 })
  const link = await dev.exportLink(email)
  await page.keyboard.press('Escape')
  await expect(dialog).toBeHidden()
  return link
}

/** Bấm tải và nhận tệp trình duyệt lưu về; trả về nội dung để soát các mục trong gói zip. */
async function downloadPackage(page: Page) {
  const [download] = await Promise.all([
    page.waitForEvent('download'),
    page.getByRole('button', { name: /Tải gói dữ liệu/ }).click(),
  ])
  return { download, content: await readFile(await saved(download)) }
}

async function saved(download: Download) {
  const path = await download.path()
  expect(path, 'trình duyệt phải lưu được tệp').toBeTruthy()
  return path!
}

test.describe('NCL-01-CN-008 · Xuất toàn bộ dữ liệu', () => {
  test('TC-01, TC-02: gói có đủ các loại dữ liệu ở định dạng mở kèm tệp hướng dẫn, thư báo có đường dẫn tải', async ({
    page,
    dev,
  }) => {
    const email = uniqueEmail('xuat-du-lieu')
    await createVerifiedAccount(page, dev, email)
    await dev.sampleDocument(email, 'Ghi chú: Định luật Ohm')
    await page.goto('/')

    const { dialog, section } = await openAccountSettings(page)
    await expect(dialog.getByRole('heading', { level: 2, name: 'Tài khoản' })).toBeVisible()
    await expect(section).toContainText('Tài liệu kèm tệp gốc, theo thư mục thư viện')
    await expect(section).toContainText('JSON, CSV, Markdown và tệp gốc')
    // TC-02: xuất dữ liệu không dùng tới trí tuệ nhân tạo nên không tính vào hạn mức.
    await expect(section).toContainText('Không tính vào hạn mức AI')
    await expectNoSeriousA11yViolations(page)

    await section.getByRole('button', { name: /Yêu cầu xuất dữ liệu/ }).click()
    await expect(section.getByTestId('export-notice')).toContainText(`gửi thư tới ${email}`)
    await expect(section.getByTestId('export-ready')).toBeVisible({ timeout: 30_000 })
    await expect(section.getByTestId('export-ready')).toContainText('Gói dữ liệu đã sẵn sàng')
    // Mỗi ngày một lần: đã yêu cầu thì báo lúc được yêu cầu tiếp, không còn nút yêu cầu.
    await expect(section.getByTestId('export-next')).toContainText('Bạn có thể yêu cầu lại sau')
    await expect(section.getByRole('button', { name: /Yêu cầu xuất/ })).toHaveCount(0)

    const mail = await dev.waitForMail(email, 'DATA_EXPORT_READY')
    expect(mail.subject).toBe('Gói dữ liệu của bạn đã sẵn sàng để tải')
    const link = await dev.exportLink(email)

    await page.goto(link)
    await expect(page.getByRole('heading', { level: 1, name: 'Tải gói dữ liệu của bạn' })).toBeVisible()
    const sections = page.getByTestId('export-sections')
    await expect(sections.getByRole('listitem')).toHaveCount(5)
    await expect(sections).toContainText('Thư viện tài liệu')
    await expect(sections).toContainText('Giao diện')
    await expect(page.getByText('Bạn vừa đăng nhập nên tải được ngay.')).toBeVisible()
    await expectNoSeriousA11yViolations(page)

    const { download, content } = await downloadPackage(page)
    expect(download.suggestedFilename()).toMatch(/^learning-companion-export-\d{4}-\d{2}-\d{2}\.zip$/)
    // Zip lưu tên tệp nguyên văn: soát các mục chính có mặt trong gói.
    const names = content.toString('utf8')
    for (const entry of [
      'README.txt',
      'manifest.json',
      'account/account.json',
      'profile/profile.json',
      'privacy/consent-history.csv',
      'appearance/appearance.json',
      'library/documents.json',
      'library/files/dinh-luat-ohm.txt',
      'library/text/Ghi chú_ Định luật Ohm.md',
    ]) {
      expect(names, `gói phải có ${entry}`).toContain(entry)
    }
    await expect(page.getByTestId('export-downloading')).toContainText(download.suggestedFilename())
  })

  test('mỗi lần tải phải xác nhận lại: quá năm phút sau khi đăng nhập thì cần mật khẩu đúng', async ({ page, dev }) => {
    const email = uniqueEmail('xac-nhan-lai')
    await createVerifiedAccount(page, dev, email)
    await page.goto('/')
    const link = await requestExport(page, dev, email)

    await dev.advanceClock(6 * 60)
    await page.goto(link)
    const password = page.getByLabel('Mật khẩu', { exact: true })
    await expect(password).toBeVisible()
    await expect(page.getByText('Gói chứa dữ liệu cá nhân, nên mỗi lần tải bạn cần nhập lại mật khẩu.')).toBeVisible()

    await page.getByRole('button', { name: /Tải gói dữ liệu/ }).click()
    await expect(page.getByText('Hãy nhập mật khẩu.')).toBeVisible()

    await password.fill('mat khau sai roi')
    await page.getByRole('button', { name: /Tải gói dữ liệu/ }).click()
    await expect(page.getByText('Mật khẩu chưa đúng.')).toBeVisible()
    await expect(password).toBeFocused()
    await expectNoSeriousA11yViolations(page)

    await password.fill(STRONG_PASSWORD)
    const { download } = await downloadPackage(page)
    expect(download.suggestedFilename()).toMatch(/\.zip$/)
  })

  test('TC-03: đường dẫn quá bảy ngày báo hết hạn và người học yêu cầu xuất lại được', async ({ page, dev }) => {
    const email = uniqueEmail('het-han')
    await createVerifiedAccount(page, dev, email)
    await page.goto('/')
    const link = await requestExport(page, dev, email)

    await dev.advanceClock(SEVEN_DAYS + 60)
    await page.goto(link)
    // Phiên cũ đã hết hạn sau bảy ngày: đăng nhập lại rồi quay về đúng trang tải.
    await expect(page).toHaveURL(/\/login$/)
    await loginThroughUi(page, email)
    await expect(page).toHaveURL(new RegExp(new URL(link).pathname + '$'))

    const expired = page.getByTestId('export-expired')
    await expect(expired.getByRole('heading', { name: 'Gói dữ liệu đã hết hạn' })).toBeVisible()
    await expect(expired).toContainText('chỉ dùng được 7 ngày')
    await expect(page.getByRole('button', { name: /Tải gói dữ liệu/ })).toHaveCount(0)
    await expectNoSeriousA11yViolations(page)

    await expired.getByRole('button', { name: /Yêu cầu xuất lại/ }).click()
    const requested = page.getByTestId('export-requested')
    await expect(requested).toContainText(`gửi thư có đường dẫn tải mới tới ${email}`)
    await requested.getByRole('link', { name: 'Xem trong cài đặt tài khoản' }).click()
    const section = page.getByRole('dialog', { name: 'Cài đặt' }).getByRole('region', { name: 'Xuất toàn bộ dữ liệu' })
    await expect(section.getByTestId('export-ready')).toBeVisible({ timeout: 30_000 })
    const fresh = await dev.exportLink(email, 2)
    expect(fresh).not.toBe(link)
  })

  test('TC-04: người học khác mở đường dẫn tải của người khác thì nhận "không tìm thấy"', async ({
    page,
    browser,
    dev,
  }) => {
    const email = uniqueEmail('chu-goi')
    await createVerifiedAccount(page, dev, email)
    await page.goto('/')
    const link = await requestExport(page, dev, email)

    const stranger = await (await browser.newContext()).newPage()
    await createVerifiedAccount(stranger, dev, uniqueEmail('nguoi-la'))
    await stranger.goto(link)

    const notFound = stranger.getByTestId('export-not-found')
    await expect(notFound.getByRole('heading', { name: 'Không tìm thấy gói dữ liệu' })).toBeVisible()
    await expect(stranger.getByRole('button', { name: /Tải gói dữ liệu/ })).toHaveCount(0)
    const id = new URL(link).pathname.split('/').pop()
    const xsrf = (await stranger.context().cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')?.value ?? ''
    const response = await stranger.request.post(`/api/v1/exports/${id}/download`, {
      data: { password: STRONG_PASSWORD },
      headers: { 'X-XSRF-TOKEN': xsrf },
    })
    expect(response.status()).toBe(404)
    await expectNoSeriousA11yViolations(stranger)
    await stranger.context().close()
  })

  test('tài khoản chỉ đăng nhập bằng Google thì đăng nhập lại để tải rồi quay về đúng trang', async ({ page, dev }) => {
    const email = uniqueEmail('google-xuat').replace('@example.com', '@gmail.com')
    await page.goto('/login')
    await page.getByTestId('oauth-google').click()
    await page.getByLabel('Địa chỉ thư Google').fill(email)
    await page.getByLabel('Tên', { exact: true }).fill('Minh An')
    await page.getByRole('button', { name: 'Cho phép' }).click()
    await page.getByRole('checkbox').check()
    await page.getByRole('button', { name: /Tạo tài khoản và vào học/ }).click()
    await expect(page.getByTestId('home-greeting')).toBeVisible()
    const link = await requestExport(page, dev, email)

    await dev.advanceClock(6 * 60)
    await page.goto(link)
    await expect(page.getByText('Gói chứa dữ liệu cá nhân, nên mỗi lần tải bạn cần đăng nhập lại.')).toBeVisible()
    await expect(page.getByLabel('Mật khẩu', { exact: true })).toHaveCount(0)
    await page.getByRole('button', { name: 'Đăng nhập lại để tải' }).click()

    await expect(page).toHaveURL(/\/login$/)
    await expect(page.getByTestId('session-notice')).toContainText('hãy đăng nhập lại rồi tải gói dữ liệu')
    await page.getByTestId('oauth-google').click()
    await page.getByLabel('Địa chỉ thư Google').fill(email)
    await page.getByLabel('Tên', { exact: true }).fill('Minh An')
    await page.getByRole('button', { name: 'Cho phép' }).click()

    await expect(page).toHaveURL(new RegExp(new URL(link).pathname + '$'))
    await expect(page.getByText('Bạn vừa đăng nhập nên tải được ngay.')).toBeVisible()
    const { download } = await downloadPackage(page)
    expect(download.suggestedFilename()).toMatch(/\.zip$/)
  })
})
