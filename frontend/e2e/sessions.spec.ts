import AxeBuilder from '@axe-core/playwright'
import type { Page } from '@playwright/test'
import { createVerifiedAccount, expect, loginThroughUi, test } from './fixtures/auth'
import { STRONG_PASSWORD, uniqueEmail } from './fixtures/testData'

/**
 * NCL-01-CN-002 — Đăng nhập và quản lý phiên trên nhiều thiết bị (Jira LC-16).
 * Mỗi "thiết bị" là một ngữ cảnh trình duyệt riêng (cookie riêng), như điện thoại và máy ở thư viện.
 */

async function expectNoSeriousA11yViolations(page: Page) {
  // Tắt hiệu ứng hiện dần để axe đo độ tương phản ở trạng thái cuối.
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

async function logout(page: Page) {
  await page.goto('/')
  await page.getByRole('button', { name: 'Đăng xuất' }).first().click()
  await expect(page).toHaveURL(/\/login\?loggedOut=1$/)
}

test.describe('NCL-01-CN-002 · Đăng nhập và quản lý phiên', () => {
  test('TC-01: đăng nhập có ghi nhớ thiết bị thì vào ứng dụng và phiên có hiệu lực ba mươi ngày', async ({
    page,
    dev,
  }) => {
    const email = uniqueEmail()
    await createVerifiedAccount(page, dev, email)
    await logout(page)

    await loginThroughUi(page, email, STRONG_PASSWORD, true)

    await expect(page).toHaveURL(/\/$/)
    await expect(page.getByTestId('home-greeting')).toBeVisible()
    const cookie = (await page.context().cookies()).find((item) => item.name === 'lc_session')
    expect(cookie?.httpOnly).toBe(true)
    const daysLeft = ((cookie?.expires ?? 0) * 1000 - Date.now()) / 86_400_000
    expect(daysLeft).toBeGreaterThan(29.9)
    expect(daysLeft).toBeLessThanOrEqual(30)

    await page.goto('/devices')
    await expect(page.getByTestId('device-row').first()).toContainText('Ghi nhớ 30 ngày')
    await expect(page.getByTestId('device-row').first()).toContainText('Thiết bị này')

    // Hai mươi chín ngày sau vẫn còn đăng nhập.
    await dev.advanceClock(29 * 86_400)
    await page.reload()
    await expect(page.getByRole('heading', { level: 1, name: 'Thiết bị đang đăng nhập' })).toBeVisible()
  })

  test('TC-02: sai mật khẩu năm lần liên tiếp thì tạm khóa mười lăm phút, báo thời gian chờ còn lại', async ({
    page,
    dev,
  }) => {
    const email = uniqueEmail()
    await createVerifiedAccount(page, dev, email)
    await logout(page)
    await page.clock.install()
    await page.goto('/login')

    const passwordField = page.getByLabel('Mật khẩu', { exact: true })
    await page.getByLabel('Thư điện tử').fill(email)
    for (let attempt = 1; attempt <= 3; attempt++) {
      await passwordField.fill(`sai-lan-${attempt}`)
      await page.getByRole('button', { name: /Đăng nhập/ }).click()
      await expect(page.getByTestId('login-error')).toContainText('Thư điện tử hoặc mật khẩu chưa đúng.')
      await expect(passwordField).toHaveValue('')
      await expect(passwordField).toBeFocused()
    }
    await expect(page.getByTestId('attempts-left')).toHaveText(
      'Còn 2 lần thử trước khi đăng nhập bị tạm khóa 15 phút.',
    )
    await passwordField.fill('sai-lan-4')
    await page.getByRole('button', { name: /Đăng nhập/ }).click()
    await expect(page.getByTestId('attempts-left')).toContainText('Còn 1 lần thử')
    await passwordField.fill('sai-lan-5')
    await page.getByRole('button', { name: /Đăng nhập/ }).click()

    const notice = page.getByTestId('lockout-notice')
    await expect(notice).toContainText('Đăng nhập đang tạm khóa')
    await expect(page.getByTestId('lockout-countdown')).toHaveText(/1[45]:\d\d/)
    await expect(passwordField).toBeDisabled()
    await expect(page.getByRole('button', { name: /Đăng nhập/ })).toBeDisabled()
    await expectNoSeriousA11yViolations(page)
    await dev.waitForMail(email, 'ACCOUNT_LOCKED')

    // Lần thứ sáu, kể cả đúng mật khẩu, vẫn bị khóa và máy chủ báo thời gian chờ còn lại.
    await dev.advanceClock(5 * 60)
    await page.reload()
    await loginThroughUi(page, email)
    await expect(page.getByTestId('lockout-countdown')).toHaveText(/(9|10):\d\d/)

    // Hết mười lăm phút thì mở lại.
    await dev.advanceClock(10 * 60 + 5)
    await page.clock.fastForward('10:05')
    await expect(page.getByText('Hết thời gian khóa, bạn có thể đăng nhập lại.')).toBeVisible()
    await expect(passwordField).toBeEnabled()
    await passwordField.fill(STRONG_PASSWORD)
    await page.getByRole('button', { name: /Đăng nhập/ }).click()
    await expect(page.getByTestId('home-greeting')).toBeVisible()
  })

  test('TC-03: đăng nhập từ thiết bị mới thì nhận thư cảnh báo, bấm nút trong thư để đăng xuất thiết bị đó', async ({
    page,
    browser,
    dev,
  }) => {
    const email = uniqueEmail()
    await createVerifiedAccount(page, dev, email)

    const library = await (await browser.newContext()).newPage()
    await loginThroughUi(library, email)
    await expect(library.getByTestId('home-greeting')).toBeVisible()

    const alert = await dev.waitForMail(email, 'NEW_DEVICE_LOGIN')
    expect(alert.subject).toBe('Tài khoản của bạn vừa đăng nhập trên thiết bị mới')
    const revokeLink = alert.links.find((link) => link.includes('/devices/revoke?token='))
    expect(revokeLink).toBeTruthy()

    // Bấm nút trong thư từ một trình duyệt bất kỳ, không cần đăng nhập.
    const inbox = await (await browser.newContext()).newPage()
    await inbox.goto(revokeLink!)
    await expect(inbox.getByRole('heading', { name: 'Đã đăng xuất thiết bị đó' })).toBeVisible()
    await expect(inbox.getByText(/đổi sang một mật khẩu mới/)).toBeVisible()
    await expect(inbox).toHaveURL(/\/devices\/revoke$/)

    // Thiết bị ở thư viện bị đăng xuất ở lần thao tác kế tiếp, thiết bị gốc vẫn đăng nhập.
    await library.getByRole('link', { name: 'Thiết bị' }).first().click()
    await expect(library).toHaveURL(/\/login$/)
    await expect(library.getByTestId('session-notice')).toContainText('đã bị đăng xuất từ một thiết bị khác')
    await page.goto('/devices')
    await expect(page.getByTestId('device-row')).toHaveCount(1)
  })

  test('Đăng nhập lại trên thiết bị quen thì không gửi thư cảnh báo', async ({ page, dev }) => {
    const email = uniqueEmail()
    await createVerifiedAccount(page, dev, email)
    await logout(page)
    await loginThroughUi(page, email)
    await expect(page.getByTestId('home-greeting')).toBeVisible()

    await page.waitForTimeout(800)
    expect((await dev.mails(email)).filter((mail) => mail.template === 'NEW_DEVICE_LOGIN')).toHaveLength(0)
  })

  test('TC-04: đăng xuất máy tính ở thư viện từ điện thoại thì máy đó phải đăng nhập lại', async ({
    page,
    browser,
    dev,
  }) => {
    const email = uniqueEmail()
    await createVerifiedAccount(page, dev, email)
    const library = await (await browser.newContext()).newPage()
    await loginThroughUi(library, email)
    await expect(library.getByTestId('home-greeting')).toBeVisible()

    await page.goto('/devices')
    const rows = page.getByTestId('device-row')
    await expect(rows).toHaveCount(2)
    await expect(rows.first()).toHaveAttribute('data-current', 'true')
    await expect(rows.first()).toContainText('Đang hoạt động')
    await expect(rows.nth(1)).toContainText(/Mạng nội bộ|Không rõ vị trí/)

    await rows.nth(1).getByRole('button', { name: /Đăng xuất/ }).click()
    const dialog = page.getByRole('dialog')
    await expect(dialog).toContainText('sẽ phải đăng nhập lại ở lần thao tác kế tiếp')
    await expect(dialog.getByRole('button', { name: 'Hủy' })).toBeFocused()
    await expectNoSeriousA11yViolations(page)
    await dialog.getByRole('button', { name: 'Đăng xuất' }).click()
    await expect(page.getByTestId('devices-notice')).toContainText('Đã đăng xuất')
    await expect(rows).toHaveCount(1)

    await library.getByRole('link', { name: 'Thiết bị' }).first().click()
    await expect(library).toHaveURL(/\/login$/)
    await expect(library.getByTestId('session-notice')).toContainText('đã bị đăng xuất từ một thiết bị khác')
  })

  test('Đăng xuất mọi thiết bị khác giữ lại thiết bị đang dùng', async ({ page, browser, dev }) => {
    const email = uniqueEmail()
    await createVerifiedAccount(page, dev, email)
    for (let i = 0; i < 2; i++) {
      const other = await (await browser.newContext()).newPage()
      await loginThroughUi(other, email)
      await expect(other.getByTestId('home-greeting')).toBeVisible()
    }

    await page.goto('/devices')
    await expect(page.getByTestId('device-row')).toHaveCount(3)
    await page.getByTestId('revoke-others').click()
    await expect(page.getByRole('dialog')).toContainText('2 thiết bị khác sẽ phải đăng nhập lại')
    await page.getByRole('dialog').getByRole('button', { name: 'Đăng xuất' }).click()

    await expect(page.getByTestId('devices-notice')).toHaveText('Đã đăng xuất 2 thiết bị khác.')
    await expect(page.getByTestId('device-row')).toHaveCount(1)
    await expect(page.getByTestId('revoke-others')).toBeDisabled()
  })

  test('TC-05: không ghi nhớ thiết bị và để hai giờ không thao tác thì phiên hết hạn, quay về đăng nhập', async ({
    page,
    dev,
  }) => {
    const email = uniqueEmail()
    await createVerifiedAccount(page, dev, email)
    await logout(page)
    await loginThroughUi(page, email)
    await expect(page.getByTestId('home-greeting')).toBeVisible()
    const cookie = (await page.context().cookies()).find((item) => item.name === 'lc_session')
    expect(cookie?.expires).toBe(-1)

    await dev.advanceClock(2 * 3600 + 60)
    await page.getByRole('link', { name: 'Thiết bị' }).first().click()

    await expect(page).toHaveURL(/\/login$/)
    await expect(page.getByTestId('session-notice')).toContainText('Phiên đăng nhập đã hết hạn')

    // Đăng nhập lại thì quay về đúng trang đang mở.
    await page.getByLabel('Thư điện tử').fill(email)
    await page.getByLabel('Mật khẩu', { exact: true }).fill(STRONG_PASSWORD)
    await page.getByRole('button', { name: /Đăng nhập/ }).click()
    await expect(page).toHaveURL(/\/devices$/)
  })

  test('Chưa đăng nhập mà mở trang thiết bị thì đăng nhập xong quay lại đúng trang đó', async ({ page, dev }) => {
    const email = uniqueEmail()
    await createVerifiedAccount(page, dev, email)
    await logout(page)

    await page.goto('/devices')
    await expect(page).toHaveURL(/\/login$/)
    await loginThroughUi(page, email)
    await expect(page.getByRole('heading', { level: 1, name: 'Thiết bị đang đăng nhập' })).toBeVisible()
  })

  test('Tài khoản chưa xác thực được nhắc và gửi lại thư xác thực từ màn hình đăng nhập', async ({ page, dev }) => {
    const email = uniqueEmail()
    await page.goto('/register')
    await page.getByLabel('Tên hiển thị').fill('Lan Anh')
    await page.getByLabel('Thư điện tử').fill(email)
    await page.getByLabel('Mật khẩu', { exact: true }).fill(STRONG_PASSWORD)
    await page.getByLabel('Nhập lại mật khẩu').fill(STRONG_PASSWORD)
    await page.getByRole('checkbox').check()
    await page.getByRole('button', { name: /Tạo tài khoản/ }).click()
    await expect(page).toHaveURL(/check-email$/)

    await dev.advanceClock(61)
    await loginThroughUi(page, email)
    await expect(page.getByTestId('login-error')).toContainText('chưa được xác thực')
    await page.getByRole('button', { name: 'Gửi lại thư xác thực' }).click()
    await expect(page).toHaveURL(/check-email$/)
    await dev.waitForMail(email, 'VERIFY_EMAIL', 2)
  })

  test('Đăng nhập chỉ bằng bàn phím', async ({ page, dev, isMobile }) => {
    test.skip(isMobile, 'Thiết bị cảm ứng không có bàn phím vật lý')
    const email = uniqueEmail()
    await createVerifiedAccount(page, dev, email)
    await logout(page)
    await page.goto('/login')
    await expectNoSeriousA11yViolations(page)

    await page.getByLabel('Thư điện tử').focus()
    await page.keyboard.type(email)
    await page.keyboard.press('Tab')
    await page.keyboard.type(STRONG_PASSWORD)
    await page.keyboard.press('Tab')
    await expect(page.getByRole('button', { name: 'Hiện mật khẩu' })).toBeFocused()
    await page.keyboard.press('Tab')
    await expect(page.getByRole('checkbox', { name: 'Ghi nhớ thiết bị này' })).toBeFocused()
    await page.keyboard.press('Space')
    await expect(page.getByRole('checkbox', { name: 'Ghi nhớ thiết bị này' })).toBeChecked()
    await page.keyboard.press('Tab')
    await expect(page.getByRole('button', { name: /Đăng nhập/ })).toBeFocused()
    await page.keyboard.press('Enter')
    await expect(page.getByTestId('home-greeting')).toBeVisible()
  })

  test('Trang thiết bị không có lỗi trợ năng nghiêm trọng', async ({ page, dev }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' })
    const email = uniqueEmail()
    await createVerifiedAccount(page, dev, email)
    await page.goto('/devices')
    await expect(page.getByTestId('device-row')).toHaveCount(1)
    await expectNoSeriousA11yViolations(page)
  })
})
