import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { routes, workspacePath } from '@/app/routes'
import type { DocumentCard, FolderTree } from '@/features/library/types'
import { apiError } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import type { AttachedDocument, Workspace } from '../types'
import { WorkspacePage } from './WorkspacePage'

const ID = '00000000-0000-4000-8000-0000000000c1'
const FOLDER = '00000000-0000-4000-8000-0000000000a1'

const econometrics: Workspace = {
  id: ID,
  name: 'Kinh tế lượng',
  icon: 'CHART',
  color: 'SKY',
  description: 'Ôn thi cuối kỳ',
  documentCount: 0,
  createdAt: '2026-11-01T00:00:00Z',
  updatedAt: '2026-11-01T00:00:00Z',
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

const statistics = card('doc-1', 'Giáo trình Thống kê', { folderId: FOLDER })
const regression = card('doc-2', 'Hồi quy tuyến tính')
const matrices = card('doc-3', 'Ma trận', { status: 'PROCESSING' })

const tree: FolderTree = {
  folders: [{ id: FOLDER, parentId: null, name: 'Học kỳ một', documentCount: 1, createdAt: '2026-10-01T00:00:00Z' }],
  rootDocumentCount: 2,
  totalDocumentCount: 3,
  starredCount: 0,
}

let attached: AttachedDocument[] = []
let libraryRequests: URL[] = []

beforeEach(() => {
  attached = []
  libraryRequests = []
  server.use(
    http.get('*/api/v1/workspaces/:id', ({ params }) =>
      params.id === ID ? HttpResponse.json(econometrics) : apiError(404, 'NOT_FOUND'),
    ),
    http.get('*/api/v1/workspaces/:id/documents', () => HttpResponse.json(attached)),
    http.get('*/api/v1/library/folders', () => HttpResponse.json(tree)),
    http.get('*/api/v1/library/documents', ({ request }) => {
      const url = new URL(request.url)
      libraryRequests.push(url)
      const inFolder = url.searchParams.get('folderId') === FOLDER
      return HttpResponse.json(inFolder ? [statistics] : [statistics, regression, matrices])
    }),
  )
})

function renderPage(id = ID, search = '') {
  return renderWithProviders(<WorkspacePage />, {
    path: routes.workspace,
    initialEntries: [`${workspacePath(id)}${search}`],
    extraRoutes: [{ path: routes.workspaces, element: <p>Danh sách không gian học</p> }],
  })
}

describe('WorkspacePage', () => {
  it('chưa có tài liệu: phần tổng quan hướng dẫn gắn tài liệu đầu tiên', async () => {
    renderPage()

    expect(await screen.findByRole('heading', { level: 1, name: 'Kinh tế lượng' })).toBeInTheDocument()
    expect(screen.getByText('Ôn thi cuối kỳ')).toBeInTheDocument()
    expect(document.title).toBe('Kinh tế lượng · Learning Companion')
    const guide = await screen.findByTestId('workspace-guide')
    expect(within(guide).getByRole('heading', { name: 'Gắn tài liệu đầu tiên' })).toBeInTheDocument()
    expect(within(guide).getByRole('button', { name: 'Chọn từ kho' })).toBeInTheDocument()
    expect(within(guide).getByRole('button', { name: 'Tải tệp mới' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Tổng quan' })).toHaveAttribute('aria-selected', 'true')
  })

  it('TC-02: gắn tài liệu chọn từ kho qua cây thư mục và ô tìm; tài liệu đã gắn không chọn lại được', async () => {
    attached = [{ document: regression, attachedAt: '2026-11-02T03:00:00Z' }]
    let body: unknown = null
    server.use(
      http.post('*/api/v1/workspaces/:id/documents', async ({ request }) => {
        body = await request.json()
        attached = [{ document: statistics, attachedAt: '2026-11-03T03:00:00Z' }, ...attached]
        return HttpResponse.json({ attached: 1, alreadyAttached: 0 })
      }),
    )
    const { user } = renderPage()

    await user.click(await screen.findByTestId('attach-documents'))
    const dialog = screen.getByRole('dialog', { name: 'Gắn tài liệu' })
    expect(dialog).toHaveTextContent('Tài liệu trong kho được dùng lại, không tải lên lần nữa.')
    const options = await within(dialog).findAllByTestId('attach-option')
    expect(options.map((option) => option.dataset.title)).toEqual(['Giáo trình Thống kê', 'Hồi quy tuyến tính', 'Ma trận'])
    const already = within(dialog).getByRole('checkbox', { name: /Hồi quy tuyến tính/ })
    expect(already).toBeChecked()
    expect(already).toBeDisabled()
    expect(within(options[1]).getByTestId('attach-already')).toHaveTextContent('Đã gắn')
    expect(within(dialog).getByTestId('attach-submit')).toBeDisabled()

    // Chọn thư mục trong cây: chỉ còn tài liệu của thư mục đó.
    await user.click(within(within(dialog).getByTestId('attach-folders')).getByRole('radio', { name: 'Học kỳ một' }))
    await waitFor(() => expect(within(dialog).getAllByTestId('attach-option')).toHaveLength(1))
    expect(libraryRequests.at(-1)?.searchParams.get('folderId')).toBe(FOLDER)

    // Ô tìm không phân biệt dấu.
    await user.click(within(within(dialog).getByTestId('attach-folders')).getByRole('radio', { name: 'Tất cả tài liệu' }))
    await user.type(within(dialog).getByLabelText('Tìm theo tên tài liệu'), 'thong ke')
    await waitFor(() => expect(within(dialog).getAllByTestId('attach-option')).toHaveLength(1))
    await user.click(within(dialog).getByRole('checkbox', { name: /Giáo trình Thống kê/ }))
    expect(within(dialog).getByTestId('attach-selected')).toHaveTextContent('Đã chọn 1 tài liệu')
    await user.click(within(dialog).getByTestId('attach-submit'))

    await waitFor(() => expect(body).toEqual({ documentIds: ['doc-1'] }))
    expect(await screen.findByText('Đã gắn 1 tài liệu vào “Kinh tế lượng”.')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Gắn tài liệu' })).not.toBeInTheDocument())
    await waitFor(() => expect(screen.getByTestId('workspace-stat-total')).toHaveTextContent('2'))
  })

  it('TC-04: gỡ tài liệu cần xác nhận và nói rõ tài liệu vẫn ở kho', async () => {
    attached = [
      { document: statistics, attachedAt: '2026-11-03T03:00:00Z' },
      { document: matrices, attachedAt: '2026-11-02T03:00:00Z' },
    ]
    let detached: string | null = null
    server.use(
      http.delete('*/api/v1/workspaces/:id/documents/:documentId', ({ params }) => {
        detached = String(params.documentId)
        attached = attached.filter((item) => item.document.id !== detached)
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const { user } = renderPage(ID, '?tab=sources')

    const rows = await screen.findAllByTestId('workspace-document')
    expect(rows.map((row) => row.dataset.title)).toEqual(['Giáo trình Thống kê', 'Ma trận'])
    expect(screen.getByRole('tab', { name: /Tài liệu/ })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByTestId('workspace-tab-count')).toHaveTextContent('2')
    expect(within(rows[1]).getByTestId('document-status')).toHaveTextContent('Đang xử lý')

    await user.click(screen.getByRole('button', { name: 'Gỡ Giáo trình Thống kê khỏi không gian học' }))
    const confirm = screen.getByRole('dialog', { name: 'Gỡ tài liệu khỏi không gian học?' })
    expect(confirm).toHaveTextContent('Tài liệu vẫn nguyên trong kho và trong các không gian học khác.')
    await user.click(within(confirm).getByRole('button', { name: 'Gỡ khỏi không gian học' }))

    await waitFor(() => expect(detached).toBe('doc-1'))
    expect(await screen.findByText('Đã gỡ “Giáo trình Thống kê”. Tài liệu vẫn ở trong kho.')).toBeInTheDocument()
    await waitFor(() => expect(screen.getAllByTestId('workspace-document')).toHaveLength(1))
  })

  it('bấm tên tài liệu thì mở khung xem bên qua địa chỉ trang', async () => {
    attached = [{ document: statistics, attachedAt: '2026-11-03T03:00:00Z' }]
    const { user, router } = renderPage(ID, '?tab=sources')

    await user.click(await screen.findByTestId('workspace-document-open'))
    await waitFor(() => expect(router.state.location.search).toBe('?tab=sources&viewer=doc-1'))
  })

  it('TC-05: không gian học của người khác hay không tồn tại hiện trang không tìm thấy', async () => {
    renderPage('00000000-0000-4000-8000-00000000dead')

    expect(await screen.findByRole('heading', { level: 1, name: 'Không tìm thấy không gian học' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Tất cả không gian học' })).toHaveAttribute('href', routes.workspaces)
    expect(screen.queryByTestId('workspace-page')).not.toBeInTheDocument()
  })

  it('xóa không gian học cần xác nhận, nói rõ tài liệu vẫn ở kho rồi về danh sách', async () => {
    let trashed = false
    server.use(
      http.delete('*/api/v1/workspaces/:id', () => {
        trashed = true
        return HttpResponse.json({ trashBatchId: 'b' })
      }),
    )
    const { user, router } = renderPage()

    await user.click(await screen.findByTestId('delete-workspace'))
    const confirm = screen.getByRole('dialog', { name: 'Xóa không gian học “Kinh tế lượng”?' })
    expect(confirm).toHaveTextContent('Tài liệu đã gắn vẫn nguyên trong kho.')
    await user.click(within(confirm).getByRole('button', { name: 'Chuyển vào thùng rác' }))

    await waitFor(() => expect(trashed).toBe(true))
    await waitFor(() => expect(router.state.location.pathname).toBe(routes.workspaces))
  })

  it('sửa tên, biểu tượng và mô tả ngay trên trang', async () => {
    let body: unknown = null
    server.use(
      http.patch('*/api/v1/workspaces/:id', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({ ...econometrics, name: 'Kinh tế lượng 2' })
      }),
    )
    const { user } = renderPage()

    await user.click(await screen.findByTestId('edit-workspace'))
    const dialog = screen.getByRole('dialog', { name: 'Sửa không gian học' })
    const name = within(dialog).getByLabelText('Tên không gian học')
    expect(name).toHaveValue('Kinh tế lượng')
    expect(within(dialog).getByRole('radio', { name: 'Biểu đồ' })).toBeChecked()
    await user.type(name, ' 2')
    await user.click(within(dialog).getByRole('button', { name: 'Lưu thay đổi' }))

    await waitFor(() =>
      expect(body).toEqual({ name: 'Kinh tế lượng 2', icon: 'CHART', color: 'SKY', description: 'Ôn thi cuối kỳ' }),
    )
    expect(await screen.findByText('Đã lưu không gian học “Kinh tế lượng 2”.')).toBeInTheDocument()
  })
})
