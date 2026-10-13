import type { Page } from '@playwright/test'
import { expect, test } from './fixtures/auth'
import { STRONG_PASSWORD } from './fixtures/testData'

/**
 * Bố cục trên điện thoại nhỏ nhất (320 px) của các màn hình Epic NCL-01: không màn hình nào được tràn ngang, kể cả
 * khi dữ liệu người học là một chuỗi dài không có chỗ ngắt (địa chỉ thư, tiêu đề tài liệu). Khung nhìn cố định,
 * không giả lập thu phóng của điện thoại, để phép đo không bị trình duyệt tự nới rộng theo nội dung.
 */
test.use({ viewport: { width: 320, height: 720 }, isMobile: false, hasTouch: true })

/** Mỗi lượt chạy một lần là đủ: khung nhìn đã được cố định ở trên, chạy thêm ở dự án điện thoại không thêm gì. */
test.beforeEach(() => {
  test.skip(test.info().project.name !== 'desktop-chrome', 'khung nhìn 320 px đã cố định cho mọi dự án')
})

const LONG_EMAIL = () =>
  `nguyen.thi.lan.anh.hoc.vien.${Date.now()}${Math.random().toString(36).slice(2, 6)}@truong-dai-hoc-bach-khoa.example.com`

/** Phần tử vượt mép phải khung nhìn, bỏ qua phần tử nằm trong vùng cuộn ngang có chủ đích. */
async function overflowing(page: Page) {
  await page.waitForTimeout(300)
  return page.evaluate(() => {
    const width = document.documentElement.clientWidth
    const offenders: string[] = []
    for (const element of Array.from(document.querySelectorAll('body *'))) {
      const box = element.getBoundingClientRect()
      if (box.width === 0 || box.right <= width + 1) continue
      let ancestor = element.parentElement
      let insideScroller = false
      while (ancestor) {
        if (['auto', 'scroll'].includes(getComputedStyle(ancestor).overflowX)) {
          insideScroller = true
          break
        }
        ancestor = ancestor.parentElement
      }
      if (!insideScroller) offenders.push(`${element.tagName.toLowerCase()} "${(element.textContent ?? '').trim().slice(0, 40)}"`)
    }
    return { scrollWidth: document.documentElement.scrollWidth, width, offenders: offenders.slice(0, 5) }
  })
}

async function expectNoOverflow(page: Page, screen: string) {
  const result = await overflowing(page)
  expect(result.offenders, `${screen} tràn ngang`).toEqual([])
  expect(result.scrollWidth, `${screen} rộng hơn khung nhìn`).toBeLessThanOrEqual(result.width)
}

test('màn hình công khai không tràn ngang ở 320 px, kể cả với địa chỉ thư rất dài', async ({ page, dev }) => {
  const email = LONG_EMAIL()
  for (const path of ['/login', '/register', '/forgot-password', '/legal/terms', '/legal/privacy']) {
    await page.goto(path)
    await expect(page.getByRole('heading', { level: 1 }).first()).toBeVisible()
    await expectNoOverflow(page, path)
  }

  await page.goto('/register')
  await expect(page.getByTestId('terms-version')).toBeVisible()
  await page.getByLabel('Tên hiển thị').fill('Nguyễn Thị Lan Anh')
  await page.getByLabel('Thư điện tử').fill(email)
  await page.getByLabel('Mật khẩu', { exact: true }).fill(STRONG_PASSWORD)
  await page.getByLabel('Nhập lại mật khẩu').fill(STRONG_PASSWORD)
  await page.getByRole('checkbox', { name: /đồng ý/ }).check()
  await page.getByRole('button', { name: /Tạo tài khoản/ }).click()
  await expect(page.getByTestId('check-email-body')).toContainText(email)
  await expectNoOverflow(page, 'kiểm tra hộp thư')

  await page.goto('/forgot-password')
  await page.getByLabel('Thư điện tử').fill(email)
  await page.getByRole('button', { name: /Gửi/ }).first().click()
  await expect(page.getByText(email).first()).toBeVisible()
  await expectNoOverflow(page, 'quên mật khẩu đã gửi')

  await page.goto(await dev.verifyLink(email))
  await expect(page.getByRole('heading', { name: 'Tài khoản đã được kích hoạt' })).toBeVisible()
  await expectNoOverflow(page, 'xác thực thành công')
})

test('màn hình trong ứng dụng không tràn ngang ở 320 px với địa chỉ thư và tiêu đề dài', async ({ page, dev }) => {
  const email = LONG_EMAIL()
  await page.goto('/register')
  await expect(page.getByTestId('terms-version')).toBeVisible()
  await page.getByLabel('Tên hiển thị').fill('Nguyễn Thị Lan Anh')
  await page.getByLabel('Thư điện tử').fill(email)
  await page.getByLabel('Mật khẩu', { exact: true }).fill(STRONG_PASSWORD)
  await page.getByLabel('Nhập lại mật khẩu').fill(STRONG_PASSWORD)
  await page.getByRole('checkbox', { name: /đồng ý/ }).check()
  await page.getByRole('button', { name: /Tạo tài khoản/ }).click()
  await page.goto(await dev.verifyLink(email))
  await expect(page.getByRole('heading', { name: 'Tài khoản đã được kích hoạt' })).toBeVisible()
  const documentId = await dev.sampleDocument(email, 'ChuongMotGioiThieuVeCoHocLuongTuVaUngDungTrongCongNgheBanDan')

  for (const path of [
    '/',
    '/library',
    `/library/${documentId}`,
    '/devices',
    '/security',
    '/privacy',
    '/?settings=profile',
    '/?settings=accounts',
    '/account/delete',
  ]) {
    await page.goto(path)
    await expect(page.getByRole('heading', { level: 1 }).first()).toBeVisible()
    await expectNoOverflow(page, path)
  }

  const section = page.getByRole('dialog', { name: 'Cài đặt' }).getByRole('region', { name: 'Xuất toàn bộ dữ liệu' })
  await page.goto('/?settings=accounts')
  await section.getByRole('button', { name: /Yêu cầu xuất dữ liệu/ }).click()
  await expect(section.getByTestId('export-notice')).toContainText(email)
  await expectNoOverflow(page, 'cài đặt › xuất dữ liệu vừa yêu cầu')
  await page.goto(await dev.exportLink(email))
  await expect(page.getByRole('heading', { level: 1, name: 'Tải gói dữ liệu của bạn' })).toBeVisible()
  await expectNoOverflow(page, 'trang tải gói dữ liệu')
})
