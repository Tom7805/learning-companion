import { expect, test as base, type APIRequestContext, type Page } from '@playwright/test'
import { DISPLAY_NAME, STRONG_PASSWORD } from './testData'

const API = `${process.env.E2E_API_URL ?? 'http://127.0.0.1:8080'}/api/v1`

export interface CapturedMail {
  to: string
  subject: string
  template:
    | 'VERIFY_EMAIL'
    | 'REGISTRATION_ATTEMPT'
    | 'NEW_DEVICE_LOGIN'
    | 'ACCOUNT_LOCKED'
    | 'PASSWORD_RESET'
    | 'PASSWORD_CHANGED'
    | 'PRODUCT_NEWS'
    | 'DATA_EXPORT_READY'
  links: string[]
  sentAt: string
}

/** Công cụ chỉ có ở hồ sơ dev của máy chủ: hộp thư mô phỏng và đồng hồ tua được. */
export class DevApi {
  constructor(private readonly request: APIRequestContext) {}

  async mails(to: string): Promise<CapturedMail[]> {
    const response = await this.request.get(`${API}/dev/mailbox`, { params: { to } })
    expect(response.ok()).toBeTruthy()
    return response.json()
  }

  /** Chờ thư theo tiêu chí TC-01: phải tới trong vòng một phút. */
  async waitForMail(to: string, template: CapturedMail['template'], count = 1): Promise<CapturedMail> {
    let found: CapturedMail[] = []
    await expect
      .poll(
        async () => {
          found = (await this.mails(to)).filter((mail) => mail.template === template)
          return found.length
        },
        { timeout: 60_000, intervals: [200, 500, 1000] },
      )
      .toBeGreaterThanOrEqual(count)
    return found[0]
  }

  async verifyLink(to: string, count = 1) {
    const mail = await this.waitForMail(to, 'VERIFY_EMAIL', count)
    const link = mail.links.find((candidate) => candidate.includes('/verify-email?token='))
    expect(link, 'thư xác thực phải chứa đường dẫn').toBeTruthy()
    return link!
  }

  /** Đường dẫn đặt lại mật khẩu trong thư mới nhất (thư mới đứng đầu hộp thư). */
  async resetLink(to: string, count = 1) {
    const mail = await this.waitForMail(to, 'PASSWORD_RESET', count)
    const link = mail.links.find((candidate) => candidate.includes('/reset-password?token='))
    expect(link, 'thư đặt lại mật khẩu phải chứa đường dẫn').toBeTruthy()
    return link!
  }

  /** Đường dẫn tải gói dữ liệu trong thư báo gói đã sẵn sàng (NCL-01-CN-008). */
  async exportLink(to: string, count = 1) {
    const mail = await this.waitForMail(to, 'DATA_EXPORT_READY', count)
    const link = mail.links.find((candidate) => /\/exports\/[0-9a-f-]{36}$/.test(candidate))
    expect(link, 'thư báo gói dữ liệu phải chứa đường dẫn tải').toBeTruthy()
    return link!
  }

  /** Bản ghi học tập mẫu kèm tệp gốc cho một tài khoản (chỉ có ở hồ sơ dev). Trả về mã tài liệu. */
  async sampleDocument(email: string, title?: string): Promise<string> {
    const response = await this.request.post(`${API}/dev/library/sample`, { data: { email, title } })
    expect(response.ok()).toBeTruthy()
    return (await response.json()).id
  }

  async setRole(email: string, role: 'LEARNER' | 'ADMIN') {
    expect((await this.request.post(`${API}/dev/accounts/role`, { data: { email, role } })).ok()).toBeTruthy()
  }

  /** Đăng phiên bản điều khoản mới có hiệu lực sau ngần ấy giây theo đồng hồ máy chủ. */
  async publishTerms(version: string, changes: string[], effectiveInSeconds: number) {
    const response = await this.request.post(`${API}/dev/legal/versions`, {
      data: {
        documentType: 'TERMS_OF_SERVICE',
        version,
        summary: 'Bản cập nhật thử nghiệm.',
        highlights: ['Bạn dùng ứng dụng cho việc học của chính mình.'],
        changes,
        effectiveInSeconds,
      },
    })
    expect(response.ok()).toBeTruthy()
  }

  /** Gửi một bản tin tính năng mới tới những người đang đồng ý nhận. */
  async sendProductNews(headline: string) {
    const response = await this.request.post(`${API}/dev/product-news`, { data: { headline, body: 'Thử ngay.' } })
    expect(response.ok()).toBeTruthy()
  }

  async advanceClock(seconds: number) {
    expect((await this.request.post(`${API}/dev/clock/advance`, { data: { seconds } })).ok()).toBeTruthy()
  }

  async resetClock() {
    await this.request.post(`${API}/dev/clock/reset`)
  }
}

export async function fillRegisterForm(
  page: Page,
  { email, password = STRONG_PASSWORD, displayName = DISPLAY_NAME }: { email: string; password?: string; displayName?: string },
) {
  await page.getByLabel('Tên hiển thị').fill(displayName)
  await page.getByLabel('Thư điện tử').fill(email)
  await page.getByLabel('Mật khẩu', { exact: true }).fill(password)
  await page.getByLabel('Nhập lại mật khẩu').fill(password)
  await page.getByRole('checkbox', { name: /đồng ý/ }).check()
}

export async function registerThroughUi(page: Page, email: string, password = STRONG_PASSWORD) {
  await page.goto('/register')
  await expect(page.getByTestId('terms-version')).toBeVisible()
  await fillRegisterForm(page, { email, password })
  await page.getByRole('button', { name: /Tạo tài khoản/ }).click()
  await expect(page).toHaveURL(/\/register\/check-email$/)
}

/** Tạo tài khoản đã xác thực trên trình duyệt của `page`; trình duyệt đó được đăng nhập luôn. */
export async function createVerifiedAccount(page: Page, dev: DevApi, email: string) {
  await registerThroughUi(page, email)
  await page.goto(await dev.verifyLink(email))
  await expect(page.getByRole('heading', { name: 'Tài khoản đã được kích hoạt' })).toBeVisible()
}

export async function loginThroughUi(page: Page, email: string, password = STRONG_PASSWORD, remember = false) {
  await page.goto('/login')
  await page.getByLabel('Thư điện tử').fill(email)
  await page.getByLabel('Mật khẩu', { exact: true }).fill(password)
  if (remember) {
    await page.getByRole('checkbox', { name: 'Ghi nhớ thiết bị này' }).check()
  }
  await page.getByRole('button', { name: /Đăng nhập/ }).click()
}

export const test = base.extend<{ dev: DevApi }>({
  dev: async ({ request }, use) => {
    const dev = new DevApi(request)
    await dev.resetClock()
    await use(dev)
    await dev.resetClock()
  },
})

export { expect }
