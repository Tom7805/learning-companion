import { expect, test as base, type APIRequestContext, type Page } from '@playwright/test'
import { DISPLAY_NAME, STRONG_PASSWORD } from './testData'

const API = 'http://127.0.0.1:8080/api/v1'

export interface CapturedMail {
  to: string
  subject: string
  template: 'VERIFY_EMAIL' | 'REGISTRATION_ATTEMPT'
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

export const test = base.extend<{ dev: DevApi }>({
  dev: async ({ request }, use) => {
    const dev = new DevApi(request)
    await dev.resetClock()
    await use(dev)
    await dev.resetClock()
  },
})

export { expect }
