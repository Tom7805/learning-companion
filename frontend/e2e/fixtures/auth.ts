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
    | 'ACCOUNT_DELETION_SCHEDULED'
    | 'ACCOUNT_DELETION_CANCELLED'
    | 'ACCOUNT_DELETION_ATTEMPT'
    | 'ACCOUNT_DELETED'
    | 'ADMIN_LOGIN_LOCKED'
    | 'AI_CIRCUIT_OPEN'
  links: string[]
  sentAt: string
}

export interface LibrarySeed {
  folders?: string[]
  tags?: { name: string; color?: 'SUN' | 'LILAC' | 'SKY' | 'MINT' | 'CORAL' | 'INK' }[]
  documents?: {
    title: string
    type?: 'PDF' | 'DOCX' | 'PPTX' | 'TEXT' | 'IMAGE' | 'AUDIO' | 'VIDEO' | 'WEB'
    folder?: string
    tags?: string[]
    starred?: boolean
    status?: 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED'
    addedDaysAgo?: number
    text?: string
  }[]
}

export interface LibrarySeeded {
  folders: Record<string, string>
  tags: Record<string, string>
  documents: string[]
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

  /**
   * Dựng kho tài liệu mẫu (NCL-05-CN-001): thư mục ghi theo đường dẫn "Học kỳ một/Kinh tế vi mô", nhãn theo tên, tài
   * liệu trỏ tới thư mục và nhãn. Trả về mã của thư mục (theo đường dẫn), nhãn (theo tên) và tài liệu (theo thứ tự).
   */
  async seedLibrary(email: string, seed: LibrarySeed): Promise<LibrarySeeded> {
    const response = await this.request.post(`${API}/dev/library/seed`, { data: { email, ...seed } })
    expect(response.ok()).toBeTruthy()
    return response.json()
  }

  /** Dựng sẵn {@code count} không gian học "{prefix} 001"… cho tài khoản (NCL-06-CN-001 TC-01). Trả về mã theo thứ tự. */
  /**
   * Dựng sẵn hội thoại riêng ở nhiều thời điểm (NCL-07-CN-003): mỗi hội thoại một câu hỏi (cũng là tên) và một câu trả
   * lời đã xong, lùi {@code hoursAgo} giờ so với bây giờ.
   */
  async seedConversations(
    email: string,
    items: { question: string; answer?: string; hoursAgo: number; pinned?: boolean }[],
  ): Promise<string[]> {
    const response = await this.request.post(`${API}/dev/assistant/conversations/seed`, {
      data: { email, items: items.map((item) => ({ pinned: false, ...item })) },
    })
    expect(response.ok()).toBeTruthy()
    return (await response.json()).conversations
  }

  async seedWorkspaces(email: string, count: number, prefix?: string): Promise<string[]> {
    const response = await this.request.post(`${API}/dev/workspaces/seed`, { data: { email, count, prefix } })
    expect(response.ok()).toBeTruthy()
    return (await response.json()).workspaces
  }

  /**
   * Dừng hoặc chạy lại tác vụ nền xử lý tài liệu (NCL-05-CN-003). Kiểm thử tải lên dừng nó để thấy tài liệu chờ xử
   * lý; chạy lại thì hàng chờ được quét ngay.
   */
  async setDocumentProcessing(running: boolean) {
    const response = await this.request.post(`${API}/dev/library/processing`, { data: { running } })
    expect(response.ok()).toBeTruthy()
  }

  /**
   * Tệp mẫu cho kiểm thử xử lý tài liệu: bài giảng nhiều trang (có thể kèm một trang quét), tệp chỉ có trang trắng,
   * ảnh trang vở mang sẵn chữ cho bản mô phỏng nhận dạng chữ.
   */
  async sampleFile(name: 'lecture.pdf' | 'blank.pdf' | 'notebook.png', params: Record<string, string | number> = {}) {
    const response = await this.request.get(`${API}/dev/library/samples/${name}`, { params })
    expect(response.ok()).toBeTruthy()
    return response.body()
  }

  /** Cho ngần ấy lời gọi nhận dạng chữ tiếp theo lỗi; số âm: lỗi cho tới khi đặt lại (NCL-05-CN-003 TC-03). */
  async failNextOcr(calls: number) {
    const response = await this.request.post(`${API}/dev/ai/mock-ocr-failures`, { data: { calls } })
    expect(response.ok()).toBeTruthy()
  }

  /**
   * Cho ngần ấy lần đính dấu nhận biết nội dung do trí tuệ nhân tạo sinh ra tiếp theo lỗi; số âm: lỗi cho tới khi đặt
   * lại (NCL-04-CN-005 TC-04). Đặt lại lớp kết nối cũng bỏ.
   */
  async failNextAiLabel(calls: number) {
    const response = await this.request.post(`${API}/dev/ai/label-failures`, { data: { calls } })
    expect(response.ok()).toBeTruthy()
  }

  /** Thông báo mẫu (NCL-02-CN-002) trỏ tới gói dữ liệu không tồn tại; trả về số thông báo đã tạo. */
  async sampleNotifications(email: string, count: number): Promise<number> {
    const response = await this.request.post(`${API}/dev/notifications/sample`, { data: { email, count } })
    expect(response.ok()).toBeTruthy()
    return (await response.json()).created
  }

  /**
   * Cấp vai trò. Hệ thống chỉ có một quản trị viên (NCL-03-CN-002): mặc định hạ quản trị viên của bài kiểm thử trước
   * xuống người học; {@code replaceExisting: false} để thử yêu cầu tạo thêm quản trị viên (bị từ chối).
   */
  async setRole(email: string, role: 'LEARNER' | 'ADMIN', replaceExisting = true) {
    return this.request.post(`${API}/dev/accounts/role`, { data: { email, role, replaceExisting } })
  }

  /** Đưa lớp kết nối trí tuệ nhân tạo về ban đầu: hai bản mô phỏng, định tuyến mặc định, mạch đóng. */
  async resetAiGateway() {
    const response = await this.request.post(`${API}/dev/ai/reset`)
    expect(response.ok()).toBeTruthy()
  }

  /** Xóa mọi phiên bản cấu hình chức năng trí tuệ nhân tạo rồi đăng ký lại phiên bản 1 từ giá trị gốc. */
  async resetAiConfigs() {
    const response = await this.request.post(`${API}/dev/ai/configs/reset`)
    expect(response.ok()).toBeTruthy()
  }

  /** Cho một nhà cung cấp mô phỏng lỗi ngay (FAIL), treo (HANG) hoặc chạy bình thường (OK). */
  async setAiMockMode(providerCode: string, mode: 'OK' | 'FAIL' | 'HANG' | 'BREAK_MIDWAY') {
    const response = await this.request.post(`${API}/dev/ai/mock/${providerCode}`, { params: { mode } })
    expect(response.ok()).toBeTruthy()
  }

  /** Đặt số lượt trí tuệ nhân tạo đã dùng trong ngày học hiện tại của tài khoản (NCL-04-CN-003). */
  async setQuotaUsage(email: string, usedUnits: number) {
    const response = await this.request.post(`${API}/dev/quota/usage`, { data: { email, usedUnits } })
    expect(response.ok()).toBeTruthy()
  }

  /**
   * Chạy một tác vụ tốn lượt thật qua lớp kết nối (bản mô phỏng) cho tài khoản, như một chức năng học tập tốn lượt.
   * Trả về phản hồi để kiểm cả trường hợp hết lượt hay tác vụ lỗi.
   */
  async runQuotaTask(email: string, task: string, cancel = false) {
    return this.request.post(`${API}/dev/quota/tasks`, { data: { email, task, cancel } })
  }

  /** Xóa sổ lượt, đưa hạn mức chung và bảng quy đổi về mặc định. */
  async resetQuota() {
    const response = await this.request.post(`${API}/dev/quota/reset`)
    expect(response.ok()).toBeTruthy()
  }

  /** Câu trả lời cố định của bản mô phỏng cho các lời gọi sinh văn bản tiếp theo; chuỗi rỗng để bỏ (NCL-04-CN-004). */
  async setAiMockReply(text: string) {
    const response = await this.request.post(`${API}/dev/ai/mock-reply`, { data: { text } })
    expect(response.ok()).toBeTruthy()
  }

  /**
   * Hỏi một câu như người học qua lối sinh nội dung chung (bộ lọc an toàn, lớp bao dữ liệu), khi chưa có trang hỏi
   * đáp. Trả về kết quả: ANSWERED, BLOCKED hay SUPPORT.
   */
  async askSafely(email: string, question: string, sources: { title: string; text: string }[] = []) {
    const response = await this.request.post(`${API}/dev/safety/ask`, { data: { email, question, sources } })
    expect(response.ok()).toBeTruthy()
    return response.json()
  }

  /** Đưa ngưỡng lọc nội dung về mặc định và xóa sổ can thiệp. */
  async resetSafety() {
    const response = await this.request.post(`${API}/dev/safety/reset`)
    expect(response.ok()).toBeTruthy()
  }

  /** Giờ của máy chủ (đồng hồ tua được), để tính mã xác thực khớp với máy chủ. */
  async serverNow(): Promise<number> {
    const response = await this.request.get(`${API}/dev/clock`)
    expect(response.ok()).toBeTruthy()
    return Date.parse((await response.json()).now)
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

  /** Chạy ngay tác vụ xóa tài khoản hằng ngày (NCL-01-CN-009); trả về số tài khoản đã xóa vĩnh viễn. */
  async runAccountPurge(): Promise<number> {
    const response = await this.request.post(`${API}/dev/jobs/account-purge`)
    expect(response.ok()).toBeTruthy()
    return (await response.json()).accounts
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
  // Đường dẫn đổi trước khi trang mới kịp hiện (trang tải riêng): chờ tiêu đề để chắc đang ở màn hình kiểm tra thư.
  await expect(page.getByRole('heading', { name: 'Kiểm tra hộp thư của bạn' })).toBeVisible()
}

/** Tạo tài khoản đã xác thực trên trình duyệt của `page`; trình duyệt đó được đăng nhập luôn. */
export async function createVerifiedAccount(page: Page, dev: DevApi, email: string) {
  await registerThroughUi(page, email)
  await page.goto(await dev.verifyLink(email))
  await expect(page.getByRole('heading', { name: 'Tài khoản đã được kích hoạt' })).toBeVisible()
}

/**
 * Ghi nhận người học đã đọc thông báo đang trò chuyện với hệ thống trí tuệ nhân tạo (NCL-04-CN-005), để các bài kiểm
 * thử không nói về thông báo này vào thẳng khung trò chuyện. Gọi sau khi đã đăng nhập trên {@code page}.
 */
export async function acknowledgeAiDisclosure(page: Page) {
  const cookie = async () => (await page.context().cookies()).find((item) => item.name === 'XSRF-TOKEN')?.value
  if (!(await cookie())) await page.request.get('/api/v1/auth/session')
  const token = await cookie()
  const response = await page.request.post('/api/v1/assistant/disclosure', {
    headers: token ? { 'X-XSRF-TOKEN': token } : {},
  })
  expect(response.ok()).toBeTruthy()
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
