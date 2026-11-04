import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { routes } from '@/app/routes'
import { SidePanel } from '@/layouts/SidePanel'
import { apiError, sampleDocument } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import type { DocumentCard, FolderTree, Tag } from '../types'
import { LibraryPage } from './LibraryPage'

const SEMESTER = '00000000-0000-4000-8000-0000000000a1'
const MICRO = '00000000-0000-4000-8000-0000000000a2'
const CALCULUS = '00000000-0000-4000-8000-0000000000a3'
const EXAM: Tag = { id: '00000000-0000-4000-8000-0000000000b1', name: 'Ôn thi', color: 'CORAL', documentCount: 1 }

const tree: FolderTree = {
  folders: [
    { id: SEMESTER, parentId: null, name: 'Học kỳ một', documentCount: 0, createdAt: '2026-10-01T00:00:00Z' },
    { id: MICRO, parentId: SEMESTER, name: 'Kinh tế vi mô', documentCount: 0, createdAt: '2026-10-01T00:00:00Z' },
    { id: CALCULUS, parentId: SEMESTER, name: 'Giải tích', documentCount: 3, createdAt: '2026-10-01T00:00:00Z' },
  ],
  rootDocumentCount: 2,
  totalDocumentCount: 5,
  starredCount: 1,
}

const card = (id: string, title: string, extra: Partial<DocumentCard> = {}): DocumentCard => ({
  id,
  title,
  type: 'PDF',
  originalFilename: `${id}.pdf`,
  sizeBytes: 2048,
  createdAt: '2026-10-11T03:00:00Z',
  folderId: null,
  tagIds: [],
  starred: false,
  status: 'READY',
  ...extra,
})

const supply = card('doc-1', 'Cung và cầu', { tagIds: [EXAM.id] })
const elasticity = card('doc-2', 'Độ co giãn', { type: 'PPTX' })

let requests: URL[] = []
let bulkBodies: unknown[] = []

beforeEach(() => {
  requests = []
  bulkBodies = []
  server.use(
    http.get('*/api/v1/library/folders', () => HttpResponse.json(tree)),
    http.get('*/api/v1/library/tags', () => HttpResponse.json([EXAM])),
    http.get('*/api/v1/library/documents', ({ request }) => {
      requests.push(new URL(request.url))
      return HttpResponse.json([supply, elasticity])
    }),
    http.post('*/api/v1/library/documents/bulk', async ({ request }) => {
      const body = (await request.json()) as { documentIds: string[] }
      bulkBodies.push(body)
      return HttpResponse.json({ action: 'MOVE', affected: body.documentIds.length })
    }),
  )
})

function renderLibrary(search = '') {
  return renderWithProviders(
    <>
      <LibraryPage />
      <SidePanel />
    </>,
    { path: routes.library, initialEntries: [`${routes.library}${search}`] },
  )
}

const lastRequest = () => requests.at(-1)!

describe('Kho tài liệu (NCL-05-CN-001)', () => {
  it('cây thư mục có hai lối tắt ở đầu, thư mục gốc và thư mục lồng nhau kèm số tài liệu cả cây con', async () => {
    const { user, router } = renderLibrary()
    const nav = await screen.findByRole('navigation', { name: 'Thư mục và lối tắt' })
    const links = within(nav).getAllByRole('link')
    expect(links.slice(0, 3).map((link) => link.textContent)).toEqual(['Gần đây', 'Có gắn sao1', 'Tất cả tài liệu5'])
    expect(within(nav).getByRole('link', { name: /^Học kỳ một/ })).toHaveTextContent('3')
    expect(within(nav).getByRole('link', { name: /^Thư mục gốc/ })).toHaveTextContent('2')

    await user.click(within(nav).getByRole('button', { name: 'Thu gọn Học kỳ một' }))
    expect(within(nav).queryByRole('link', { name: /^Giải tích/ })).not.toBeInTheDocument()
    await user.click(within(nav).getByRole('button', { name: 'Mở Học kỳ một' }))

    await user.click(within(nav).getByRole('link', { name: /^Kinh tế vi mô/ }))
    expect(router.state.location.search).toBe(`?folder=${MICRO}`)
    await waitFor(() => expect(lastRequest().searchParams.get('folderId')).toBe(MICRO))
    expect(lastRequest().searchParams.get('scope')).toBe('FOLDER')
    expect(screen.getByTestId('library-view-title')).toHaveTextContent('Kinh tế vi mô')
    expect(screen.getByRole('navigation', { name: 'Thư mục' })).toHaveTextContent('Thư mục gốcHọc kỳ mộtKinh tế vi mô')
  })

  it('TC-02: lọc loại trình chiếu và nhãn ôn thi gửi đúng điều kiện và ghi lên địa chỉ trang', async () => {
    const { user, router } = renderLibrary()
    await user.click(await screen.findByRole('button', { name: 'Bộ lọc' }))
    const filters = screen.getByTestId('library-filters')
    await user.click(within(filters).getByRole('button', { name: 'Trình chiếu' }))
    await user.click(within(filters).getByRole('button', { name: 'Ôn thi' }))

    await waitFor(() => expect(lastRequest().searchParams.getAll('tag')).toEqual([EXAM.id]))
    expect(lastRequest().searchParams.getAll('type')).toEqual(['PPTX'])
    expect(router.state.location.search).toBe(`?type=PPTX&tag=${EXAM.id}`)
    expect(within(filters).getByRole('button', { name: 'Ôn thi' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: /^Bộ lọc.*2 bộ lọc$/ })).toBeInTheDocument()

    await user.selectOptions(within(filters).getByRole('combobox', { name: 'Sắp xếp theo' }), 'NAME')
    await waitFor(() => expect(lastRequest().searchParams.get('sort')).toBe('NAME'))
    expect(lastRequest().searchParams.get('direction')).toBe('ASC')
    await user.click(within(filters).getByRole('button', { name: 'Xóa bộ lọc' }))
    await waitFor(() => expect(lastRequest().searchParams.getAll('type')).toEqual([]))
  })

  it('lọc khoảng ngày theo giờ địa phương: "đến ngày" tính trọn ngày đó', async () => {
    renderLibrary('?from=2026-10-01&to=2026-10-31')
    await screen.findAllByTestId('document-card')
    expect(lastRequest().searchParams.get('from')).toBe(new Date(2026, 9, 1).toISOString())
    expect(lastRequest().searchParams.get('to')).toBe(new Date(2026, 10, 1).toISOString())
  })

  it('TC-01: chọn hai tài liệu rồi chuyển vào Kinh tế vi mô bằng hộp thoại, không cần kéo thả', async () => {
    const { user } = renderLibrary()
    await user.click(await screen.findByRole('checkbox', { name: 'Chọn Cung và cầu' }))
    await user.click(screen.getByRole('checkbox', { name: 'Chọn Độ co giãn' }))
    const bar = screen.getByRole('region', { name: 'Thao tác với tài liệu đã chọn' })
    expect(bar).toHaveTextContent('Đã chọn 2 tài liệu')

    await user.click(within(bar).getByRole('button', { name: 'Chuyển tới…' }))
    const dialog = screen.getByRole('dialog', { name: 'Chuyển 2 tài liệu' })
    expect(within(dialog).getByRole('radio', { name: /Thư mục gốc/ })).toBeDisabled()
    await user.click(within(dialog).getByRole('radio', { name: 'Kinh tế vi mô' }))
    await user.click(within(dialog).getByRole('button', { name: 'Chuyển tới đây' }))

    await waitFor(() => expect(bulkBodies).toEqual([{ action: 'MOVE', documentIds: ['doc-1', 'doc-2'], folderId: MICRO }]))
    expect(await screen.findByText('Đã chuyển 2 tài liệu vào Kinh tế vi mô.')).toBeInTheDocument()
  })

  it('chuyển vào thư mục đã có tài liệu cùng tên thì hộp thoại nêu tên trùng', async () => {
    server.use(
      http.post('*/api/v1/library/documents/bulk', () =>
        apiError(409, 'LIBRARY_NAME_TAKEN', 'folderId', { conflicts: ['Cung và cầu'] }),
      ),
    )
    const { user } = renderLibrary()
    await user.click(await screen.findByRole('checkbox', { name: 'Chọn Cung và cầu' }))
    await user.click(screen.getByRole('button', { name: 'Chuyển tới…' }))
    const dialog = screen.getByRole('dialog', { name: 'Chuyển 1 tài liệu' })
    await user.click(within(dialog).getByRole('radio', { name: 'Giải tích' }))
    await user.click(within(dialog).getByRole('button', { name: 'Chuyển tới đây' }))
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('đã có tài liệu cùng tên: “Cung và cầu”')
  })

  it('TC-03: tên thư mục trùng được báo ngay khi gõ, kèm tên gợi ý bấm là dùng', async () => {
    let created: unknown = null
    server.use(
      http.get('*/api/v1/library/folders/name-check', ({ request }) => {
        const name = new URL(request.url).searchParams.get('name')
        return HttpResponse.json(name?.toLowerCase() === 'giải tích' ? { available: false, suggestion: 'Giải tích (2)' } : { available: true })
      }),
      http.post('*/api/v1/library/folders', async ({ request }) => {
        created = await request.json()
        return HttpResponse.json({ id: 'new', parentId: SEMESTER, name: 'Giải tích (2)', documentCount: 0, createdAt: '2026-11-04T00:00:00Z' }, { status: 201 })
      }),
    )
    const { user } = renderLibrary()
    const nav = await screen.findByRole('navigation', { name: 'Thư mục và lối tắt' })
    await user.click(within(nav).getByRole('button', { name: 'Thao tác với thư mục Học kỳ một' }))
    await user.click(screen.getByRole('menuitem', { name: 'Tạo thư mục con' }))
    const dialog = screen.getByRole('dialog', { name: 'Tạo thư mục' })
    expect(dialog).toHaveTextContent('Trong Học kỳ một')

    await user.type(within(dialog).getByLabelText('Tên thư mục'), 'Giải tích')
    expect(await within(dialog).findByText('Đã có “Giải tích” trong thư mục này.')).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Tạo thư mục' })).toBeDisabled()
    expect(within(dialog).getByRole('button', { name: 'Hủy' })).toBeInTheDocument()

    await user.click(within(dialog).getByRole('button', { name: 'Dùng tên “Giải tích (2)”' }))
    expect(within(dialog).getByLabelText('Tên thư mục')).toHaveValue('Giải tích (2)')
    await waitFor(() => expect(within(dialog).getByRole('button', { name: 'Tạo thư mục' })).toBeEnabled())
    await user.click(within(dialog).getByRole('button', { name: 'Tạo thư mục' }))
    await waitFor(() => expect(created).toEqual({ name: 'Giải tích (2)', parentId: SEMESTER }))
    expect(await screen.findByText('Đã tạo thư mục “Giải tích (2)”.')).toBeInTheDocument()
  })

  it('TC-04: xóa thư mục cần xác nhận, báo số tài liệu vào thùng rác', async () => {
    let deleted: string | null = null
    server.use(
      http.delete('*/api/v1/library/folders/:id', ({ params }) => {
        deleted = String(params.id)
        return HttpResponse.json({ batchId: 'b', folders: 3, documents: 3 })
      }),
    )
    const { user, router } = renderLibrary(`?folder=${CALCULUS}`)
    const nav = await screen.findByRole('navigation', { name: 'Thư mục và lối tắt' })
    await user.click(within(nav).getByRole('button', { name: 'Thao tác với thư mục Học kỳ một' }))
    await user.click(screen.getByRole('menuitem', { name: 'Xóa thư mục' }))
    const confirm = screen.getByRole('dialog', { name: 'Xóa thư mục “Học kỳ một”?' })
    expect(confirm).toHaveTextContent('3 tài liệu bên trong sẽ vào thùng rác')
    await user.click(within(confirm).getByRole('button', { name: 'Chuyển vào thùng rác' }))

    await waitFor(() => expect(deleted).toBe(SEMESTER))
    expect(await screen.findByText('Đã chuyển thư mục “Học kỳ một” và 3 tài liệu vào thùng rác.')).toBeInTheDocument()
    // Đang xem thư mục con của thư mục vừa xóa thì về thư mục cha của nó.
    await waitFor(() => expect(router.state.location.search).toBe('?view=root'))
  })

  it('TC-05: thư mục không tìm thấy (của người khác hay đã xóa) thì báo rõ và cho về tất cả tài liệu', async () => {
    server.use(http.get('*/api/v1/library/documents', () => apiError(404, 'NOT_FOUND')))
    renderLibrary('?folder=00000000-0000-4000-8000-00000000dead')
    expect(await screen.findByText('Không tìm thấy thư mục')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Về tất cả tài liệu' })).toHaveAttribute('href', routes.library)
  })

  it('gắn sao, đổi dạng danh sách và mở khung thông tin có thư mục, nhãn, nút tải tệp gốc', async () => {
    let starred: unknown = null
    server.use(
      http.put('*/api/v1/library/documents/:id/star', async ({ request }) => {
        starred = await request.json()
        return HttpResponse.json({ ...supply, starred: true })
      }),
      http.get('*/api/v1/library/documents/:id', ({ request }) =>
        HttpResponse.json({
          ...sampleDocument,
          ...supply,
          folderId: MICRO,
          track: new URL(request.url).searchParams.get('track'),
        }),
      ),
    )
    const { user } = renderLibrary()
    await user.click(await screen.findByRole('button', { name: 'Gắn sao Cung và cầu' }))
    await waitFor(() => expect(starred).toEqual({ starred: true }))
    expect(await screen.findByText('Đã gắn sao “Cung và cầu”.')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Dạng danh sách' }))
    expect(screen.getByTestId('document-list')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Dạng danh sách' })).toHaveAttribute('aria-pressed', 'true')

    await user.click(screen.getByRole('button', { name: 'Thông tin Cung và cầu' }))
    const info = await within(screen.getByTestId('side-panel')).findByTestId('document-info')
    expect(within(info).getByTestId('info-folder')).toHaveTextContent('Học kỳ một / Kinh tế vi mô')
    expect(within(info).getByTestId('info-tags')).toHaveTextContent('Ôn thi')
    expect(within(info).getByRole('button', { name: 'Tải tệp gốc' })).toBeInTheDocument()
    expect(info).toHaveTextContent('2 KB')
  })
})
