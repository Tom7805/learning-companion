import AxeBuilder from '@axe-core/playwright'
import type { Page } from '@playwright/test'
import { createVerifiedAccount, expect, test } from './fixtures/auth'
import { STRONG_PASSWORD, uniqueEmail } from './fixtures/testData'

/**
 * NCL-01-CN-007 — Đăng nhập bằng tài khoản của nhà cung cấp định danh bên ngoài (Jira LC-61).
 * Môi trường thử dùng nhà cung cấp giả lập: trang cho phép quyền ở /dev/oauth/authorize, luồng chuyển hướng,
 * mã chống giả mạo và PKCE chạy như với Google thật.
 */

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

/** Bấm "Tiếp tục với Google" ở màn hình đăng nhập và tới trang cho phép quyền của nhà cung cấp. */
async function startGoogle(page: Page) {
  await page.goto('/login')
  await page.getByTestId('oauth-google').click()
  await expect(page).toHaveURL(/\/dev\/oauth\/authorize\?/)
  await expect(page.getByTestId('dev-oauth-scopes')).toContainText('Xem tên của bạn')
  await expect(page.getByTestId('dev-oauth-scopes')).toContainText('Xem địa chỉ thư của bạn')
}

async function allowAs(page: Page, email: string, name = 'Minh An') {
  await page.getByLabel('Địa chỉ thư Google').fill(email)
  await page.getByLabel('Tên', { exact: true }).fill(name)
  await page.getByRole('button', { name: 'Cho phép' }).click()
}

async function signUpWithGoogle(page: Page, email: string) {
  await startGoogle(page)
  await allowAs(page, email)
  await expect(page.getByRole('heading', { name: 'Hoàn tất tạo tài khoản' })).toBeVisible()
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: /Tạo tài khoản và vào học/ }).click()
  await expect(page.getByTestId('home-greeting')).toBeVisible()
}

async function openLinkedAccounts(page: Page) {
  await page.getByRole('button', { name: /Trình đơn tài khoản/ }).first().click()
  await page.getByRole('menuitem', { name: 'Cài đặt' }).click()
  const dialog = page.getByRole('dialog', { name: 'Cài đặt' })
  await dialog.getByRole('button', { name: 'Tài khoản liên kết' }).click()
  await expect(dialog.getByRole('heading', { name: 'Tài khoản liên kết' })).toBeVisible()
  return dialog
}

test.describe('NCL-01-CN-007 · Đăng nhập bằng Google', () => {
  test('TC-01: người chưa có tài khoản cho phép quyền, đồng ý điều khoản thì có tài khoản với thư đã xác thực và vào thẳng ứng dụng', async ({
    page,
  }) => {
    const email = uniqueEmail('google-moi').replace('@example.com', '@gmail.com')
    await startGoogle(page)
    await allowAs(page, email, 'Nguyễn Minh An')

    await expect(page).toHaveURL(/\/auth\/callback\/google$/)
    await expect(page.getByRole('heading', { name: 'Hoàn tất tạo tài khoản' })).toBeVisible()
    await expect(page.getByTestId('oauth-email')).toContainText(email)
    await expect(page.getByTestId('oauth-email')).toContainText('Đã được Google xác thực')
    await expect(page.getByLabel('Tên hiển thị')).toHaveValue('Nguyễn Minh An')
    await expectNoSeriousA11yViolations(page)

    await page.getByRole('button', { name: /Tạo tài khoản và vào học/ }).click()
    await expect(page.getByText(/Bạn cần đồng ý điều khoản/).first()).toBeVisible()
    await page.getByRole('checkbox').check()
    await page.getByRole('button', { name: /Tạo tài khoản và vào học/ }).click()

    await expect(page.getByTestId('home-greeting')).toBeVisible()
    const session = await (await page.request.get('/api/v1/auth/session')).json()
    expect(session.account.email).toBe(email)
    expect(session.account.status).toBe('ACTIVE')
    expect(session.account.emailVerifiedAt).toBeTruthy()

    const dialog = await openLinkedAccounts(page)
    await expect(dialog.getByTestId('linked-google')).toContainText(`Đã liên kết với ${email}`)
    await expectNoSeriousA11yViolations(page)
  })

  test('TC-02: địa chỉ thư đã có tài khoản dùng mật khẩu thì phải nhập mật khẩu một lần để liên kết', async ({
    page,
    browser,
    dev,
  }) => {
    const email = uniqueEmail('co-mat-khau')
    await createVerifiedAccount(page, dev, email)

    const visitor = await (await browser.newContext()).newPage()
    await startGoogle(visitor)
    await allowAs(visitor, email, 'Lan Anh')

    await expect(visitor.getByRole('heading', { name: 'Địa chỉ thư này đã có tài khoản' })).toBeVisible()
    await expect(visitor.getByText(/không tự gộp tài khoản/)).toBeVisible()
    // Chưa nhập mật khẩu thì chưa đăng nhập gì cả.
    expect((await (await visitor.request.get('/api/v1/auth/session')).json()).authenticated).toBe(false)

    const password = visitor.getByLabel('Mật khẩu', { exact: true })
    await password.fill('sai-mat-khau')
    await visitor.getByRole('button', { name: /Liên kết và đăng nhập/ }).click()
    await expect(visitor.getByText('Mật khẩu chưa đúng.')).toBeVisible()
    await expect(password).toHaveValue('')

    await password.fill(STRONG_PASSWORD)
    await visitor.getByRole('button', { name: /Liên kết và đăng nhập/ }).click()
    await expect(visitor.getByTestId('home-greeting')).toBeVisible()

    // Lần sau bấm Google là vào thẳng, không hỏi lại mật khẩu.
    const later = await (await browser.newContext()).newPage()
    await startGoogle(later)
    await allowAs(later, email, 'Lan Anh')
    await expect(later.getByTestId('home-greeting')).toBeVisible()
  })

  test('TC-03: mã chống giả mạo bị sửa hoặc kết quả mở ở trình duyệt khác thì từ chối đăng nhập', async ({
    page,
    browser,
  }) => {
    const email = uniqueEmail('gia-mao').replace('@example.com', '@gmail.com')
    await startGoogle(page)
    const authorize = new URL(page.url())
    authorize.searchParams.set('state', `${authorize.searchParams.get('state')}x`)
    await page.goto(authorize.pathname + authorize.search)
    await allowAs(page, email)

    await expect(page.getByTestId('oauth-failed')).toContainText('Không xác minh được yêu cầu đăng nhập này')
    expect((await (await page.request.get('/api/v1/auth/session')).json()).authenticated).toBe(false)
    await expectNoSeriousA11yViolations(page)

    // Kết quả hợp lệ nhưng mở ở trình duyệt khác (không có cookie của lượt đăng nhập) cũng bị từ chối.
    // Kẻ gian bắt đầu lượt đăng nhập ở máy mình rồi gửi đường dẫn kết quả cho người khác mở.
    await startGoogle(page)
    const query = new URL(page.url()).searchParams
    const issued = await page.request.post('/api/v1/dev/oauth/authorize', {
      data: {
        redirectUri: query.get('redirect_uri'),
        state: query.get('state'),
        codeChallenge: query.get('code_challenge'),
        email,
        name: 'Gia Mạo',
        emailVerified: true,
        allow: true,
      },
    })
    const callback = new URL((await issued.json()).redirectUrl)
    const victim = await (await browser.newContext()).newPage()
    await victim.goto(callback.pathname + callback.search)
    await expect(victim.getByTestId('oauth-failed')).toContainText('Không xác minh được yêu cầu đăng nhập này')
    expect((await (await victim.request.get('/api/v1/auth/session')).json()).authenticated).toBe(false)
  })

  test('TC-04: tài khoản chỉ đăng nhập qua Google bấm gỡ liên kết thì phải đặt mật khẩu trước', async ({ page }) => {
    const email = uniqueEmail('chi-google').replace('@example.com', '@gmail.com')
    await signUpWithGoogle(page, email)

    let dialog = await openLinkedAccounts(page)
    await dialog.getByRole('button', { name: 'Gỡ liên kết Google' }).click()
    const needPassword = page.getByRole('dialog', { name: 'Hãy đặt mật khẩu trước' })
    await expect(needPassword).toContainText('chỉ đăng nhập bằng Google')
    await needPassword.getByRole('button', { name: 'Đặt mật khẩu' }).click()

    await expect(page).toHaveURL(/\/security$/)
    await expect(page.getByRole('heading', { name: 'Đặt mật khẩu', level: 2 })).toBeVisible()
    await page.getByLabel('Mật khẩu mới', { exact: true }).fill(STRONG_PASSWORD)
    await page.getByLabel('Nhập lại mật khẩu mới').fill(STRONG_PASSWORD)
    await page.getByRole('button', { name: 'Đặt mật khẩu' }).click()
    await expect(page.getByTestId('set-password-success')).toBeVisible()

    dialog = await openLinkedAccounts(page)
    await dialog.getByRole('button', { name: 'Gỡ liên kết Google' }).click()
    const confirm = page.getByRole('dialog', { name: 'Gỡ liên kết Google?' })
    await confirm.getByRole('button', { name: 'Gỡ liên kết' }).click()
    await expect(dialog.getByTestId('linked-notice')).toContainText('Đã gỡ liên kết Google.')
    await expect(dialog.getByTestId('linked-google')).toContainText('Chưa liên kết')
  })

  test('Liên kết Google từ phần cài đặt khi đang đăng nhập bằng mật khẩu', async ({ page, dev }) => {
    await createVerifiedAccount(page, dev, uniqueEmail('lien-ket'))
    await page.goto('/')
    const dialog = await openLinkedAccounts(page)
    await expect(dialog.getByTestId('linked-google')).toContainText('Chưa liên kết')

    await dialog.getByRole('button', { name: 'Liên kết Google' }).click()
    await expect(page).toHaveURL(/\/dev\/oauth\/authorize\?/)
    await allowAs(page, uniqueEmail('tai-khoan-google').replace('@example.com', '@gmail.com'))

    const settings = page.getByRole('dialog', { name: 'Cài đặt' })
    await expect(settings.getByTestId('linked-notice')).toContainText('Đã liên kết Google')
    await expect(settings.getByTestId('linked-google')).toContainText('Đã liên kết với')
  })

  test('Hủy ở trang cho phép quyền thì báo đã hủy và không đăng nhập', async ({ page }) => {
    await startGoogle(page)
    await page.getByRole('button', { name: 'Từ chối' }).click()
    await expect(page.getByTestId('oauth-failed')).toContainText('Bạn đã hủy đăng nhập bằng Google')
    await page.getByRole('link', { name: 'Về màn hình đăng nhập' }).click()
    await expect(page).toHaveURL(/\/login$/)
  })
})
