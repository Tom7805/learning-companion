import AxeBuilder from '@axe-core/playwright'
import type { Page } from '@playwright/test'
import { createVerifiedAccount, expect, loginThroughUi, test } from './fixtures/auth'
import { logoutThroughUi, navigateTo, openAccountMenu, openSidebar } from './fixtures/shell'
import { uniqueEmail } from './fixtures/testData'

/**
 * NCL-02-CN-001 — Khung ứng dụng có thanh bên, khung xem bên và chế độ sáng tối (Jira LC-18).
 * Cài ứng dụng và mở lại khi mạng chập chờn (TC-04, TC-05) cần bản dựng thật có service worker: xem pwa.spec.ts.
 */

async function expectNoSeriousA11yViolations(page: Page) {
  // Tắt hiệu ứng để axe đo độ tương phản ở trạng thái cuối.
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

async function signUp(page: Page, dev: Parameters<typeof createVerifiedAccount>[1], tag: string) {
  const email = uniqueEmail(tag)
  await createVerifiedAccount(page, dev, email)
  await page.goto('/')
  await expect(page.getByTestId('home-greeting')).toBeVisible()
  return email
}

const hasHorizontalScroll = (page: Page) =>
  page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth)

const width = async (page: Page, testId: string) => (await page.getByTestId(testId).boundingBox())?.width ?? 0

test.describe('NCL-02-CN-001 · Khung ứng dụng trên máy tính', () => {
  test.beforeEach(() => test.skip(test.info().project.name !== 'desktop-chrome', 'Thanh bên luôn hiện trên màn hình rộng'))

  test('TC-01: thu gọn thanh bên rồi tải lại vẫn là cột biểu tượng, vùng chính rộng ra; máy khác cũng vậy', async ({
    page,
    browser,
    dev,
  }) => {
    const email = await signUp(page, dev, 'thu-gon')
    const sidebar = page.getByTestId('sidebar')
    const main = page.locator('#main')
    const before = (await main.boundingBox())!.width
    await expect(sidebar.getByRole('navigation', { name: 'Chức năng' }).getByRole('link')).toHaveText([
      'Trang chính',
      'Thư viện',
    ])
    await expectNoSeriousA11yViolations(page)

    const saved = page.waitForResponse((response) => response.url().includes('/ui-preferences') && response.request().method() === 'PATCH')
    await sidebar.getByRole('button', { name: 'Thu gọn thanh bên' }).click()
    expect((await saved).ok()).toBe(true)

    await expect(sidebar).toHaveAttribute('data-collapsed', 'true')
    await expect.poll(async () => (await main.boundingBox())!.width).toBeGreaterThan(before + 150)
    // Rê chuột lên biểu tượng thì hiện tên mục.
    const library = sidebar.getByRole('link', { name: 'Thư viện' })
    await library.hover()
    const tooltip = library.locator('xpath=following-sibling::span[1]')
    await expect(tooltip).toHaveText('Thư viện')
    await expect(tooltip).toHaveCSS('opacity', '1')
    await expectNoSeriousA11yViolations(page)

    await page.reload()
    await expect(sidebar).toHaveAttribute('data-collapsed', 'true')
    expect((await main.boundingBox())!.width).toBeGreaterThan(before + 150)

    // Tùy chọn đi theo tài khoản: đăng nhập trên máy khác cũng thấy thanh bên thu gọn.
    const laptop = await (await browser.newContext({ viewport: { width: 1440, height: 900 } })).newPage()
    await loginThroughUi(laptop, email)
    await expect(laptop.getByTestId('sidebar')).toHaveAttribute('data-collapsed', 'true')

    await sidebar.getByRole('button', { name: 'Mở rộng thanh bên' }).click()
    await expect(sidebar).not.toHaveAttribute('data-collapsed')
    await expect.poll(async () => (await main.boundingBox())!.width).toBeLessThan(before + 10)
  })

  test('TC-01: thu gọn rồi tải lại khi chưa kịp lưu lên tài khoản thì vẫn thu gọn, và được lưu ngay khi mở lại', async ({
    page,
    dev,
  }) => {
    await signUp(page, dev, 'chua-luu')
    const preferences = '**/api/v1/ui-preferences'
    await page.route(preferences, (route) => (route.request().method() === 'PATCH' ? route.abort() : route.continue()))
    await page.getByTestId('sidebar').getByRole('button', { name: 'Thu gọn thanh bên' }).click()
    await expect(page.getByTestId('sidebar')).toHaveAttribute('data-collapsed', 'true')
    await page.unroute(preferences)

    const pushed = page.waitForResponse(
      (response) => response.url().includes('/ui-preferences') && response.request().method() === 'PATCH',
    )
    await page.reload()
    expect((await pushed).ok()).toBe(true)
    await expect(page.getByTestId('sidebar')).toHaveAttribute('data-collapsed', 'true')
    expect(await (await page.request.get('/api/v1/ui-preferences')).json()).toMatchObject({ sidebarCollapsed: true })
  })

  test('khung xem bên: xem tài liệu cạnh thư viện, đổi độ rộng, phóng toàn màn hình và Esc', async ({ page, dev }) => {
    const email = await signUp(page, dev, 'xem-ben')
    await dev.sampleDocument(email, 'Ghi chú: Định luật Ohm')
    await navigateTo(page, 'Thư viện')

    const peek = page.getByRole('button', { name: 'Xem Ghi chú: Định luật Ohm bên cạnh' })
    await peek.click()
    const panel = page.getByTestId('side-panel')
    await expect(panel).toBeVisible()
    await expect(panel.getByRole('heading', { name: 'Ghi chú: Định luật Ohm' })).toBeVisible()
    await expect(panel.getByTestId('document-peek')).toContainText('I = U / R')
    await expect(page).toHaveURL(/\/library$/)
    await expect(panel).toBeFocused()
    await expectNoSeriousA11yViolations(page)

    // Bàn phím: thanh chia có tiêu điểm thì mũi tên trái nới rộng khung.
    const start = await width(page, 'side-panel')
    const separator = page.getByRole('separator', { name: 'Kéo để đổi độ rộng khung xem bên' })
    await separator.focus()
    await page.keyboard.press('ArrowLeft')
    await page.keyboard.press('ArrowLeft')
    await expect.poll(() => width(page, 'side-panel')).toBe(start + 48)

    // Kéo bằng chuột.
    const box = (await separator.boundingBox())!
    await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2)
    await page.mouse.down()
    await page.mouse.move(box.x + box.width / 2 - 100, box.y + box.height / 2, { steps: 5 })
    await page.mouse.up()
    await expect.poll(() => width(page, 'side-panel')).toBe(start + 148)

    // Phóng toàn màn hình rồi Esc về lại khung bên.
    await panel.getByRole('button', { name: 'Phóng toàn màn hình' }).click()
    const overlay = page.getByTestId('side-panel')
    await expect(overlay).toHaveAttribute('data-fullscreen', 'true')
    await expect.poll(() => width(page, 'side-panel')).toBeGreaterThan(1400)
    await expectNoSeriousA11yViolations(page)
    await page.keyboard.press('Escape')
    await expect(page.getByTestId('side-panel')).not.toHaveAttribute('data-fullscreen')
    await expect(page.getByRole('button', { name: 'Phóng toàn màn hình' })).toBeFocused()

    // Esc lần nữa thì đóng, tiêu điểm về nút đã mở. Độ rộng được nhớ cho lần sau.
    await page.keyboard.press('Escape')
    await expect(page.getByTestId('side-panel')).toBeHidden()
    await expect(peek).toBeFocused()
    await page.reload()
    await peek.click()
    await expect.poll(() => width(page, 'side-panel')).toBe(start + 148)

    await page.getByTestId('side-panel').getByRole('link', { name: 'Mở toàn trang' }).click()
    await expect(page.getByRole('heading', { level: 1, name: 'Ghi chú: Định luật Ohm' })).toBeVisible()
    await expect(page.getByTestId('side-panel')).toBeHidden()
  })

  test('Mở nhanh bằng Ctrl K: gõ không dấu, Enter mở tài liệu, tài liệu vào mục Gần đây', async ({ page, dev }) => {
    const email = await signUp(page, dev, 'mo-nhanh')
    await dev.sampleDocument(email, 'Ghi chú: Định luật Ohm')
    await expect(page.getByTestId('sidebar-recent')).toContainText('Tài liệu bạn mở sẽ hiện ở đây')

    await page.keyboard.press('Control+k')
    const search = page.getByRole('combobox', { name: 'Tìm trang hoặc tài liệu' })
    await expect(search).toBeFocused()
    await expectNoSeriousA11yViolations(page)
    await search.fill('dinh luat')
    await expect(page.getByRole('option')).toHaveText(['Ghi chú: Định luật Ohm'])
    await page.keyboard.press('Enter')

    await expect(page.getByRole('heading', { level: 1, name: 'Ghi chú: Định luật Ohm' })).toBeVisible()
    const recent = page.getByTestId('sidebar-recent')
    await expect(recent.getByRole('link', { name: 'Ghi chú: Định luật Ohm' })).toHaveAttribute('aria-current', 'page')

    // Mở nhanh tới một trang, rồi quay lại tài liệu từ mục Gần đây.
    await page.getByTestId('sidebar').getByRole('button', { name: /Mở nhanh/ }).click()
    await search.fill('thu vien')
    await page.getByRole('option', { name: 'Thư viện' }).click()
    await expect(page.getByRole('heading', { level: 1, name: 'Thư viện của tôi' })).toBeVisible()
    await recent.getByRole('link', { name: 'Ghi chú: Định luật Ohm' }).click()
    await expect(page.getByRole('heading', { level: 1, name: 'Ghi chú: Định luật Ohm' })).toBeVisible()

    // Mục Gần đây là của riêng tài khoản trên máy này: đăng xuất là xóa.
    await logoutThroughUi(page)
    expect(await page.evaluate(() => Object.keys(localStorage).filter((key) => key.startsWith('lc:recent:')))).toEqual([])
  })
})

test.describe('NCL-02-CN-001 · Khung ứng dụng trên điện thoại', () => {
  test.beforeEach(() => test.skip(test.info().project.name !== 'mobile-chrome', 'Ngăn kéo chỉ có trên màn hình hẹp'))

  test('TC-02: ở 360 px, nút mở thanh bên trượt ngăn kéo từ cạnh trái, không cuộn ngang, chạm ra ngoài thì đóng', async ({
    page,
    dev,
  }) => {
    await page.setViewportSize({ width: 360, height: 780 })
    await signUp(page, dev, 'ngan-keo')
    expect(await hasHorizontalScroll(page)).toBe(false)
    await expect(page.getByTestId('sidebar')).toHaveCount(0)

    const opener = page.getByTestId('open-sidebar')
    await expect(opener).toHaveAccessibleName('Mở thanh bên')
    await opener.tap()
    const drawer = page.getByRole('dialog', { name: 'Thanh bên' })
    await expect(drawer).toBeVisible()
    await expect(opener).toHaveAttribute('aria-expanded', 'true')
    await expect.poll(async () => (await drawer.boundingBox())?.x).toBe(0)
    expect((await drawer.boundingBox())!.width).toBeLessThan(360)
    expect(await hasHorizontalScroll(page)).toBe(false)
    await expectNoSeriousA11yViolations(page)

    // Chạm vào phần trang còn lộ ra bên phải ngăn kéo.
    await page.touchscreen.tap(350, 400)
    await expect(drawer).toBeHidden()
    await expect(opener).toHaveAttribute('aria-expanded', 'false')

    // Chọn một mục thì sang trang đó và ngăn kéo tự đóng.
    await opener.tap()
    await drawer.getByRole('link', { name: 'Thư viện' }).tap()
    await expect(page.getByRole('heading', { level: 1, name: 'Thư viện của tôi' })).toBeVisible()
    await expect(drawer).toBeHidden()
    expect(await hasHorizontalScroll(page)).toBe(false)
  })

  test('khung xem bên mở toàn màn hình trên điện thoại', async ({ page, dev }) => {
    const email = await signUp(page, dev, 'xem-ben-dt')
    await dev.sampleDocument(email, 'Ghi chú: Định luật Ohm')
    const sidebar = await openSidebar(page)
    await sidebar.getByRole('link', { name: 'Thư viện' }).tap()

    await page.getByRole('button', { name: 'Xem Ghi chú: Định luật Ohm bên cạnh' }).tap()
    const panel = page.getByTestId('side-panel')
    await expect(panel).toHaveAttribute('data-fullscreen', 'true')
    await expect(panel.getByTestId('document-peek')).toContainText('I = U / R')
    const viewport = page.viewportSize()!
    const box = (await panel.boundingBox())!
    expect(Math.round(box.width)).toBe(viewport.width)
    await expect(panel.getByRole('button', { name: 'Phóng toàn màn hình' })).toHaveCount(0)
    expect(await hasHorizontalScroll(page)).toBe(false)
    await expectNoSeriousA11yViolations(page)

    await panel.getByRole('button', { name: 'Đóng khung xem bên' }).tap()
    await expect(panel).toBeHidden()
  })
})

test.describe('NCL-02-CN-001 · Giao diện, trợ giúp và mạng', () => {
  test('TC-03: hệ điều hành tối và chọn "theo hệ thống" thì giao diện tối, chữ đủ tương phản ở mọi màn hình', async ({
    page,
    dev,
  }) => {
    await page.emulateMedia({ colorScheme: 'dark' })
    const email = await signUp(page, dev, 'che-do-toi')
    await dev.sampleDocument(email, 'Ghi chú: Định luật Ohm')
    const html = page.locator('html')
    await expect(html).toHaveAttribute('data-theme', 'dark')
    await expect(page.locator('meta[name="theme-color"]')).toHaveAttribute('content', '#09090a')
    await expectNoSeriousA11yViolations(page)

    const menu = await openAccountMenu(page)
    await menu.getByRole('menuitem', { name: 'Giao diện' }).click()
    const section = page.getByTestId('appearance-section')
    await expect(section.getByRole('radio', { name: /Theo hệ thống/ })).toBeChecked()
    await expect(section.getByTestId('theme-effective')).toHaveText('Đang dùng: Tối (theo hệ thống)')
    await expectNoSeriousA11yViolations(page)

    // Hệ điều hành chuyển sáng thì giao diện theo ngay.
    await page.emulateMedia({ colorScheme: 'light' })
    await expect(html).toHaveAttribute('data-theme', 'light')
    await expect(section.getByTestId('theme-effective')).toHaveText('Đang dùng: Sáng (theo hệ thống)')

    // Chọn hẳn chế độ tối và chữ lớn: áp ngay, còn nguyên sau khi tải lại dù hệ điều hành đang sáng.
    await section.getByText('Tối', { exact: true }).click()
    await section.getByText('Lớn', { exact: true }).click()
    await expect(html).toHaveAttribute('data-theme', 'dark')
    await expect(html).toHaveCSS('font-size', '18px')
    await page.keyboard.press('Escape')
    await page.reload()
    await expect(html).toHaveAttribute('data-theme', 'dark')
    await expect(html).toHaveCSS('font-size', '18px')
    expect(await hasHorizontalScroll(page)).toBe(false)
    await expectNoSeriousA11yViolations(page)

    // Thư viện, tài liệu, khung xem bên và trang đăng nhập ở chế độ tối cũng đủ tương phản.
    await navigateTo(page, 'Thư viện')
    await expect(page.getByTestId('document-card')).toBeVisible()
    await expectNoSeriousA11yViolations(page)
    await page.getByRole('button', { name: 'Xem Ghi chú: Định luật Ohm bên cạnh' }).click()
    await expect(page.getByTestId('document-peek')).toBeVisible()
    await expectNoSeriousA11yViolations(page)
    await page.getByTestId('document-peek').getByRole('link', { name: 'Mở toàn trang' }).click()
    await expect(page.getByTestId('document-content')).toBeVisible()
    await expectNoSeriousA11yViolations(page)
    await logoutThroughUi(page)
    await expect(html).toHaveAttribute('data-theme', 'dark')
    await expectNoSeriousA11yViolations(page)
  })

  test('Trợ giúp, giao diện và cài ứng dụng trong trình đơn tài khoản', async ({ page, dev }) => {
    await signUp(page, dev, 'tro-giup')
    // Giả lời mời cài của trình duyệt (Chrome chỉ phát khi đủ điều kiện, xem pwa.spec.ts).
    await page.evaluate(() => {
      const offer = Object.assign(new Event('beforeinstallprompt', { cancelable: true }), {
        prompt: async () => {
          ;(window as unknown as { prompted: boolean }).prompted = true
        },
        userChoice: Promise.resolve({ outcome: 'accepted' }),
      })
      window.dispatchEvent(offer)
    })

    let menu = await openAccountMenu(page)
    await expect(menu.getByRole('menuitem')).toHaveText(['Cài đặt', 'Giao diện', 'Trợ giúp', 'Cài ứng dụng', 'Đăng xuất'])
    await expectNoSeriousA11yViolations(page)
    await menu.getByRole('menuitem', { name: 'Cài ứng dụng' }).click()
    await expect(page.getByText('Đã cài Learning Companion lên máy của bạn.')).toBeVisible()
    expect(await page.evaluate(() => (window as unknown as { prompted?: boolean }).prompted)).toBe(true)

    menu = await openAccountMenu(page)
    await expect(menu.getByRole('menuitem', { name: 'Cài ứng dụng' })).toHaveCount(0)
    await menu.getByRole('menuitem', { name: 'Trợ giúp' }).click()
    const help = page.getByTestId('side-panel')
    await expect(help.getByRole('heading', { name: 'Trợ giúp', exact: true })).toBeVisible()
    await expect(help.getByTestId('help-install')).toContainText('Ứng dụng đã được cài trên máy này.')
    await expectNoSeriousA11yViolations(page)

    await help.getByRole('button', { name: 'Mở cài đặt giao diện' }).click()
    await expect(page).toHaveURL(/settings=appearance/)
    await expect(page.getByTestId('appearance-section')).toBeVisible()
  })

  test('mất mạng giữa chừng thì báo đang kết nối lại; có mạng trở lại thì báo và tải lại', async ({
    page,
    context,
    dev,
  }) => {
    await signUp(page, dev, 'mat-mang')

    await context.setOffline(true)
    await expect(page.getByTestId('offline-banner')).toContainText('Mất kết nối mạng')
    expect(await hasHorizontalScroll(page)).toBe(false)
    await expectNoSeriousA11yViolations(page)

    await context.setOffline(false)
    await expect(page.getByText('Đã kết nối lại.')).toBeVisible()
    await expect(page.getByTestId('offline-banner')).toBeHidden()
    await navigateTo(page, 'Thư viện')
    await expect(page.getByRole('heading', { level: 1, name: 'Thư viện của tôi' })).toBeVisible()
  })
})
