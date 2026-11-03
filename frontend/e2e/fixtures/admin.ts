import { createHmac } from 'node:crypto'
import type { Page } from '@playwright/test'
import { expect, loginThroughUi, type DevApi } from './auth'

/**
 * Quản trị viên đăng nhập hai bước (NCL-03-CN-002). Kiểm thử đóng vai ứng dụng sinh mã: đọc khóa trên màn hình
 * thiết lập rồi tự tính mã sáu chữ số theo RFC 6238, với giờ lấy từ đồng hồ (tua được) của máy chủ.
 */
const BASE32 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'

function decodeBase32(text: string) {
  const bytes: number[] = []
  let buffer = 0
  let bits = 0
  for (const char of text.replace(/\s/g, '').toUpperCase()) {
    buffer = (buffer << 5) | BASE32.indexOf(char)
    bits += 5
    if (bits >= 8) {
      bytes.push((buffer >> (bits - 8)) & 0xff)
      bits -= 8
    }
  }
  return Buffer.from(bytes)
}

export function totpAt(secret: string, epochMs: number) {
  const counter = Buffer.alloc(8)
  counter.writeBigUInt64BE(BigInt(Math.floor(epochMs / 1000 / 30)))
  const hash = createHmac('sha1', decodeBase32(secret)).update(counter).digest()
  const offset = hash[hash.length - 1] & 0x0f
  const binary = (hash.readUInt32BE(offset) & 0x7fffffff) % 1_000_000
  return binary.toString().padStart(6, '0')
}

/** Mã đang hiện trên "điện thoại" theo giờ máy chủ; {@code stepOffset} để lấy mã của bước 30 giây kế tiếp. */
export async function currentCode(dev: DevApi, secret: string, stepOffset = 0) {
  return totpAt(secret, (await dev.serverNow()) + stepOffset * 30_000)
}

/** Một mã chắc chắn sai: khác mã của bước hiện tại và hai bước kề bên. */
export async function wrongCode(dev: DevApi, secret: string) {
  const now = await dev.serverNow()
  const valid = [-1, 0, 1].map((step) => totpAt(secret, now + step * 30_000))
  for (let candidate = 0; ; candidate += 1) {
    const code = candidate.toString().padStart(6, '0')
    if (!valid.includes(code)) return code
  }
}

export interface EnrolledAdmin {
  email: string
  secret: string
  recoveryCodes: string[]
}

/** Đang ở màn hình thiết lập: đọc khóa, nhập mã đầu tiên, lưu mã khôi phục rồi vào trang quản trị. */
export async function completeEnrollment(page: Page, dev: DevApi): Promise<Pick<EnrolledAdmin, 'secret' | 'recoveryCodes'>> {
  const secret = ((await page.getByTestId('totp-secret').textContent()) ?? '').replace(/\s/g, '')
  expect(secret).toMatch(/^[A-Z2-7]{32}$/)
  await page.getByLabel('Mã sáu chữ số từ ứng dụng').fill(await currentCode(dev, secret))
  await page.getByRole('button', { name: /Hoàn tất thiết lập/ }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Lưu mười mã khôi phục' })).toBeVisible()
  const recoveryCodes = (await page.getByTestId('recovery-code').allTextContents()).map((text) =>
    text.replace(/^\d+\.\s*/, '').trim(),
  )
  expect(recoveryCodes).toHaveLength(10)
  await page.getByRole('checkbox', { name: 'Tôi đã lưu các mã ở nơi an toàn' }).check()
  await page.getByRole('button', { name: /Vào trang quản trị/ }).click()
  await expect(page).toHaveURL(/\/admin\/audit$/)
  return { secret, recoveryCodes }
}

/**
 * Biến tài khoản đã đăng ký thành quản trị viên duy nhất rồi đăng nhập đủ hai bước như lần đầu: mật khẩu, thiết lập
 * ứng dụng sinh mã, lưu mã khôi phục. Phiên người học cũ của tài khoản không còn dùng được.
 */
export async function becomeAdmin(page: Page, dev: DevApi, email: string): Promise<EnrolledAdmin> {
  expect((await dev.setRole(email, 'ADMIN')).ok()).toBeTruthy()
  await page.context().clearCookies()
  await loginThroughUi(page, email)
  await expect(page).toHaveURL(/\/login\/two-factor$/)
  await expect(page.getByRole('heading', { level: 1, name: 'Thiết lập xác thực hai bước' })).toBeVisible()
  const enrolled = await completeEnrollment(page, dev)
  await expect(page.getByRole('heading', { level: 1, name: 'Nhật ký thao tác' })).toBeVisible()
  return { email, ...enrolled }
}
