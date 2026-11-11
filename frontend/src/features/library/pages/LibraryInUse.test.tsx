import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { routes, workspacePath } from '@/app/routes'
import type { WorkspaceSummary } from '@/features/workspaces/types'
import { SidePanel } from '@/layouts/SidePanel'
import { apiError, sampleDocument } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import type { DocumentCard, FolderTree } from '../types'
import { LibraryPage } from './LibraryPage'

const SEMESTER = '00000000-0000-4000-8000-0000000000a1'

const tree: FolderTree = {
  folders: [{ id: SEMESTER, parentId: null, name: 'Học kỳ một', documentCount: 1, createdAt: '2026-10-01T00:00:00Z' }],
  rootDocumentCount: 1,
  totalDocumentCount: 2,
  starredCount: 0,
}

const shared: DocumentCard = {
  id: sampleDocument.id,
  title: 'Giáo trình Thống kê',
  type: 'PDF',
  originalFilename: 'thong-ke.pdf',
  sizeBytes: 2048,
  createdAt: '2026-10-11T03:00:00Z',
  folderId: null,
  tagIds: [],
  starred: false,
  status: 'READY',
}

const workspaces: WorkspaceSummary[] = [
  { id: '00000000-0000-4000-8000-0000000000c1', name: 'Kinh tế lượng', icon: 'CHART', color: 'SKY' },
  { id: '00000000-0000-4000-8000-0000000000c2', name: 'Thống kê', icon: 'CALCULATOR', color: 'MINT' },
  { id: '00000000-0000-4000-8000-0000000000c3', name: 'Xác suất', icon: 'BOOK', color: 'SUN' },
]

let usageRequests: URL[] = []

beforeEach(() => {
  usageRequests = []
  server.use(
    http.get('*/api/v1/library/folders', () => HttpResponse.json(tree)),
    http.get('*/api/v1/library/documents', () => HttpResponse.json([shared])),
    http.get('*/api/v1/workspaces/usage', ({ request }) => {
      usageRequests.push(new URL(request.url))
      return HttpResponse.json(workspaces)
    }),
  )
})

function renderLibrary() {
  return renderWithProviders(
    <>
      <LibraryPage />
      <SidePanel />
    </>,
    { path: routes.library },
  )
}

/** NCL-06-CN-001: kho tài liệu báo tài liệu đang được không gian học nào dùng. */
describe('LibraryPage: tài liệu đang gắn với không gian học', () => {
  it('TC-03: xóa tài liệu đang được ba không gian học dùng thì hộp thoại nêu tên cả ba, xác nhận mới xóa', async () => {
    const bodies: unknown[] = []
    server.use(
      http.post('*/api/v1/library/documents/bulk', async ({ request }) => {
        bodies.push(await request.json())
        return HttpResponse.json({ action: 'TRASH', affected: 1, trashBatchId: 'b' })
      }),
    )
    const { user } = renderLibrary()

    await user.click(await screen.findByRole('checkbox', { name: 'Chọn Giáo trình Thống kê' }))
    await user.click(within(screen.getByTestId('bulk-bar')).getByRole('button', { name: /Xóa/ }))
    const confirm = screen.getByRole('dialog', { name: 'Xóa 1 tài liệu?' })
    const impact = await within(confirm).findByTestId('delete-impact')
    expect(impact).toHaveTextContent('Đang được dùng trong 3 không gian học:')
    expect(within(impact).getAllByTestId('delete-impact-workspace').map((item) => item.textContent)).toEqual([
      'Kinh tế lượng',
      'Thống kê',
      'Xác suất',
    ])
    expect(usageRequests.at(-1)?.searchParams.getAll('documentId')).toEqual([shared.id])

    // Hủy thì không có gì bị xóa.
    await user.click(within(confirm).getByRole('button', { name: 'Hủy' }))
    expect(bodies).toEqual([])

    await user.click(within(screen.getByTestId('bulk-bar')).getByRole('button', { name: /Xóa/ }))
    const again = screen.getByRole('dialog', { name: 'Xóa 1 tài liệu?' })
    await within(again).findByTestId('delete-impact')
    await user.click(within(again).getByRole('button', { name: 'Chuyển vào thùng rác' }))

    await waitFor(() => expect(bodies).toEqual([{ action: 'TRASH', documentIds: [shared.id], confirmInUse: true }]))
  })

  it('tài liệu không gắn với không gian học nào thì xóa như trước, không gửi xác nhận thêm', async () => {
    const bodies: unknown[] = []
    server.use(
      http.get('*/api/v1/workspaces/usage', () => HttpResponse.json([])),
      http.post('*/api/v1/library/documents/bulk', async ({ request }) => {
        bodies.push(await request.json())
        return HttpResponse.json({ action: 'TRASH', affected: 1, trashBatchId: 'b' })
      }),
    )
    const { user } = renderLibrary()

    await user.click(await screen.findByRole('checkbox', { name: 'Chọn Giáo trình Thống kê' }))
    await user.click(within(screen.getByTestId('bulk-bar')).getByRole('button', { name: /Xóa/ }))
    const confirm = screen.getByRole('dialog', { name: 'Xóa 1 tài liệu?' })
    const submit = within(confirm).getByRole('button', { name: 'Chuyển vào thùng rác' })
    await waitFor(() => expect(submit).toBeEnabled())
    expect(within(confirm).queryByTestId('delete-impact')).not.toBeInTheDocument()
    await user.click(submit)

    await waitFor(() => expect(bodies).toEqual([{ action: 'TRASH', documentIds: [shared.id] }]))
  })

  it('TC-03: xóa thư mục chứa tài liệu đang dùng cũng nêu tên không gian học và gửi kèm xác nhận', async () => {
    let confirmed: string | null = null
    server.use(
      http.delete('*/api/v1/library/folders/:id', ({ request }) => {
        confirmed = new URL(request.url).searchParams.get('confirmInUse')
        return HttpResponse.json({ batchId: 'b', folders: 1, documents: 1 })
      }),
    )
    const { user } = renderLibrary()

    const nav = await screen.findByRole('navigation', { name: 'Thư mục và lối tắt' })
    await user.click(within(nav).getByRole('button', { name: 'Thao tác với thư mục Học kỳ một' }))
    await user.click(screen.getByRole('menuitem', { name: 'Xóa thư mục' }))
    const confirm = screen.getByRole('dialog', { name: 'Xóa thư mục “Học kỳ một”?' })
    expect(await within(confirm).findByTestId('delete-impact')).toHaveTextContent('Kinh tế lượng')
    expect(usageRequests.at(-1)?.searchParams.get('folderId')).toBe(SEMESTER)
    await user.click(within(confirm).getByRole('button', { name: 'Chuyển vào thùng rác' }))

    await waitFor(() => expect(confirmed).toBe('true'))
  })

  it('máy chủ từ chối vì tài liệu vừa được gắn thêm: báo lỗi rõ ràng và giữ hộp thoại', async () => {
    let usageCalls = 0
    server.use(
      http.get('*/api/v1/workspaces/usage', () => {
        usageCalls += 1
        return HttpResponse.json(usageCalls === 1 ? [] : workspaces.slice(0, 1))
      }),
      http.post('*/api/v1/library/documents/bulk', () => apiError(409, 'LIBRARY_DOCUMENT_IN_USE')),
    )
    const { user } = renderLibrary()

    await user.click(await screen.findByRole('checkbox', { name: 'Chọn Giáo trình Thống kê' }))
    await user.click(within(screen.getByTestId('bulk-bar')).getByRole('button', { name: /Xóa/ }))
    const confirm = screen.getByRole('dialog', { name: 'Xóa 1 tài liệu?' })
    const submit = within(confirm).getByRole('button', { name: 'Chuyển vào thùng rác' })
    await waitFor(() => expect(submit).toBeEnabled())
    await user.click(submit)

    expect(
      await screen.findByText(
        'Tài liệu đang được dùng trong không gian học. Hãy xem các không gian học bị ảnh hưởng rồi xác nhận lại.',
      ),
    ).toBeInTheDocument()
    expect(await within(confirm).findByTestId('delete-impact')).toHaveTextContent('Kinh tế lượng')
  })

  it('khung thông tin tài liệu liệt kê các không gian học đang dùng nó, bấm là mở không gian học', async () => {
    const { user } = renderLibrary()

    await user.click(await screen.findByTestId('document-info-button'))
    const usage = await screen.findByTestId('document-usage')
    const links = within(usage).getAllByRole('link')
    expect(links.map((link) => link.textContent)).toEqual(['Kinh tế lượng', 'Thống kê', 'Xác suất'])
    expect(links[0]).toHaveAttribute('href', workspacePath(workspaces[0].id))
  })
})
