import AxeBuilder from '@axe-core/playwright'
import type { Browser, Locator, Page } from '@playwright/test'
import { createVerifiedAccount, expect, test, type DevApi } from './fixtures/auth'
import { navigateTo } from './fixtures/shell'
import { uniqueEmail } from './fixtures/testData'

/**
 * NCL-05-CN-001 — Kho tài liệu với thư mục nhiều cấp, nhãn và gắn sao (Jira LC-29). Chạy trên máy tính (cây thư mục
 * luôn hiện, kéo thả) và điện thoại (cây trong ngăn kéo, chuyển bằng hộp thoại).
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

async function signUp(page: Page, dev: DevApi, tag: string) {
  const email = uniqueEmail(tag)
  await createVerifiedAccount(page, dev, email)
  return email
}

const isMobile = (page: Page) => (page.viewportSize()?.width ?? 1440) < 1024

/** Cây thư mục: cột bên trái trên máy tính, ngăn kéo mở bằng nút "Thư mục: …" trên điện thoại. */
async function folderTree(page: Page): Promise<Locator> {
  if (isMobile(page)) {
    const drawer = page.getByRole('dialog', { name: 'Thư mục và lối tắt' })
    if (!(await drawer.isVisible())) await page.getByTestId('open-folder-tree').click()
    await expect(drawer).toBeVisible()
    return drawer.getByRole('navigation', { name: 'Thư mục và lối tắt' })
  }
  return page.getByRole('navigation', { name: 'Thư mục và lối tắt' })
}

function folderRow(tree: Locator, name: string) {
  return tree.getByTestId('folder-row').filter({ has: tree.page().getByRole('link', { name: new RegExp(`^${name}`) }) })
}

async function closeTree(page: Page) {
  const drawer = page.getByRole('dialog', { name: 'Thư mục và lối tắt' })
  if (await drawer.isVisible()) await page.keyboard.press('Escape')
}

test.describe('NCL-05-CN-001 · Kho tài liệu: thư mục nhiều cấp, nhãn và gắn sao', () => {
  test('TC-01: kéo hai tài liệu từ thư mục gốc vào Kinh tế vi mô, cây thư mục cập nhật ngay số tài liệu', async ({
    page,
    dev,
  }) => {
    const email = await signUp(page, dev, 'kho-keo-tha')
    await dev.seedLibrary(email, {
      folders: ['Học kỳ một/Kinh tế vi mô', 'Học kỳ một/Giải tích', 'Học kỳ một/Vật lý đại cương'],
      documents: [
        { title: 'Cung và cầu', type: 'PDF' },
        { title: 'Độ co giãn', type: 'PPTX' },
        { title: 'Đạo hàm', type: 'DOCX', folder: 'Học kỳ một/Giải tích' },
      ],
    })
    await page.goto('/')
    await navigateTo(page, 'Thư viện')
    let tree = await folderTree(page)
    await tree.getByRole('link', { name: /^Thư mục gốc/ }).click()
    await expect(page.getByTestId('library-view-title')).toHaveText('Thư mục gốc')
    await expect(page.getByTestId('document-card')).toHaveCount(2)
    await expectNoSeriousA11yViolations(page)

    await page.getByRole('checkbox', { name: 'Chọn Cung và cầu' }).check()
    await page.getByRole('checkbox', { name: 'Chọn Độ co giãn' }).check()
    await expect(page.getByTestId('bulk-bar')).toContainText('Đã chọn 2 tài liệu')

    if (isMobile(page)) {
      // Điện thoại không kéo thả được: chuyển bằng hộp thoại chọn thư mục.
      await page.getByTestId('bulk-bar').getByRole('button', { name: 'Chuyển tới…' }).click()
      const dialog = page.getByRole('dialog', { name: 'Chuyển 2 tài liệu' })
      await dialog.getByText('Kinh tế vi mô', { exact: true }).click()
      await expectNoSeriousA11yViolations(page)
      await dialog.getByRole('button', { name: 'Chuyển tới đây' }).click()
    } else {
      await page.getByTestId('document-item').filter({ hasText: 'Cung và cầu' }).getByTestId('document-card')
        .dragTo(folderRow(tree, 'Kinh tế vi mô'))
    }

    await expect(page.getByTestId('toast').filter({ hasText: 'Đã chuyển 2 tài liệu vào Kinh tế vi mô.' })).toBeVisible()
    await expect(page.getByTestId('document-card')).toHaveCount(0)
    await expect(page.getByText('Thư mục này chưa có tài liệu')).toBeVisible()
    tree = await folderTree(page)
    await expect(folderRow(tree, 'Kinh tế vi mô')).toContainText('2')
    await expect(folderRow(tree, 'Thư mục gốc')).toContainText('0')
    await expect(folderRow(tree, 'Học kỳ một')).toContainText('3')
    await tree.getByRole('link', { name: /^Kinh tế vi mô/ }).click()
    await expect(page.getByTestId('library-view-title')).toHaveText('Kinh tế vi mô')
    await expect(page.getByTestId('document-card')).toHaveCount(2)
    await expect(page.getByTestId('document-card').filter({ hasText: 'Cung và cầu' })).toBeVisible()
    await expect(page.getByTestId('document-card').filter({ hasText: 'Độ co giãn' })).toBeVisible()
    await expect(page.getByRole('navigation', { name: 'Thư mục', exact: true })).toContainText('Học kỳ một')
  })

  test('TC-02: kho ba trăm tài liệu, lọc loại trình chiếu và nhãn ôn thi thì chỉ còn đúng các tệp đó', async ({
    page,
    dev,
  }) => {
    test.setTimeout(120_000)
    const email = await signUp(page, dev, 'kho-loc')
    const types = ['PDF', 'DOCX', 'PPTX', 'TEXT', 'IMAGE', 'WEB'] as const
    const documents = Array.from({ length: 300 }, (_, i) => ({
      title: `Tài liệu ${String(i).padStart(3, '0')}`,
      type: types[i % types.length],
      tags: [i % 4 === 0 ? 'Ôn thi' : 'Đọc thêm'],
    }))
    const expected = documents.filter((document) => document.type === 'PPTX' && document.tags[0] === 'Ôn thi')
    await dev.seedLibrary(email, {
      tags: [
        { name: 'Ôn thi', color: 'CORAL' },
        { name: 'Đọc thêm', color: 'SKY' },
      ],
      documents,
    })

    await page.goto('/library')
    await expect(page.getByTestId('library-summary')).toContainText('300 tài liệu')
    await page.getByRole('button', { name: 'Bộ lọc', exact: true }).click()
    const filters = page.getByTestId('library-filters')
    await filters.getByRole('button', { name: 'Trình chiếu' }).click()
    await filters.getByRole('button', { name: 'Ôn thi' }).click()

    await expect(page.getByTestId('library-summary')).toContainText(`${expected.length} tài liệu`)
    const cards = page.getByTestId('document-card')
    await expect(cards).toHaveCount(expected.length)
    for (const card of await cards.all()) {
      await expect(card).toContainText('Trình chiếu')
      await expect(card).toContainText('Ôn thi')
    }
    await expect(page).toHaveURL(/type=PPTX&tag=/)
    await expectNoSeriousA11yViolations(page)

    // Dạng danh sách, sắp xếp theo tên và đường dẫn giữ nguyên bộ lọc khi tải lại.
    await page.getByRole('button', { name: 'Dạng danh sách' }).click()
    await page.getByRole('combobox', { name: 'Sắp xếp theo' }).selectOption('NAME')
    await expect(page.getByTestId('document-list')).toBeVisible()
    await expect(cards.first()).toContainText('Tài liệu 008')
    await page.reload()
    await expect(page.getByTestId('document-list')).toBeVisible()
    await expect(cards).toHaveCount(expected.length)
    await expectNoSeriousA11yViolations(page)
  })

  test('TC-03: tạo thư mục con trùng tên trong cùng thư mục cha bị báo trước khi lưu kèm tên gợi ý', async ({
    page,
    dev,
  }) => {
    const email = await signUp(page, dev, 'kho-trung-ten')
    await dev.seedLibrary(email, { folders: ['Học kỳ một/Giải tích'] })
    await page.goto('/library')

    const tree = await folderTree(page)
    await folderRow(tree, 'Học kỳ một').getByRole('button', { name: 'Thao tác với thư mục Học kỳ một' }).click()
    await page.getByRole('menuitem', { name: 'Tạo thư mục con' }).click()
    const dialog = page.getByRole('dialog', { name: 'Tạo thư mục' })
    await expect(dialog).toContainText('Trong Học kỳ một')
    await dialog.getByLabel('Tên thư mục').fill('giải tích')
    await expect(dialog.getByText('Đã có “giải tích” trong thư mục này.')).toBeVisible()
    await expect(dialog.getByRole('button', { name: 'Tạo thư mục' })).toBeDisabled()
    await expectNoSeriousA11yViolations(page)

    await dialog.getByRole('button', { name: 'Dùng tên “giải tích (2)”' }).click()
    await expect(dialog.getByRole('img', { name: 'Tên này dùng được' })).toBeVisible()
    await dialog.getByRole('button', { name: 'Tạo thư mục' }).click()
    await expect(page.getByTestId('toast').filter({ hasText: 'Đã tạo thư mục “giải tích (2)”.' })).toBeVisible()
    const after = await folderTree(page)
    await expect(after.getByRole('link', { name: /^giải tích \(2\)/ })).toBeVisible()
    await expect(after.getByRole('link', { name: /^Giải tích/ })).toBeVisible()
    await closeTree(page)
  })

  test('TC-04: xóa thư mục có mười tài liệu thì cả thư mục và tài liệu vào thùng rác, biến khỏi cây', async ({
    page,
    dev,
  }) => {
    const email = await signUp(page, dev, 'kho-xoa')
    const seeded = await dev.seedLibrary(email, {
      folders: ['Học kỳ một/Giải tích', 'Học kỳ hai'],
      documents: [
        ...Array.from({ length: 10 }, (_, i) => ({
          title: `Bài ${i + 1}`,
          folder: i < 7 ? 'Học kỳ một' : 'Học kỳ một/Giải tích',
        })),
        { title: 'Ở thư mục gốc' },
      ],
    })
    await page.goto(`/library?folder=${seeded.folders['Học kỳ một']}`)
    await expect(page.getByTestId('document-card')).toHaveCount(7)

    let tree = await folderTree(page)
    await folderRow(tree, 'Học kỳ một').getByRole('button', { name: 'Thao tác với thư mục Học kỳ một' }).click()
    await page.getByRole('menuitem', { name: 'Xóa thư mục' }).click()
    const confirm = page.getByRole('dialog', { name: 'Xóa thư mục “Học kỳ một”?' })
    await expect(confirm).toContainText('10 tài liệu bên trong sẽ vào thùng rác')
    await expectNoSeriousA11yViolations(page)
    await confirm.getByRole('button', { name: 'Chuyển vào thùng rác' }).click()

    await expect(
      page.getByTestId('toast').filter({ hasText: 'Đã chuyển thư mục “Học kỳ một” và 10 tài liệu vào thùng rác.' }),
    ).toBeVisible()
    await expect(page).toHaveURL(/view=root/)
    await expect(page.getByTestId('document-card')).toHaveText([/Ở thư mục gốc/])
    tree = await folderTree(page)
    await expect(tree.getByRole('link', { name: /^Học kỳ một/ })).toHaveCount(0)
    await expect(tree.getByRole('link', { name: /^Giải tích/ })).toHaveCount(0)
    await expect(tree.getByRole('link', { name: /^Học kỳ hai/ })).toBeVisible()
    await expect(tree.getByRole('link', { name: /^Tất cả tài liệu/ })).toContainText('1')
    await closeTree(page)

    // Tài liệu trong thùng rác không mở được bằng đường dẫn cũ.
    await page.goto(`/library/${seeded.documents[0]}`)
    await expect(page.getByText('Không tìm thấy tài liệu')).toBeVisible()
  })

  test('TC-05: người học khác mở thư mục bằng đường dẫn trực tiếp thì nhận không tìm thấy', async ({
    page,
    dev,
    browser,
  }) => {
    const owner = await signUp(page, dev, 'kho-chu')
    const seeded = await dev.seedLibrary(owner, {
      folders: ['Học kỳ một'],
      documents: [{ title: 'Nhật ký riêng của Lan', folder: 'Học kỳ một' }],
    })
    const folderId = seeded.folders['Học kỳ một']

    const stranger = await otherLearner(browser, dev)
    const denied = stranger.waitForResponse((response) => response.url().includes(`folderId=${folderId}`))
    await stranger.goto(`/library?folder=${folderId}`)
    expect((await denied).status()).toBe(404)
    await expect(stranger.getByText('Không tìm thấy thư mục')).toBeVisible()
    await expect(stranger.getByText('Nhật ký riêng của Lan')).toHaveCount(0)
    await expectNoSeriousA11yViolations(stranger)
    await stranger.getByRole('link', { name: 'Về tất cả tài liệu' }).click()
    await expect(stranger).toHaveURL(/\/library$/)
    await expect(stranger.getByTestId('library-empty')).toBeVisible()
    await stranger.context().close()

    // Chủ thư mục vẫn mở được bình thường.
    await page.goto(`/library?folder=${folderId}`)
    await expect(page.getByTestId('document-card')).toHaveText([/Nhật ký riêng của Lan/])
  })

  test('nhãn màu, gắn sao, lối tắt và khung thông tin có nút tải tệp gốc', async ({ page, dev }) => {
    const email = await signUp(page, dev, 'kho-nhan')
    await dev.seedLibrary(email, {
      folders: ['Học kỳ một/Kinh tế vi mô'],
      documents: [
        { title: 'Cung và cầu', type: 'PDF', folder: 'Học kỳ một/Kinh tế vi mô' },
        { title: 'Ghi chú tự học', status: 'PROCESSING' },
      ],
    })
    await page.goto('/library')

    // Tạo nhãn trong hộp thoại quản lý nhãn.
    await page.getByRole('button', { name: 'Bộ lọc', exact: true }).click()
    await page.getByRole('button', { name: 'Quản lý nhãn' }).click()
    const manager = page.getByRole('dialog', { name: 'Nhãn của bạn' })
    await manager.getByLabel('Nhãn mới').fill('Ôn thi')
    await manager.getByRole('radio', { name: 'Màu Cam san hô' }).check({ force: true })
    await manager.getByRole('button', { name: 'Thêm nhãn' }).click()
    await expect(manager.getByTestId('tag-list')).toContainText('Ôn thi')
    await manager.getByLabel('Nhãn mới').fill('ôn THI')
    await manager.getByRole('button', { name: 'Thêm nhãn' }).click()
    await expect(manager.getByText('Bạn đã có nhãn tên này.')).toBeVisible()
    await expectNoSeriousA11yViolations(page)
    await manager.getByRole('button', { name: 'Đóng' }).click()

    // Gắn nhãn và gắn sao từ thẻ, xem trong khung thông tin.
    await page.getByRole('checkbox', { name: 'Chọn Cung và cầu' }).check()
    await page.getByTestId('bulk-bar').getByRole('button', { name: 'Gắn nhãn…' }).click()
    const assign = page.getByRole('dialog', { name: 'Nhãn của “Cung và cầu”' })
    await assign.getByRole('checkbox', { name: 'Ôn thi' }).check()
    await assign.getByRole('button', { name: 'Gắn nhãn' }).click()
    await expect(page.getByTestId('toast').filter({ hasText: 'Đã cập nhật nhãn.' })).toBeVisible()
    const card = page.getByTestId('document-item').filter({ hasText: 'Cung và cầu' })
    await expect(card.getByTestId('tag-chip')).toHaveText('Ôn thi')
    await card.getByRole('button', { name: 'Gắn sao Cung và cầu' }).click()
    await expect(card.getByRole('button', { name: 'Bỏ sao Cung và cầu' })).toHaveAttribute('aria-pressed', 'true')
    await expect(page.getByTestId('document-item').filter({ hasText: 'Ghi chú tự học' })).toContainText('Đang xử lý')

    await card.getByRole('button', { name: 'Thông tin Cung và cầu' }).click()
    const info = page.getByTestId('side-panel').getByTestId('document-info')
    await expect(info.getByTestId('info-folder')).toHaveText('Học kỳ một / Kinh tế vi mô')
    await expect(info.getByTestId('info-tags')).toHaveText('Ôn thi')
    await expect(info).toContainText('Chưa mở lần nào')
    await expectNoSeriousA11yViolations(page)
    const download = page.waitForEvent('download')
    await info.getByRole('button', { name: 'Tải tệp gốc' }).click()
    expect((await download).suggestedFilename()).toBe('cung-va-cau.pdf')
    await page.keyboard.press('Escape')

    // Lối tắt "Có gắn sao" và "Gần đây" ở đầu cây.
    let tree = await folderTree(page)
    await tree.getByRole('link', { name: /^Có gắn sao/ }).click()
    await expect(page.getByTestId('document-card')).toHaveText([/Cung và cầu/])
    await page.getByTestId('document-card').click()
    await expect(page.getByRole('heading', { level: 1, name: 'Cung và cầu' })).toBeVisible()
    await page.goto('/library?view=recent')
    await expect(page.getByTestId('document-card').first()).toContainText('Cung và cầu')
    tree = await folderTree(page)
    await expect(tree.getByRole('link', { name: /^Gần đây/ })).toHaveAttribute('aria-current', 'page')
    await expectNoSeriousA11yViolations(page)
    await closeTree(page)
  })
})

async function otherLearner(browser: Browser, dev: DevApi) {
  const context = await browser.newContext()
  const page = await context.newPage()
  await createVerifiedAccount(page, dev, uniqueEmail('kho-la'))
  return page
}
