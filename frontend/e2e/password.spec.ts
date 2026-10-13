import AxeBuilder from '@axe-core/playwright'
import type { Browser, Page } from '@playwright/test'
import { createVerifiedAccount, expect, loginThroughUi, test } from './fixtures/auth'
import { STRONG_PASSWORD, uniqueEmail } from './fixtures/testData'
import { openAccountPage } from './fixtures/shell'

/**
 * NCL-01-CN-003 — Quên mật khẩu, đổi mật khẩu và đăng xuất khỏi mọi thiết bị (Jira LC-17).
 * Mỗi "thiết bị" là một ngữ cảnh trình duyệt riêng (cookie riêng).
 */

const NEW_PASSWORD = 'biển xanh gió đông 2027'

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

async function newDevice(browser: Browser, email: string) {
  const device = await (await browser.newContext()).newPage()
  await loginThroughUi(device, email)
  await expect(device.getByTestId('home-greeting')).toBeVisible()
  return device
}

/** Thiết bị đã bị đăng xuất: lần chuyển trang kế tiếp đưa về màn hình đăng nhập kèm lý do. */
async function expectSignedOut(device: Page) {
  await device.reload()
  await expect(device).toHaveURL(/\/login$/)
  await expect(device.getByTestId('session-notice')).toContainText('đã bị đăng xuất từ một thiết bị khác')
}

async function fillNewPassword(page: Page, password = NEW_PASSWORD) {
  await page.getByLabel('Mật khẩu mới', { exact: true }).fill(password)
  await page.getByLabel('Nhập lại mật khẩu mới').fill(password)
}

test.describe('NCL-01-CN-003 · Quên mật khẩu', () => {
  test('TC-01: quên mật khẩu, mở thư, đặt mật khẩu mới thì mọi thiết bị bị đăng xuất và có thư xác nhận', async ({
    page,
    browser,
    dev,
  }) => {
    const email = uniqueEmail('quen')
    await createVerifiedAccount(page, dev, email)
    const laptop = await newDevice(browser, email)

    // Ở một trình duyệt khác: gõ địa chỉ ở màn hình đăng nhập rồi bấm "Quên mật khẩu?" thì địa chỉ đi theo.
    const visitor = await (await browser.newContext()).newPage()
    await visitor.goto('/login')
    await visitor.getByLabel('Thư điện tử').fill(email)
    await visitor.getByRole('link', { name: 'Quên mật khẩu?' }).click()
    await expect(visitor).toHaveURL(/\/forgot-password$/)
    await expect(visitor.getByLabel('Thư điện tử')).toHaveValue(email)
    await visitor.getByRole('button', { name: /Gửi hướng dẫn/ }).click()

    await expect(visitor.getByRole('heading', { name: 'Kiểm tra hộp thư của bạn' })).toBeVisible()
    await expect(visitor.getByTestId('forgot-sent-body')).toContainText(email)
    await expect(visitor.getByTestId('forgot-resend')).toBeDisabled()
    // Tải lại trang vẫn giữ màn hình đã gửi.
    await visitor.reload()
    await expect(visitor.getByTestId('forgot-sent-body')).toContainText(email)

    const mail = await dev.waitForMail(email, 'PASSWORD_RESET')
    expect(mail.subject).toBe('Đặt lại mật khẩu Learning Companion')
    const link = await dev.resetLink(email)

    await visitor.goto(link)
    await expect(visitor.getByRole('heading', { name: 'Đặt mật khẩu mới' })).toBeVisible()
    // Chuỗi bí mật không còn trên thanh địa chỉ.
    await expect(visitor).toHaveURL(/\/reset-password$/)
    await fillNewPassword(visitor)
    await visitor.getByRole('button', { name: /Lưu mật khẩu mới/ }).click()

    await expect(visitor.getByRole('heading', { name: 'Đã đặt mật khẩu mới' })).toBeVisible()
    const confirmation = await dev.waitForMail(email, 'PASSWORD_CHANGED')
    expect(confirmation.subject).toBe('Mật khẩu tài khoản của bạn vừa được thay đổi')

    // Mọi phiên trước đó đều bị đăng xuất.
    await expectSignedOut(page)
    await expectSignedOut(laptop)

    // Sang đăng nhập với địa chỉ điền sẵn, con trỏ ở ô mật khẩu.
    await visitor.getByRole('button', { name: /Đăng nhập bằng mật khẩu mới/ }).click()
    await expect(visitor).toHaveURL(/\/login$/)
    await expect(visitor.getByTestId('session-notice')).toContainText('Mật khẩu đã được đặt lại')
    await expect(visitor.getByLabel('Thư điện tử')).toHaveValue(email)
    await expect(visitor.getByLabel('Mật khẩu', { exact: true })).toBeFocused()

    // Mật khẩu cũ hết dùng được, mật khẩu mới vào được.
    await visitor.getByLabel('Mật khẩu', { exact: true }).fill(STRONG_PASSWORD)
    await visitor.getByRole('button', { name: /Đăng nhập/ }).click()
    await expect(visitor.getByTestId('login-error')).toContainText('Thư điện tử hoặc mật khẩu chưa đúng.')
    await visitor.getByLabel('Mật khẩu', { exact: true }).fill(NEW_PASSWORD)
    await visitor.getByRole('button', { name: /Đăng nhập/ }).click()
    await expect(visitor.getByTestId('home-greeting')).toBeVisible()

    // Đường dẫn chỉ dùng được một lần.
    await visitor.goto(link)
    await expect(visitor.getByRole('heading', { name: 'Đường dẫn không còn dùng được' })).toBeVisible()
  })

  test('Địa chỉ chưa đăng ký nhận đúng màn hình như địa chỉ có tài khoản', async ({ page }) => {
    const email = uniqueEmail('khong-co')
    await page.goto('/forgot-password')
    await page.getByLabel('Thư điện tử').fill(email)
    await page.getByRole('button', { name: /Gửi hướng dẫn/ }).click()

    await expect(page.getByRole('heading', { name: 'Kiểm tra hộp thư của bạn' })).toBeVisible()
    await expect(page.getByTestId('forgot-sent-body')).toHaveText(
      `Nếu ${email} có tài khoản Learning Companion, hướng dẫn đặt lại mật khẩu đã được gửi tới địa chỉ này.`,
    )
  })

  test('TC-02: đường dẫn quá 30 phút báo hết hạn và yêu cầu được đường dẫn mới ngay tại đó', async ({ page, dev }) => {
    const email = uniqueEmail('het-han')
    await createVerifiedAccount(page, dev, email)
    const visitor = await (await page.context().browser()!.newContext()).newPage()
    await visitor.goto('/forgot-password')
    await visitor.getByLabel('Thư điện tử').fill(email)
    await visitor.getByRole('button', { name: /Gửi hướng dẫn/ }).click()
    const expiredLink = await dev.resetLink(email)

    await dev.advanceClock(31 * 60)
    await visitor.goto(expiredLink)
    await expect(visitor.getByRole('heading', { name: 'Đường dẫn đã hết hạn' })).toBeVisible()
    await visitor.getByRole('button', { name: /Gửi đường dẫn mới/ }).click()
    await expect(visitor.getByTestId('reset-resent')).toContainText('Đã gửi đường dẫn mới')

    const freshLink = await dev.resetLink(email, 2)
    expect(freshLink).not.toBe(expiredLink)
    await visitor.goto(freshLink)
    await expect(visitor.getByRole('heading', { name: 'Đặt mật khẩu mới' })).toBeVisible()
    await expectNoSeriousA11yViolations(visitor)

    // Mật khẩu mới phải khác mật khẩu cũ.
    await fillNewPassword(visitor, STRONG_PASSWORD)
    await visitor.getByRole('button', { name: /Lưu mật khẩu mới/ }).click()
    await expect(visitor.getByText('Mật khẩu mới cần khác mật khẩu đang dùng.')).toBeVisible()
    await expect(visitor.getByLabel('Mật khẩu mới', { exact: true })).toBeFocused()
  })

  test('Đang bị khóa đăng nhập thì có lối đặt lại mật khẩu để mở khóa ngay', async ({ page, dev }) => {
    const email = uniqueEmail('khoa')
    await createVerifiedAccount(page, dev, email)
    const visitor = await (await page.context().browser()!.newContext()).newPage()
    await visitor.goto('/login')
    await visitor.getByLabel('Thư điện tử').fill(email)
    for (let attempt = 1; attempt <= 5; attempt++) {
      await visitor.getByLabel('Mật khẩu', { exact: true }).fill(`sai-lan-${attempt}`)
      await visitor.getByRole('button', { name: /Đăng nhập/ }).click()
      await expect(visitor.getByLabel('Mật khẩu', { exact: true })).toHaveValue('')
    }
    await expect(visitor.getByTestId('lockout-notice')).toBeVisible()

    await visitor.getByRole('link', { name: /Đặt lại để mở khóa ngay/ }).click()
    await expect(visitor.getByLabel('Thư điện tử')).toHaveValue(email)
    await visitor.getByRole('button', { name: /Gửi hướng dẫn/ }).click()
    await visitor.goto(await dev.resetLink(email))
    await fillNewPassword(visitor)
    await visitor.getByRole('button', { name: /Lưu mật khẩu mới/ }).click()
    await visitor.getByRole('button', { name: /Đăng nhập bằng mật khẩu mới/ }).click()
    await visitor.getByLabel('Mật khẩu', { exact: true }).fill(NEW_PASSWORD)
    await visitor.getByRole('button', { name: /Đăng nhập/ }).click()
    await expect(visitor.getByTestId('home-greeting')).toBeVisible()
  })

  test('Màn hình quên mật khẩu đạt chuẩn trợ năng', async ({ page }) => {
    await page.goto('/forgot-password')
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Quên mật khẩu?')
    await expectNoSeriousA11yViolations(page)
    await page.getByLabel('Thư điện tử').fill(uniqueEmail('a11y'))
    await page.getByRole('button', { name: /Gửi hướng dẫn/ }).click()
    await expect(page.getByRole('heading', { name: 'Kiểm tra hộp thư của bạn' })).toBeVisible()
    await expectNoSeriousA11yViolations(page)
  })
})

test.describe('NCL-01-CN-003 · Đổi mật khẩu và đăng xuất khỏi mọi thiết bị', () => {
  test('Đổi mật khẩu thì các thiết bị khác bị đăng xuất, thiết bị này vẫn dùng tiếp và có thư xác nhận', async ({
    page,
    browser,
    dev,
  }) => {
    const email = uniqueEmail('doi')
    await createVerifiedAccount(page, dev, email)
    const laptop = await newDevice(browser, email)

    await page.goto('/')
    await openAccountPage(page, 'Bảo mật')
    await expect(page.getByRole('heading', { level: 1, name: 'Bảo mật tài khoản' })).toBeVisible()
    await expect(page.getByTestId('password-changed-at')).toHaveText('Chưa đổi mật khẩu kể từ khi tạo tài khoản')
    await expectNoSeriousA11yViolations(page)

    await page.getByLabel('Mật khẩu hiện tại').fill(STRONG_PASSWORD)
    await fillNewPassword(page)
    await page.getByRole('button', { name: 'Đổi mật khẩu' }).click()

    await expect(page.getByTestId('change-password-success')).toContainText('đăng xuất 1 thiết bị khác')
    await expect(page.getByLabel('Mật khẩu hiện tại')).toHaveValue('')
    await expect(page.getByTestId('password-changed-at')).toContainText('Đổi mật khẩu lần cuối')
    await expect(page.getByTestId('other-sessions-count')).toHaveText('Không có thiết bị nào khác đang đăng nhập.')
    expect((await dev.waitForMail(email, 'PASSWORD_CHANGED')).subject).toBe(
      'Mật khẩu tài khoản của bạn vừa được thay đổi',
    )

    await expectSignedOut(laptop)
    await page.reload()
    await expect(page.getByRole('heading', { level: 1, name: 'Bảo mật tài khoản' })).toBeVisible()
    await loginThroughUi(laptop, email, NEW_PASSWORD)
    await expect(laptop.getByTestId('home-greeting')).toBeVisible()
  })

  test('TC-03: sai mật khẩu hiện tại bị từ chối và tính là một lần nhập sai', async ({ page, dev }) => {
    const email = uniqueEmail('sai')
    await createVerifiedAccount(page, dev, email)
    await page.goto('/security')

    const current = page.getByLabel('Mật khẩu hiện tại')
    await fillNewPassword(page)
    for (let attempt = 1; attempt <= 3; attempt++) {
      await current.fill(`sai-mat-khau-${attempt}`)
      await page.getByRole('button', { name: 'Đổi mật khẩu' }).click()
      await expect(page.getByText('Mật khẩu hiện tại chưa đúng.')).toBeVisible()
      await expect(current).toHaveValue('')
      await expect(current).toBeFocused()
    }
    // Ba lần sai được đếm như đăng nhập sai: còn hai lần trước khi khóa.
    await expect(page.getByTestId('change-attempts-left')).toHaveText(
      'Còn 2 lần thử trước khi tài khoản bị tạm khóa 15 phút.',
    )
    // Mật khẩu mới vẫn được giữ, không phải gõ lại.
    await expect(page.getByLabel('Mật khẩu mới', { exact: true })).toHaveValue(NEW_PASSWORD)

    for (let attempt = 4; attempt <= 5; attempt++) {
      await current.fill(`sai-mat-khau-${attempt}`)
      await page.getByRole('button', { name: 'Đổi mật khẩu' }).click()
    }
    await expect(page.getByTestId('lockout-notice')).toContainText('Tạm khóa đổi mật khẩu')
    await expect(current).toBeDisabled()
    // Thiết bị này vẫn đăng nhập, chỉ thao tác đổi mật khẩu bị chặn.
    await page.reload()
    await expect(page.getByRole('heading', { level: 1, name: 'Bảo mật tài khoản' })).toBeVisible()
  })

  test('TC-04: đăng nhập trên ba thiết bị, "Đăng xuất khỏi mọi thiết bị" thu hồi hai thiết bị kia và giữ thiết bị này', async ({
    page,
    browser,
    dev,
  }) => {
    const email = uniqueEmail('ba-may')
    await createVerifiedAccount(page, dev, email)
    const laptop = await newDevice(browser, email)
    const tablet = await newDevice(browser, email)

    await page.goto('/security')
    await expect(page.getByTestId('other-sessions-count')).toHaveText('2 thiết bị khác đang đăng nhập')
    await page.getByTestId('sign-out-everywhere').click()
    const dialog = page.getByRole('dialog', { name: 'Đăng xuất mọi thiết bị khác?' })
    await expect(dialog).toContainText('2 thiết bị khác sẽ phải đăng nhập lại.')
    await expect(dialog.getByRole('button', { name: 'Hủy' })).toBeFocused()
    await dialog.getByRole('button', { name: 'Đăng xuất' }).click()

    await expect(page.getByTestId('security-notice')).toHaveText('Đã đăng xuất 2 thiết bị khác.')
    await expect(page.getByTestId('other-sessions-count')).toHaveText('Không có thiết bị nào khác đang đăng nhập.')
    await expect(page.getByTestId('sign-out-everywhere')).toBeDisabled()

    await expectSignedOut(laptop)
    await expectSignedOut(tablet)
    await page.reload()
    await expect(page.getByRole('heading', { level: 1, name: 'Bảo mật tài khoản' })).toBeVisible()
  })
})
