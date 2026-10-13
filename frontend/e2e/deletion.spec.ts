import AxeBuilder from '@axe-core/playwright'
import type { Page } from '@playwright/test'
import { createVerifiedAccount, expect, fillRegisterForm, loginThroughUi, test } from './fixtures/auth'
import { STRONG_PASSWORD, uniqueEmail } from './fixtures/testData'

/**
 * NCL-01-CN-009 — Xóa tài khoản có thời gian chờ (Jira LC-80).
 * Xóa từ mục Tài khoản trong cài đặt, xác nhận bằng mật khẩu, chờ 30 ngày (đồng hồ máy chủ được tua) rồi tác vụ hằng
 * ngày xóa vĩnh viễn; đăng nhập lại trong thời gian chờ thì hủy được.
 */

const DAY = 86_400

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

async function openDeletion(page: Page) {
  await page.getByRole('button', { name: /Trình đơn tài khoản/ }).first().click()
  await page.getByRole('menuitem', { name: 'Cài đặt' }).click()
  const dialog = page.getByRole('dialog', { name: 'Cài đặt' })
  await dialog.getByRole('button', { name: 'Tài khoản', exact: true }).click()
  const section = dialog.getByRole('region', { name: 'Xóa tài khoản' })
  await expect(section).toContainText('30 ngày để đổi ý')
  await section.getByRole('link', { name: 'Xóa tài khoản…' }).click()
  await expect(page).toHaveURL(/\/account\/delete$/)
  await expect(page.getByRole('heading', { level: 1, name: 'Xóa tài khoản' })).toBeVisible()
}

/** Xác nhận xóa trên trang xóa tài khoản đang mở. */
async function confirmDeletion(page: Page, password = STRONG_PASSWORD) {
  await page.getByRole('checkbox', { name: /Tôi hiểu tài khoản/ }).check()
  await page.getByLabel('Mật khẩu hiện tại', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Xóa tài khoản', exact: true }).click()
}

/** Tạo tài khoản kèm một tài liệu rồi yêu cầu xóa; trình duyệt về trang "đã lên lịch xóa". */
async function scheduleDeletion(page: Page, dev: Parameters<typeof createVerifiedAccount>[1], email: string) {
  await createVerifiedAccount(page, dev, email)
  await dev.sampleDocument(email, 'Ghi chú vẫn còn nguyên')
  await page.goto('/')
  await openDeletion(page)
  await confirmDeletion(page)
  await expect(page.getByTestId('deletion-scheduled')).toBeVisible()
}

test.describe('NCL-01-CN-009 · Xóa tài khoản có thời gian chờ', () => {
  test('TC-01: xác nhận đúng mật khẩu thì tài khoản chờ xóa, mọi phiên bị thu hồi và thư nêu hạn chót đổi ý', async ({
    page,
    browser,
    dev,
  }) => {
    const email = uniqueEmail('xoa-tai-khoan')
    await createVerifiedAccount(page, dev, email)
    const laptop = await (await browser.newContext()).newPage()
    await loginThroughUi(laptop, email)
    await expect(laptop.getByTestId('home-greeting')).toBeVisible()
    await page.goto('/')

    await openDeletion(page)
    await expect(page.getByTestId('deletion-timeline')).toContainText(`gửi thư xác nhận tới ${email}`)
    await expect(page.getByTestId('deletion-timeline')).toContainText('chỉ còn mã đã ẩn danh')
    await expect(page.getByRole('link', { name: 'Mở mục xuất dữ liệu' })).toBeVisible()
    await expectNoSeriousA11yViolations(page)

    await page.getByRole('button', { name: 'Xóa tài khoản', exact: true }).click()
    await expect(page.getByText('Hãy đánh dấu để xác nhận bạn đã hiểu.')).toBeVisible()
    await expect(page.getByText('Hãy nhập mật khẩu.')).toBeVisible()

    await confirmDeletion(page)
    const scheduled = page.getByTestId('deletion-scheduled')
    await expect(page).toHaveURL(/\/account\/deletion-scheduled\?until=/)
    await expect(scheduled.getByRole('heading', { name: 'Đã lên lịch xóa tài khoản' })).toBeVisible()
    await expect(scheduled).toContainText('sẽ bị xóa vĩnh viễn vào')
    await expectNoSeriousA11yViolations(page)

    const mail = await dev.waitForMail(email, 'ACCOUNT_DELETION_SCHEDULED')
    expect(mail.subject).toBe('Tài khoản của bạn sẽ bị xóa sau 30 ngày')
    // Thiết bị khác cũng bị đăng xuất ngay.
    await laptop.reload()
    await expect(laptop).toHaveURL(/\/login$/)
    await expect(laptop.getByTestId('session-notice')).toContainText('đã bị đăng xuất')
    await laptop.context().close()
  })

  test('TC-02: chờ xóa được mười ngày, đăng nhập lại và hủy thì tài khoản hoạt động lại với nguyên dữ liệu', async ({
    page,
    dev,
  }) => {
    const email = uniqueEmail('doi-y')
    await scheduleDeletion(page, dev, email)

    await dev.advanceClock(10 * DAY)
    await loginThroughUi(page, email)
    const notice = page.getByTestId('pending-deletion')
    await expect(notice.getByRole('heading', { name: 'Tài khoản đang chờ xóa' })).toBeVisible()
    await expect(notice).toContainText('ngày để đổi ý')
    await expect(page.getByTestId('home-greeting')).toHaveCount(0)
    // Trong lúc chờ xóa, mọi chức năng khác bị chặn ở máy chủ.
    expect((await page.request.get('/api/v1/library/documents')).status()).toBe(403)
    await expectNoSeriousA11yViolations(page)

    await notice.getByRole('button', { name: /Hủy yêu cầu xóa và học tiếp/ }).click()
    await expect(notice.getByRole('heading', { name: 'Tài khoản đã hoạt động lại' })).toBeVisible()
    await notice.getByRole('button', { name: /Vào học tiếp/ }).click()
    await expect(page.getByTestId('home-greeting')).toBeVisible()
    await page.goto('/library')
    await expect(page.getByTestId('document-card')).toContainText('Ghi chú vẫn còn nguyên')
    await dev.waitForMail(email, 'ACCOUNT_DELETION_CANCELLED')
  })

  test('TC-03: quá ba mươi ngày thì tác vụ hằng ngày xóa vĩnh viễn, không đăng nhập được nữa và đăng ký lại được', async ({
    page,
    dev,
  }) => {
    const email = uniqueEmail('xoa-vinh-vien')
    await scheduleDeletion(page, dev, email)

    await dev.advanceClock(30 * DAY + 60)
    expect(await dev.runAccountPurge()).toBeGreaterThanOrEqual(1)
    const goodbye = await dev.waitForMail(email, 'ACCOUNT_DELETED')
    expect(goodbye.subject).toBe('Tài khoản Learning Companion của bạn đã được xóa')

    await loginThroughUi(page, email)
    await expect(page.getByText('Thư điện tử hoặc mật khẩu chưa đúng.').first()).toBeVisible()

    await page.goto('/register')
    await expect(page.getByTestId('terms-version')).toBeVisible()
    await fillRegisterForm(page, { email })
    await page.getByRole('button', { name: /Tạo tài khoản/ }).click()
    await expect(page).toHaveURL(/\/register\/check-email$/)
  })

  test('TC-04: phiên bị đánh cắp mà không biết mật khẩu thì bị từ chối và chủ tài khoản nhận thư cảnh báo', async ({
    page,
    browser,
    dev,
  }) => {
    const email = uniqueEmail('phien-bi-danh-cap')
    await createVerifiedAccount(page, dev, email)
    const thief = await (await browser.newContext()).newPage()
    await loginThroughUi(thief, email)
    await expect(thief.getByTestId('home-greeting')).toBeVisible()

    await thief.goto('/account/delete')
    await confirmDeletion(thief, 'doan bua mat khau')
    await expect(thief.getByText('Mật khẩu chưa đúng.')).toBeVisible()
    await expect(thief.getByLabel('Mật khẩu hiện tại', { exact: true })).toBeFocused()
    await expectNoSeriousA11yViolations(thief)

    const warning = await dev.waitForMail(email, 'ACCOUNT_DELETION_ATTEMPT')
    expect(warning.subject).toBe('Một yêu cầu xóa tài khoản của bạn vừa bị từ chối')
    expect(warning.links.some((link) => link.endsWith('/devices'))).toBe(true)
    // Tài khoản vẫn hoạt động bình thường với chủ tài khoản.
    await page.goto('/')
    await expect(page.getByTestId('home-greeting')).toBeVisible()
    await thief.context().close()
  })
})
