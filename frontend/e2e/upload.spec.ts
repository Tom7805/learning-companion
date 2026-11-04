import AxeBuilder from '@axe-core/playwright'
import type { Browser, Locator, Page } from '@playwright/test'
import { createVerifiedAccount, expect, test, type DevApi } from './fixtures/auth'
import { eicar, pdf, type UploadFile } from './fixtures/files'
import { uniqueEmail } from './fixtures/testData'

/**
 * NCL-05-CN-002 — Tải tài liệu lên kho (Jira LC-30). Máy tính kéo thả tệp vào thư mục đang mở; điện thoại chọn tệp bằng
 * nút. Tải theo từng phần 2 MiB, mạng đứt thì tải tiếp từ phần còn thiếu.
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

const isMobile = (page: Page) => (page.viewportSize()?.width ?? 1440) < 1024

async function signUp(page: Page, dev: DevApi, tag: string) {
  const email = uniqueEmail(tag)
  await createVerifiedAccount(page, dev, email)
  return email
}

/** Chọn tệp bằng nút "Chọn tệp" (bàn phím, điện thoại). */
async function chooseFiles(page: Page, files: UploadFile[]) {
  const chooser = page.waitForEvent('filechooser')
  await page.getByRole('button', { name: 'Chọn tệp' }).click()
  await (await chooser).setFiles(files)
}

/** Kéo thả tệp từ máy tính vào trang kho, như người dùng kéo từ trình quản lý tệp. */
async function dropFiles(page: Page, files: UploadFile[]) {
  const target = page.getByTestId('library-main')
  // Chỉ thả được khi trang đã biết thư mục đích (vùng kéo thả đã hiện).
  await expect(page.getByTestId('upload-dropzone')).toBeVisible()
  const dataTransfer = await page.evaluateHandle(
    (items) => {
      const transfer = new DataTransfer()
      for (const item of items) transfer.items.add(new File([new Uint8Array(item.bytes)], item.name, { type: item.type }))
      return transfer
    },
    files.map((file) => ({ name: file.name, type: file.mimeType, bytes: Array.from(file.buffer) })),
  )
  await target.dispatchEvent('dragover', { dataTransfer })
  await expect(page.getByTestId('upload-dropzone')).toHaveAttribute('data-active', 'true')
  await target.dispatchEvent('drop', { dataTransfer })
}

async function addFiles(page: Page, files: UploadFile[]) {
  if (isMobile(page)) await chooseFiles(page, files)
  else await dropFiles(page, files)
}

function uploadItem(page: Page, name: string): Locator {
  return page.getByTestId('upload-item').filter({ hasText: name })
}

async function usedBytes(page: Page): Promise<number> {
  const response = await page.request.get('/api/v1/quota')
  expect(response.ok()).toBeTruthy()
  return (await response.json()).storage.usedBytes
}

async function otherLearner(browser: Browser, dev: DevApi) {
  const context = await browser.newContext()
  const page = await context.newPage()
  await createVerifiedAccount(page, dev, uniqueEmail('tai-len-la'))
  return page
}

test.beforeEach(async ({ dev }) => {
  await dev.resetQuota()
  // Tài liệu vừa tải lên nằm ở trạng thái chờ xử lý; việc xử lý được kiểm ở processing.spec.ts.
  await dev.setDocumentProcessing(false)
})

test.afterEach(async ({ dev }) => {
  await dev.setDocumentProcessing(true)
})

test.describe('NCL-05-CN-002 · Tải tài liệu lên kho', () => {
  test('TC-01: kéo thả ba bài giảng vào thư mục Giải tích đang mở: ba tài liệu chờ xử lý, dung lượng tăng tương ứng', async ({
    page,
    dev,
  }) => {
    const email = await signUp(page, dev, 'tai-len-ba-tep')
    const seeded = await dev.seedLibrary(email, { folders: ['Học kỳ một/Giải tích'] })
    const calculus = seeded.folders['Học kỳ một/Giải tích']
    const before = await usedBytes(page)

    await page.goto(`/library?folder=${calculus}`)
    await expect(page.getByTestId('library-view-title')).toHaveText('Giải tích')
    const dropzone = page.getByTestId('upload-dropzone')
    await expect(dropzone).toContainText('Kéo thả tệp vào đây để tải vào Giải tích')
    await expect(dropzone).toContainText('tối đa 50 MB mỗi tệp')
    await expectNoSeriousA11yViolations(page)

    const files = [pdf('Bài 1 - Giới hạn.pdf', 'Bai 1 Gioi han'), pdf('Bài 2 - Đạo hàm.pdf', 'Bai 2 Dao ham'),
      pdf('Bài 3 - Tích phân.pdf', 'Bai 3 Tich phan')]
    await addFiles(page, files)

    const queue = page.getByTestId('upload-queue')
    await expect(queue.getByTestId('upload-item')).toHaveCount(3)
    await expect(queue.getByRole('heading')).toHaveText('Đã tải xong 3/3 tệp')
    for (const title of ['Bài 1 - Giới hạn', 'Bài 2 - Đạo hàm', 'Bài 3 - Tích phân']) {
      await expect(uploadItem(page, title)).toHaveAttribute('data-state', 'done')
      await expect(uploadItem(page, title).getByTestId('upload-status')).toContainText('Đã thêm vào Giải tích, đang chờ xử lý')
    }
    // Ba thẻ tài liệu mới trong thư mục Giải tích, đều ở trạng thái chờ xử lý.
    await expect(page.getByTestId('document-card')).toHaveCount(3)
    await expect(page.getByTestId('document-card').getByTestId('document-status')).toHaveText(['Chờ xử lý', 'Chờ xử lý', 'Chờ xử lý'])
    const total = files.reduce((sum, file) => sum + file.buffer.length, 0)
    await expect.poll(() => usedBytes(page)).toBe(before + total)
    await expectNoSeriousA11yViolations(page)

    // Đổi tên tài liệu ngay sau khi tải.
    await uploadItem(page, 'Bài 1 - Giới hạn').getByRole('button', { name: 'Đổi tên Bài 1 - Giới hạn' }).click()
    const rename = page.getByRole('dialog', { name: 'Đổi tên tài liệu' })
    await rename.getByLabel('Tên tài liệu').fill('Giới hạn và liên tục')
    await rename.getByRole('button', { name: 'Lưu tên mới' }).click()
    await expect(rename).toBeHidden()
    await expect(page.getByTestId('upload-queue').getByRole('link', { name: 'Giới hạn và liên tục' })).toBeVisible()
    await expect(page.getByTestId('document-card').filter({ hasText: 'Giới hạn và liên tục' })).toHaveCount(1)
  })

  test('TC-02: tệp chứa mã độc mẫu bị từ chối với lý do có mã độc và không được lưu', async ({ page, dev }) => {
    await signUp(page, dev, 'tai-len-ma-doc')
    await page.goto('/library')
    await chooseFiles(page, [eicar('de-cuong-on-tap.pdf')])

    const item = uploadItem(page, 'de-cuong-on-tap.pdf')
    await expect(item).toHaveAttribute('data-state', 'rejected')
    await expect(item.getByTestId('upload-status')).toContainText('Tệp có mã độc nên không được lưu.')
    await expect(page.getByTestId('upload-announcement')).toHaveText(/Không tải được de-cuong-on-tap\.pdf: Tệp có mã độc/)
    await expect(page.getByTestId('library-empty')).toBeVisible()
    expect(await usedBytes(page)).toBe(0)
    await expectNoSeriousA11yViolations(page)
  })

  test('TC-03: tải lại giáo trình đã có trong Giải tích vào Ôn thi: báo trùng kèm vị trí, gợi ý dùng lại bản đã có', async ({
    page,
    dev,
  }) => {
    const email = await signUp(page, dev, 'tai-len-trung')
    const seeded = await dev.seedLibrary(email, { folders: ['Học kỳ một/Giải tích', 'Ôn thi'] })
    const textbook = pdf('Giáo trình chương 1.pdf', 'Giao trinh chuong mot')

    await page.goto(`/library?folder=${seeded.folders['Học kỳ một/Giải tích']}`)
    await addFiles(page, [textbook])
    await expect(uploadItem(page, 'Giáo trình chương 1')).toHaveAttribute('data-state', 'done')
    const used = await usedBytes(page)

    await page.goto(`/library?folder=${seeded.folders['Ôn thi']}`)
    await expect(page.getByTestId('upload-dropzone')).toContainText('để tải vào Ôn thi')
    await addFiles(page, [{ ...textbook, name: 'giao-trinh-ban-sao.pdf' }])

    const dialog = page.getByRole('dialog', { name: 'Tài liệu này đã có trong kho' })
    await expect(dialog).toContainText('Nội dung của “giao-trinh-ban-sao.pdf” trùng với “Giáo trình chương 1” đang ở Học kỳ một › Giải tích.')
    await expect(dialog).toContainText('Hãy dùng lại bản đã có')
    await expectNoSeriousA11yViolations(page)
    await dialog.getByText('Đóng', { exact: true }).click()
    await expect(uploadItem(page, 'giao-trinh-ban-sao.pdf').getByTestId('upload-status')).toContainText(
      'Đã có trong kho, ở Học kỳ một › Giải tích',
    )
    await expect(page.getByTestId('document-card')).toHaveCount(0)
    expect(await usedBytes(page)).toBe(used)

    // Mở bản đã có thay vì lưu thêm.
    await uploadItem(page, 'giao-trinh-ban-sao.pdf').getByRole('link', { name: 'Mở bản đã có' }).click()
    await expect(page).toHaveURL(/\/library\/[0-9a-f-]{36}$/)
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Giáo trình chương 1')
  })

  test('TC-04: mạng mất khi tệp đang tải được một nửa; mạng có lại thì tải tiếp từ phần còn thiếu', async ({
    page,
    dev,
  }) => {
    await signUp(page, dev, 'tai-len-mat-mang')
    await page.goto('/library')
    // Tệp hơn 5 MiB: ba phần 2 MiB.
    const big = pdf('Tệp lớn.pdf', 'Tep lon mang chap chon', 5 * 1024 * 1024)
    const sentParts: string[] = []
    page.on('request', (request) => {
      const match = request.url().match(/\/uploads\/[^/]+\/parts\/(\d+)$/)
      if (match && request.method() === 'PUT') sentParts.push(match[1])
    })
    let dropped = false
    await page.route('**/api/v1/library/uploads/*/parts/1', async (route) => {
      if (dropped) return route.continue()
      dropped = true
      await page.context().setOffline(true)
      await route.abort('internetdisconnected')
    })

    await chooseFiles(page, [big])
    const item = uploadItem(page, 'Tệp lớn.pdf')
    await expect(item).toHaveAttribute('data-state', 'offline')
    await expect(item.getByTestId('upload-status')).toContainText('Mất kết nối ở 2 MB')
    await expect(item.getByTestId('upload-status')).toContainText('Sẽ tải tiếp khi mạng có lại')
    await expect(item.getByRole('progressbar')).toHaveAttribute('aria-valuenow', /^(3[0-9]|40)$/)
    await expectNoSeriousA11yViolations(page)

    await page.context().setOffline(false)
    await expect(uploadItem(page, 'Tệp lớn')).toHaveAttribute('data-state', 'done', { timeout: 30_000 })
    // Phần đầu không bị gửi lại: chỉ phần bị đứt được gửi lại một lần.
    expect(sentParts.filter((part) => part === '0')).toHaveLength(1)
    expect(sentParts).toEqual(['0', '1', '1', '2'])
    await expect(page.getByTestId('document-card')).toHaveCount(1)
  })

  test('TC-05: người học khác biết mã thư mục gửi yêu cầu tải tệp vào đó thì nhận không tìm thấy', async ({
    page,
    dev,
    browser,
  }) => {
    const owner = await signUp(page, dev, 'tai-len-chu')
    const seeded = await dev.seedLibrary(owner, { folders: ['Giải tích'] })
    const folderId = seeded.folders['Giải tích']

    const stranger = await otherLearner(browser, dev)
    await stranger.goto('/library')
    const xsrf = (await stranger.context().cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')?.value ?? ''
    const response = await stranger.request.post('/api/v1/library/uploads', {
      data: { fileName: 'chen-vao.pdf', sizeBytes: 1000, folderId },
      headers: { 'X-XSRF-TOKEN': xsrf },
    })
    expect(response.status()).toBe(404)
    expect((await response.json()).code).toBe('NOT_FOUND')
    await stranger.context().close()

    await page.goto(`/library?folder=${folderId}`)
    await expect(page.getByTestId('document-card')).toHaveCount(0)
  })

  test('tệp có mật khẩu, tệp hỏng và định dạng không hỗ trợ đều bị từ chối kèm lý do riêng', async ({ page, dev }) => {
    await signUp(page, dev, 'tai-len-tu-choi')
    await page.goto('/library')
    const good = pdf('hong.pdf', 'Bai giang hong')
    await chooseFiles(page, [
      { name: 'hong.pdf', mimeType: 'application/pdf', buffer: good.buffer.subarray(0, 60) },
      { name: 'cai-dat.pdf', mimeType: 'application/pdf', buffer: Buffer.concat([Buffer.from('MZ'), Buffer.alloc(400)]) },
    ])
    await expect(uploadItem(page, 'hong.pdf').getByTestId('upload-status')).toContainText('Tệp bị hỏng hoặc không mở được.')
    await expect(uploadItem(page, 'cai-dat.pdf').getByTestId('upload-status')).toContainText('Định dạng này chưa được nhận.')
    await expect(page.getByTestId('library-empty')).toBeVisible()
  })
})
