import AxeBuilder from '@axe-core/playwright'
import type { Page } from '@playwright/test'
import { createVerifiedAccount, expect, test } from './fixtures/auth'
import { uniqueEmail } from './fixtures/testData'
import { accountMenuButton } from './fixtures/shell'

/**
 * NCL-01-CN-005 — Hồ sơ và tùy chọn học tập cá nhân (Jira LC-22).
 * Cài đặt mở từ trình đơn tài khoản (đáy thanh bên trên máy tính, ảnh đại diện đầu trang trên điện thoại).
 */

// Ảnh PNG 2×2 điểm ảnh thật để thử tải ảnh đại diện.
const PNG_2X2 = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFklEQVR4nGP8z8Dwn4GBgYGJgYGBAQAm/wH/4dJ6RAAAAABJRU5ErkJggg==',
  'base64',
)

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

async function openSettings(page: Page) {
  await page.getByRole('button', { name: /Trình đơn tài khoản/ }).first().click()
  await page.getByRole('menuitem', { name: 'Cài đặt' }).click()
  const dialog = page.getByRole('dialog', { name: 'Cài đặt' })
  await expect(dialog).toBeVisible()
  await expect(dialog.getByRole('heading', { name: 'Hồ sơ và tùy chọn học tập' })).toBeVisible()
  return dialog
}

async function profileId(page: Page) {
  return (await (await page.request.get('/api/v1/profiles/me')).json()).id as string
}

test.describe('NCL-01-CN-005 · Hồ sơ và tùy chọn học tập', () => {
  test('TC-01: chọn múi giờ Việt Nam và mục tiêu ba mươi phút thì hồ sơ được lưu, mọi mốc thời gian theo giờ Việt Nam', async ({
    browser,
    dev,
  }) => {
    // Máy đang đặt múi giờ New York để thấy rõ thời gian hiển thị theo hồ sơ chứ không theo thiết bị.
    const page = await (await browser.newContext({ timezoneId: 'America/New_York', locale: 'vi-VN' })).newPage()
    const email = uniqueEmail('mui-gio')
    await createVerifiedAccount(page, dev, email)
    await dev.sampleDocument(email, 'Ghi chú múi giờ')

    const dialog = await openSettings(page)
    await expect(dialog.getByTestId('time-zone-suggestion')).toContainText('New York')
    await dialog.getByLabel('Múi giờ').selectOption('Asia/Ho_Chi_Minh')
    await expect(dialog.getByTestId('time-zone-notice')).toContainText('Hồ Chí Minh (Việt Nam)')
    await dialog.getByRole('button', { name: '30 phút' }).click()
    await expect(dialog.getByRole('button', { name: '30 phút' })).toHaveAttribute('aria-pressed', 'true')
    await expect(dialog.getByTestId('save-status')).toHaveText('Đã lưu')
    await expectNoSeriousA11yViolations(page)

    // Tải lại trang: cài đặt mở lại đúng chỗ và giá trị vẫn còn.
    await page.reload()
    const reopened = page.getByRole('dialog', { name: 'Cài đặt' })
    await expect(reopened.getByLabel('Múi giờ')).toHaveValue('Asia/Ho_Chi_Minh')
    await expect(reopened.getByRole('button', { name: '30 phút' })).toHaveAttribute('aria-pressed', 'true')
    await page.keyboard.press('Escape')
    await expect(reopened).toBeHidden()

    const [document] = await (await page.request.get('/api/v1/library/documents')).json()
    const [inVietnam, inNewYork] = await page.evaluate(
      (createdAt) =>
        ['Asia/Ho_Chi_Minh', 'America/New_York'].map((timeZone) =>
          new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short', timeZone }).format(
            new Date(createdAt),
          ),
        ),
      document.createdAt as string,
    )
    await page.goto('/library')
    await expect(page.getByTestId('document-card')).toContainText(inVietnam)
    await expect(page.getByTestId('document-card')).not.toContainText(inNewYork)
  })

  test('TC-02: tên hiển thị sáu mươi ký tự bị báo tối đa năm mươi ký tự và không được lưu', async ({ page, dev }) => {
    await createVerifiedAccount(page, dev, uniqueEmail('ten-dai'))
    const dialog = await openSettings(page)
    const name = dialog.getByLabel('Tên hiển thị')

    await name.fill('Đ'.repeat(60))
    await name.blur()

    await expect(dialog.getByText('Tên hiển thị tối đa 50 ký tự.')).toBeVisible()
    await expect(dialog.getByTestId('name-count')).toHaveText('60/50 ký tự')
    await page.reload()
    await expect(accountMenuButton(page)).toHaveAccessibleName('Trình đơn tài khoản của Lan Anh')

    // Tên hợp lệ thì lưu ngay và đầu trang đổi theo.
    const again = page.getByRole('dialog', { name: 'Cài đặt' })
    await again.getByLabel('Tên hiển thị').fill('Nguyễn Lan Anh')
    await again.getByLabel('Tên hiển thị').press('Enter')
    await expect(again.getByTestId('save-status')).toHaveText('Đã lưu')
    await expect(accountMenuButton(page)).toHaveAccessibleName('Trình đơn tài khoản của Nguyễn Lan Anh')
  })

  test('TC-03: tệp văn bản đổi đuôi bị từ chối; ảnh thật được cắt vuông và hiện ngay', async ({ page, dev }) => {
    await createVerifiedAccount(page, dev, uniqueEmail('anh'))
    const dialog = await openSettings(page)

    await dialog.getByTestId('avatar-input').setInputFiles({
      name: 'anh-dai-dien.png',
      mimeType: 'image/png',
      buffer: Buffer.from('Ghi chú học tập, không phải ảnh'),
    })
    await expect(dialog.getByTestId('avatar-error')).toHaveText('Ảnh đại diện cần là tệp ảnh JPG, PNG hoặc WebP.')

    await dialog.getByTestId('avatar-input').setInputFiles({ name: 'chan-dung.png', mimeType: 'image/png', buffer: PNG_2X2 })
    const cropper = page.getByRole('dialog', { name: 'Cắt ảnh đại diện' })
    await expect(cropper).toBeVisible()
    await expectNoSeriousA11yViolations(page)
    await cropper.getByTestId('avatar-crop-area').press('ArrowLeft')
    await cropper.getByRole('button', { name: 'Dùng ảnh này' }).click()

    await expect(cropper).toBeHidden()
    await expect(dialog.getByTestId('avatar-error')).toBeHidden()
    await expect(dialog.getByTestId('avatar-image')).toBeVisible()
    await expect(page.getByRole('button', { name: /Trình đơn tài khoản/ }).first().getByTestId('avatar-image')).toBeVisible()

    await dialog.getByRole('button', { name: 'Gỡ ảnh' }).click()
    await expect(dialog.getByTestId('avatar-image')).toBeHidden()
  })

  test('TC-04: đổi từ giờ Việt Nam sang múi giờ chậm hơn bảy giờ thì báo các mốc sẽ hiển thị theo múi giờ mới', async ({
    page,
    dev,
  }) => {
    const email = uniqueEmail('du-hoc')
    await createVerifiedAccount(page, dev, email)
    await dev.sampleDocument(email)
    const dialog = await openSettings(page)
    await dialog.getByLabel('Múi giờ').selectOption('Asia/Ho_Chi_Minh')
    await expect(dialog.getByTestId('save-status')).toHaveText('Đã lưu')

    await dialog.getByLabel('Múi giờ').selectOption('Africa/Abidjan')

    const notice = dialog.getByTestId('time-zone-notice')
    await expect(notice).toContainText('Từ giờ mọi mốc thời gian hiển thị theo múi giờ (GMT+00:00) Abidjan.')
    await expect(notice).toContainText('Lịch học và lời nhắc được tính lại theo múi giờ mới.')
    await expect(dialog.getByTestId('save-status')).toHaveText('Đã lưu')
    expect((await (await page.request.get('/api/v1/profiles/me')).json()).timeZone).toBe('Africa/Abidjan')

    await page.keyboard.press('Escape')
    const [document] = await (await page.request.get('/api/v1/library/documents')).json()
    const expected = await page.evaluate(
      (createdAt) =>
        new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Africa/Abidjan' }).format(
          new Date(createdAt),
        ),
      document.createdAt as string,
    )
    await page.goto('/library')
    await expect(page.getByTestId('document-card')).toContainText(expected)
  })

  test('TC-05: người học khác biết mã hồ sơ gửi yêu cầu sửa thì nhận không tìm thấy', async ({ page, browser, dev }) => {
    await createVerifiedAccount(page, dev, uniqueEmail('chu-ho-so'))
    const id = await profileId(page)

    const stranger = await (await browser.newContext()).newPage()
    await createVerifiedAccount(stranger, dev, uniqueEmail('la'))
    const xsrf = (await stranger.context().cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')?.value ?? ''
    const response = await stranger.request.patch(`/api/v1/profiles/${id}`, {
      data: { displayName: 'Kẻ mạo danh', dailyGoalMinutes: 5 },
      headers: { 'X-XSRF-TOKEN': xsrf },
    })
    expect(response.status()).toBe(404)
    expect((await response.json()).code).toBe('NOT_FOUND')

    const mine = await (await page.request.get('/api/v1/profiles/me')).json()
    expect(mine.displayName).toBe('Lan Anh')
    expect(mine.dailyGoalMinutes).toBe(30)
  })

  test('Trình đơn tài khoản mở và đóng bằng bàn phím', async ({ page, dev, isMobile }) => {
    test.skip(isMobile, 'Thiết bị cảm ứng không có bàn phím vật lý')
    await createVerifiedAccount(page, dev, uniqueEmail('ban-phim'))
    const trigger = page.getByTestId('account-menu-sidebar')

    await trigger.focus()
    await page.keyboard.press('Enter')
    await expect(page.getByRole('menuitem', { name: 'Cài đặt' })).toBeFocused()
    await page.keyboard.press('Escape')
    await expect(trigger).toBeFocused()
    await page.keyboard.press('Enter')
    await page.keyboard.press('Enter')
    await expect(page.getByRole('dialog', { name: 'Cài đặt' })).toBeVisible()
    await expect(page).toHaveURL(/settings=profile/)
    await page.keyboard.press('Escape')
    await expect(page).not.toHaveURL(/settings=/)
  })
})
