import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { routes, workspacePath } from '@/app/routes'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import type { Workspace } from '../types'
import { WorkspacesPage } from './WorkspacesPage'

const workspace = (index: number, extra: Partial<Workspace> = {}): Workspace => ({
  id: `00000000-0000-4000-8000-${String(index).padStart(12, '0')}`,
  name: `Không gian ${String(index).padStart(3, '0')}`,
  icon: 'BOOK',
  color: 'SUN',
  description: '',
  documentCount: 0,
  createdAt: '2026-11-01T00:00:00Z',
  updatedAt: '2026-11-01T00:00:00Z',
  ...extra,
})

function renderPage(search = '') {
  return renderWithProviders(<WorkspacesPage />, {
    path: routes.workspaces,
    initialEntries: [`${routes.workspaces}${search}`],
    extraRoutes: [{ path: routes.workspace, element: <p>Trang không gian học</p> }],
  })
}

describe('WorkspacesPage', () => {
  it('chưa có không gian học nào thì giải thích và mời tạo', async () => {
    const { user } = renderPage()

    expect(await screen.findByText('Chưa có không gian học nào')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Tạo không gian học đầu tiên' }))
    expect(screen.getByRole('dialog', { name: 'Tạo không gian học' })).toBeInTheDocument()
  })

  it('TC-01: đã có hai trăm không gian học vẫn tạo được không gian thứ hai trăm lẻ một rồi mở trang của nó', async () => {
    const existing = Array.from({ length: 200 }, (_, index) => workspace(index + 1))
    let created: unknown = null
    server.use(
      http.get('*/api/v1/workspaces', () => HttpResponse.json(existing)),
      http.post('*/api/v1/workspaces', async ({ request }) => {
        created = await request.json()
        return HttpResponse.json(workspace(201, { name: 'Kinh tế lượng', icon: 'CHART', color: 'SKY' }), { status: 201 })
      }),
    )
    const { user, router } = renderPage()

    expect(await screen.findByTestId('workspaces-summary')).toHaveTextContent('200 không gian học')
    expect(screen.getAllByTestId('workspace-card')).toHaveLength(200)
    await user.click(screen.getByTestId('create-workspace'))
    const dialog = screen.getByRole('dialog', { name: 'Tạo không gian học' })
    await user.type(within(dialog).getByLabelText('Tên không gian học'), '  Kinh tế   lượng ')
    await user.click(within(dialog).getByRole('radio', { name: 'Biểu đồ' }))
    await user.click(within(dialog).getByRole('radio', { name: 'Xanh trời' }))
    await user.type(within(dialog).getByLabelText('Mô tả (không bắt buộc)'), 'Ôn thi cuối kỳ')
    await user.click(within(dialog).getByRole('button', { name: 'Tạo không gian học' }))

    await waitFor(() =>
      expect(created).toEqual({ name: 'Kinh tế lượng', icon: 'CHART', color: 'SKY', description: 'Ôn thi cuối kỳ' }),
    )
    expect(await screen.findByText('Đã tạo không gian học “Kinh tế lượng”.')).toBeInTheDocument()
    await waitFor(() => expect(router.state.location.pathname).toBe(workspacePath(workspace(201).id)))
  })

  it('tên bắt buộc và tối đa một trăm ký tự: báo ngay trong hộp thoại, không gửi lên máy chủ', async () => {
    let posted = false
    server.use(
      http.post('*/api/v1/workspaces', () => {
        posted = true
        return HttpResponse.json(workspace(1), { status: 201 })
      }),
    )
    const { user } = renderPage('?new=1')

    const dialog = await screen.findByRole('dialog', { name: 'Tạo không gian học' })
    const name = within(dialog).getByLabelText('Tên không gian học')
    await waitFor(() => expect(name).toHaveFocus())
    expect(dialog).toHaveTextContent('0/100 ký tự')
    await user.click(within(dialog).getByRole('button', { name: 'Tạo không gian học' }))
    expect(within(dialog).getByText('Hãy đặt tên cho không gian học.')).toBeInTheDocument()

    await user.click(name)
    await user.paste('a'.repeat(101))
    expect(within(dialog).getByText('Tên dài tối đa 100 ký tự.')).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Tạo không gian học' })).toBeDisabled()
    expect(posted).toBe(false)
  })

  it('tìm theo tên không phân biệt dấu, không khớp gì thì cho bỏ tìm', async () => {
    const list = [
      workspace(1, { name: 'Xác suất thống kê', documentCount: 1234, description: 'Chương 1 đến 4' }),
      workspace(2, { name: 'Kinh tế lượng' }),
      ...Array.from({ length: 6 }, (_, index) => workspace(index + 3)),
    ]
    server.use(http.get('*/api/v1/workspaces', () => HttpResponse.json(list)))
    const { user } = renderPage()

    const first = (await screen.findAllByTestId('workspace-card'))[0]
    expect(first).toHaveAttribute('href', workspacePath(list[0].id))
    expect(within(first).getByTestId('workspace-card-count')).toHaveTextContent('1.234 tài liệu')
    expect(within(screen.getAllByTestId('workspace-card')[1]).getByTestId('workspace-card-count')).toHaveTextContent(
      'Chưa gắn tài liệu',
    )

    await user.type(screen.getByLabelText('Tìm không gian học'), 'xac suat')
    expect(screen.getAllByTestId('workspace-card')).toHaveLength(1)
    expect(screen.getByTestId('workspaces-summary')).toHaveTextContent('1 / 8 không gian học')

    await user.clear(screen.getByLabelText('Tìm không gian học'))
    await user.type(screen.getByLabelText('Tìm không gian học'), 'hóa học')
    expect(screen.getByText('Không có không gian học nào khớp')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Bỏ tìm kiếm' }))
    expect(screen.getAllByTestId('workspace-card')).toHaveLength(8)
  })

  it('lỗi tải thì cho thử lại', async () => {
    let attempt = 0
    server.use(
      http.get('*/api/v1/workspaces', () => {
        attempt += 1
        return attempt === 1 ? HttpResponse.error() : HttpResponse.json([workspace(1)])
      }),
    )
    const { user } = renderPage()

    await user.click(await screen.findByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByTestId('workspace-card')).toBeInTheDocument()
  })
})
