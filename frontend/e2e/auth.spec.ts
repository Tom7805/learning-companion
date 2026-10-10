import AxeBuilder from '@axe-core/playwright'
import type { Page } from '@playwright/test'
import { expect, fillRegisterForm, registerThroughUi, test } from './fixtures/auth'
import { BREACHED_PASSWORD, DISPLAY_NAME, STRONG_PASSWORD, uniqueEmail } from './fixtures/testData'

/**
 * NCL-01-CN-001 — Đăng ký tài khoản và xác thực thư điện tử (Jira LC-15).
 * Mỗi bài thao tác trên giao diện thật với máy chủ thật; TC-05 (nhật ký) được kiểm ở AuthControllerIT.
 */

async function expectNoSeriousA11yViolations(page: Page) {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .exclude('a[href="/dev/mailbox"]')
    .analyze()
  const serious = results.violations.filter((violation) => ['serious', 'critical'].includes(violation.impact ?? ''))
  expect(
    serious.map((violation) => `${violation.id}: ${violation.nodes.map((node) => node.target.join(' ')).join(', ')}`),
  ).toEqual([])
}

test.describe('NCL-01-CN-001 · Đăng ký và xác thực thư điện tử', () => {
  test('TC-01: đăng ký hợp lệ chuyển sang màn hình kiểm tra hộp thư và thư xác thực tới trong một phút', async ({
    page,
    dev,
  }) => {
    const email = uniqueEmail()
    await registerThroughUi(page, email)

    await expect(page.getByRole('heading', { name: 'Kiểm tra hộp thư của bạn' })).toBeVisible()
    await expect(page.getByTestId('check-email-body')).toContainText(email)
    await expect(page.getByTestId('resend-button')).toBeDisabled()
    await expect(page.getByTestId('resend-button')).toContainText(/Gửi lại sau 0:[0-5]\d|Gửi lại sau 1:00/)

    const mail = await dev.waitForMail(email, 'VERIFY_EMAIL')
    expect(mail.subject).toBe('Xác thực địa chỉ thư của bạn')
    expect(Date.now() - new Date(mail.sentAt).getTime()).toBeLessThan(60_000)
    expect(mail.links.some((link) => /\/verify-email\?token=[\w-]{40,}$/.test(link))).toBeTruthy()
  })

  test('TC-02: mật khẩu đã lộ bị từ chối, giải thích và gợi ý dùng cụm từ dài hơn', async ({ page, dev }) => {
    const email = uniqueEmail()
    await page.goto('/register')
    await fillRegisterForm(page, { email, password: BREACHED_PASSWORD })
    await page.getByRole('button', { name: /Tạo tài khoản/ }).click()

    const password = page.getByLabel('Mật khẩu', { exact: true })
    await expect(page.getByText(/đã từng bị lộ trong các vụ rò rỉ dữ liệu/)).toBeVisible()
    await expect(password).toHaveAttribute('aria-invalid', 'true')
    await expect(password).toBeFocused()
    await expect(page.getByText(/cụm từ dài hơn/).first()).toBeVisible()
    await expect(page.locator('li[data-state="fail"]')).toHaveText(/Không phải mật khẩu phổ biến đã bị lộ/)
    await expect(page).toHaveURL(/\/register$/)

    // Không tạo tài khoản nên cũng không có thư nào.
    await page.waitForTimeout(500)
    expect(await dev.mails(email)).toHaveLength(0)

    // Đổi sang cụm từ an toàn thì đăng ký được ngay, không phải điền lại các ô khác.
    await password.fill(STRONG_PASSWORD)
    await page.getByLabel('Nhập lại mật khẩu').fill(STRONG_PASSWORD)
    await page.getByRole('button', { name: /Tạo tài khoản/ }).click()
    await expect(page).toHaveURL(/\/register\/check-email$/)
  })

  test('TC-03: địa chỉ đã có tài khoản thấy màn hình giống hệt, còn chủ địa chỉ nhận thư báo có người thử đăng ký', async ({
    page,
    browser,
    dev,
  }) => {
    const owner = uniqueEmail('chu')
    await registerThroughUi(page, owner)
    await page.goto(await dev.verifyLink(owner))
    await expect(page.getByRole('heading', { name: 'Tài khoản đã được kích hoạt' })).toBeVisible()

    const stranger = await browser.newContext()
    const strangerPage = await stranger.newPage()
    const fresh = uniqueEmail('moi')
    await registerThroughUi(strangerPage, fresh)
    const freshScreen = (await strangerPage.locator('main').innerText()).replaceAll(fresh, '<email>')

    await registerThroughUi(strangerPage, owner)
    const duplicateScreen = (await strangerPage.locator('main').innerText()).replaceAll(owner, '<email>')
    expect(duplicateScreen).toBe(freshScreen)

    const notice = await dev.waitForMail(owner, 'REGISTRATION_ATTEMPT')
    expect(notice.subject).toBe('Có người vừa thử đăng ký bằng địa chỉ thư của bạn')
    expect((await dev.mails(owner)).filter((mail) => mail.template === 'VERIFY_EMAIL')).toHaveLength(1)
    await stranger.close()
  })

  test('TC-04: đường dẫn quá 24 giờ báo hết hạn, gửi lại được thư mới và đường dẫn mới kích hoạt được', async ({
    page,
    dev,
  }) => {
    const email = uniqueEmail()
    await registerThroughUi(page, email)
    const expiredLink = await dev.verifyLink(email)

    await dev.advanceClock(24 * 3600 + 5)
    await page.goto(expiredLink)

    await expect(page.getByRole('heading', { name: 'Đường dẫn đã hết hạn' })).toBeVisible()
    await expect(page.getByText(/chỉ có hiệu lực trong 24 giờ/)).toBeVisible()
    await page.getByRole('button', { name: /Gửi thư xác thực mới/ }).click()
    await expect(page.getByTestId('expired-resent')).toBeVisible()
    await expect(page.getByRole('button', { name: /Gửi lại sau/ })).toBeDisabled()

    const freshLink = await dev.verifyLink(email, 2)
    expect(freshLink).not.toBe(expiredLink)
    await page.goto(freshLink)
    await expect(page.getByRole('heading', { name: 'Tài khoản đã được kích hoạt' })).toBeVisible()
  })

  test('Xác thực xong được đăng nhập luôn, tự vào trang chính và đăng xuất được', async ({ page, dev }) => {
    const email = uniqueEmail()
    await registerThroughUi(page, email)
    await page.goto(await dev.verifyLink(email))

    await expect(page.getByRole('heading', { name: 'Tài khoản đã được kích hoạt' })).toBeVisible()
    await expect(page.getByText(`Chào mừng ${DISPLAY_NAME}!`, { exact: false })).toBeVisible()
    await expect(page).toHaveURL(/\/verify-email$/)
    await expect(page.getByTestId('redirect-countdown')).toContainText('giây')

    await expect(page).toHaveURL(/\/$/, { timeout: 10_000 })
    await expect(page.getByTestId('home-greeting')).toHaveText(`Chào ${DISPLAY_NAME}`)
    await expect(page.getByText(email).first()).toBeVisible()

    // Phiên nằm trong cookie HttpOnly: tải lại trang vẫn đăng nhập.
    await page.reload()
    await expect(page.getByTestId('home-greeting')).toBeVisible()
    const session = (await page.context().cookies()).find((cookie) => cookie.name === 'lc_session')
    expect(session?.httpOnly).toBe(true)
    expect(session?.sameSite).toBe('Lax')

    await page.getByRole('button', { name: 'Đăng xuất' }).first().click()
    await expect(page).toHaveURL(/\/login\?loggedOut=1$/)
    await expect(page.getByTestId('session-notice')).toHaveText('Bạn đã đăng xuất. Hẹn gặp lại!')
    await page.goto('/')
    await expect(page).toHaveURL(/\/login$/)
  })

  test('Đường dẫn chỉ dùng được một lần', async ({ page, dev, browser }) => {
    const email = uniqueEmail()
    await registerThroughUi(page, email)
    const link = await dev.verifyLink(email)
    await page.goto(link)
    await expect(page.getByRole('heading', { name: 'Tài khoản đã được kích hoạt' })).toBeVisible()

    // Mở lại trên thiết bị khác (chưa đăng nhập).
    const other = await browser.newContext()
    const otherPage = await other.newPage()
    await otherPage.goto(link)
    await expect(otherPage.getByRole('heading', { name: 'Đường dẫn này đã được dùng' })).toBeVisible()
    await expect(otherPage.getByRole('link', { name: 'Đi tới đăng nhập' })).toBeVisible()
    await other.close()

    // Mở lại trên chính thiết bị đã đăng nhập.
    await page.goto(link)
    await page.getByRole('link', { name: 'Vào ứng dụng' }).click()
    await expect(page.getByTestId('home-greeting')).toBeVisible()
  })

  test('Gửi lại thư bị khóa một phút, sau đó gửi được và đường dẫn cũ mất hiệu lực', async ({ page, dev }) => {
    await page.clock.install()
    const email = uniqueEmail()
    await registerThroughUi(page, email)
    const firstLink = await dev.verifyLink(email)
    const resend = page.getByTestId('resend-button')
    await expect(resend).toBeDisabled()

    await page.clock.fastForward('01:01')
    await dev.advanceClock(61)
    await expect(resend).toBeEnabled()
    await expect(resend).toHaveText(/Gửi lại thư/)
    await resend.click()

    await expect(page.getByTestId('resent-notice')).toBeVisible()
    await expect(resend).toBeDisabled()
    const secondLink = await dev.verifyLink(email, 2)
    expect(secondLink).not.toBe(firstLink)

    await page.goto(firstLink)
    await expect(page.getByRole('heading', { name: 'Đường dẫn không hợp lệ' })).toBeVisible()
    await page.goto(secondLink)
    await expect(page.getByRole('heading', { name: 'Tài khoản đã được kích hoạt' })).toBeVisible()
  })

  test('Gửi biểu mẫu trống báo lỗi từng trường và đưa tiêu điểm về trường đầu tiên', async ({ page }) => {
    await page.goto('/register')
    await page.getByRole('button', { name: /Tạo tài khoản/ }).click()

    await expect(page.getByTestId('error-summary')).toHaveText('Biểu mẫu còn 5 chỗ cần sửa.')
    await expect(page.getByLabel('Tên hiển thị')).toBeFocused()
    for (const message of [
      'Hãy nhập tên hiển thị.',
      'Hãy nhập địa chỉ thư điện tử.',
      'Hãy nhập mật khẩu.',
      'Hãy nhập lại mật khẩu.',
      'Bạn cần đồng ý điều khoản và chính sách quyền riêng tư để tạo tài khoản.',
    ]) {
      await expect(page.getByText(message)).toBeVisible()
    }

    // Sửa dần thì số lỗi giảm và dấu tích xanh xuất hiện.
    await page.getByLabel('Tên hiển thị').fill(DISPLAY_NAME)
    await expect(page.getByTestId('error-summary')).toHaveText('Biểu mẫu còn 4 chỗ cần sửa.')
    await expect(page.getByRole('img', { name: 'Hợp lệ' })).toHaveCount(1)
  })

  test('Hoàn thành đăng ký chỉ bằng bàn phím', async ({ page, isMobile }) => {
    test.skip(isMobile, 'Thiết bị cảm ứng không có bàn phím vật lý')
    const email = uniqueEmail()
    await page.goto('/register')
    await expect(page.getByTestId('terms-version')).toBeVisible()

    await page.keyboard.press('Tab')
    await expect(page.getByRole('link', { name: 'Bỏ qua tới nội dung chính' })).toBeFocused()
    await page.keyboard.press('Tab')
    await expect(page.getByLabel('Tên hiển thị')).toBeFocused()
    await page.keyboard.type(DISPLAY_NAME)
    await page.keyboard.press('Tab')
    await page.keyboard.type(email)
    await page.keyboard.press('Tab')
    await page.keyboard.type(STRONG_PASSWORD)
    await page.keyboard.press('Tab')
    await expect(page.getByRole('button', { name: 'Hiện mật khẩu' })).toBeFocused()
    await page.keyboard.press('Tab')
    await page.keyboard.type(STRONG_PASSWORD)
    await page.keyboard.press('Tab')
    await expect(page.getByRole('checkbox')).toBeFocused()
    await page.keyboard.press('Space')
    await expect(page.getByRole('checkbox')).toBeChecked()

    const submit = page.getByRole('button', { name: /Tạo tài khoản/ })
    for (let i = 0; i < 5 && !(await submit.evaluate((el) => el === document.activeElement)); i++) {
      await page.keyboard.press('Tab')
    }
    await expect(submit).toBeFocused()
    await page.keyboard.press('Enter')
    await expect(page).toHaveURL(/\/register\/check-email$/)
    await expect(page.getByRole('heading', { name: 'Kiểm tra hộp thư của bạn' })).toBeFocused()
  })

  test('Đổi địa chỉ thư quay lại biểu mẫu đã điền sẵn, không giữ mật khẩu', async ({ page }) => {
    const email = uniqueEmail()
    await registerThroughUi(page, email)
    await page.getByRole('button', { name: 'Đổi địa chỉ thư' }).click()

    await expect(page).toHaveURL(/\/register$/)
    await expect(page.getByLabel('Thư điện tử')).toHaveValue(email)
    await expect(page.getByLabel('Tên hiển thị')).toHaveValue(DISPLAY_NAME)
    await expect(page.getByLabel('Mật khẩu', { exact: true })).toHaveValue('')
  })

  test('Tải lại màn hình kiểm tra hộp thư vẫn giữ địa chỉ và thời gian chờ', async ({ page }) => {
    const email = uniqueEmail()
    await registerThroughUi(page, email)
    await page.reload()
    await expect(page.getByTestId('check-email-body')).toContainText(email)
    await expect(page.getByTestId('resend-button')).toBeDisabled()
  })

  test('Liên kết điều khoản và chính sách mở đúng phiên bản trong thẻ mới', async ({ page, context }) => {
    await page.goto('/register')
    const [terms] = await Promise.all([
      context.waitForEvent('page'),
      page.getByRole('link', { name: /Điều khoản sử dụng/ }).click(),
    ])
    await expect(terms.getByRole('heading', { name: 'Điều khoản sử dụng' })).toBeVisible()
    await expect(terms.getByTestId('legal-version')).toContainText('2026-10-01')
    await terms.close()
    await expect(page.getByRole('checkbox')).not.toBeChecked()
  })

  test('Không có lỗi trợ năng nghiêm trọng trên các màn hình của luồng', async ({ page, dev }) => {
    // Tắt hiệu ứng hiện dần để axe đo độ tương phản ở trạng thái cuối, không phải giữa lúc mờ dần.
    await page.emulateMedia({ reducedMotion: 'reduce' })
    await page.goto('/register')
    await expect(page.getByTestId('terms-version')).toBeVisible()
    await expectNoSeriousA11yViolations(page)

    await page.getByRole('button', { name: /Tạo tài khoản/ }).click()
    await expect(page.getByTestId('error-summary')).toBeVisible()
    await expectNoSeriousA11yViolations(page)

    const email = uniqueEmail()
    await registerThroughUi(page, email)
    await expectNoSeriousA11yViolations(page)

    const link = await dev.verifyLink(email)
    await dev.advanceClock(25 * 3600)
    await page.goto(link)
    await expect(page.getByRole('heading', { name: 'Đường dẫn đã hết hạn' })).toBeVisible()
    await expectNoSeriousA11yViolations(page)

    await page.goto('/verify-email?token=sai')
    await expect(page.getByRole('heading', { name: 'Đường dẫn không hợp lệ' })).toBeVisible()
    await expectNoSeriousA11yViolations(page)
  })
})
