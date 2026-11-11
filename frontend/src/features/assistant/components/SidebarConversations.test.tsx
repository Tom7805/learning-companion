import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import { groupConversations } from '../lib/history'
import type { ConversationSummary } from '../types'
import { SidebarConversations } from './SidebarConversations'

const saveBlob = vi.hoisted(() => vi.fn())
vi.mock('@/shared/lib/download', () => ({ saveBlob, downloadFromUrl: vi.fn() }))

const LIST = '*/api/v1/assistant/conversations'
const HOUR = 60 * 60 * 1000

const at = (hoursAgo: number) => new Date(Date.now() - hoursAgo * HOUR).toISOString()
const summary = (id: string, title: string, updatedAt: string, pinned = false): ConversationSummary => ({
  id,
  title,
  updatedAt,
  pinned,
  workspaceId: null,
})

let conversations: ConversationSummary[]
let queries: string[]

beforeEach(() => {
  saveBlob.mockReset()
  queries = []
  conversations = [
    summary('c-pin', 'Ôn thi đồ thị', at(24 * 30), true),
    summary('c-today', 'Câu hỏi hôm nay', new Date().toISOString()),
    summary('c-old', 'Ôn tập học kỳ trước', at(24 * 40)),
  ]
  server.use(
    http.get(LIST, ({ request }) => {
      const q = new URL(request.url).searchParams.get('q') ?? ''
      queries.push(q)
      return HttpResponse.json(q ? conversations.filter((item) => item.title.toLowerCase().includes(q.toLowerCase())) : conversations)
    }),
  )
})

function renderSidebar() {
  return renderWithProviders(<SidebarConversations />, {
    path: '/chat/:id?',
    initialEntries: ['/chat/c-today'],
    extraRoutes: [{ path: '/workspaces/:id', element: <p>Không gian học</p> }],
  })
}

describe('groupConversations', () => {
  it('chia theo hôm nay, hôm qua, bảy ngày qua và cũ hơn theo lịch của người học; đã ghim đứng đầu', () => {
    const now = new Date(2026, 10, 11, 9, 0)
    const local = (day: number, hour: number) => new Date(2026, 10, day, hour, 0).toISOString()
    const groups = groupConversations(
      [
        summary('p', 'Ghim', local(1, 8), true),
        summary('a', 'Sáng nay', local(11, 0)),
        summary('b', 'Tối qua', local(10, 23)),
        summary('c', 'Hôm qua sớm', local(10, 0)),
        summary('d', 'Tuần này', local(4, 12)),
        summary('e', 'Lâu rồi', local(3, 23)),
      ],
      now,
    )
    expect(groups.map(({ group, items }) => [group, items.map((item) => item.id)])).toEqual([
      ['pinned', ['p']],
      ['today', ['a']],
      ['yesterday', ['b', 'c']],
      ['week', ['d']],
      ['older', ['e']],
    ])
  })
})

describe('SidebarConversations', () => {
  it('TC-01: hiện hội thoại theo nhóm, hội thoại đã ghim nằm trên cùng và hội thoại đang mở được đánh dấu', async () => {
    renderSidebar()

    const groups = await screen.findAllByTestId('conversation-group')
    expect(groups.map((group) => group.dataset.group)).toEqual(['pinned', 'today', 'older'])
    expect(within(groups[0]).getByRole('heading')).toHaveTextContent('Đã ghim')
    expect(within(groups[0]).getByRole('link')).toHaveAccessibleName('Ôn thi đồ thị (Đã ghim)')
    expect(within(groups[1]).getByRole('link', { name: 'Câu hỏi hôm nay' })).toHaveAttribute('aria-current', 'page')
    expect(within(groups[2]).getByRole('link', { name: 'Ôn tập học kỳ trước' })).toHaveAttribute('href', '/chat/c-old')
  })

  it('chưa có hội thoại nào thì mời bắt đầu hỏi', async () => {
    conversations = []
    renderSidebar()

    expect(await screen.findByText('Chưa có hội thoại nào. Hỏi trợ lý để bắt đầu.')).toBeInTheDocument()
  })

  it('TC-03: tìm theo từ khóa gửi từ khóa cho máy chủ; không có kết quả thì nói rõ; Esc đóng ô tìm', async () => {
    const { user } = renderSidebar()
    await screen.findAllByTestId('sidebar-conversation')

    await user.click(screen.getByRole('button', { name: 'Tìm hội thoại' }))
    const input = screen.getByRole('searchbox', { name: 'Tìm hội thoại' })
    expect(input).toHaveFocus()
    await user.type(input, 'ôn thi')
    await waitFor(() => expect(screen.getAllByTestId('sidebar-conversation')).toHaveLength(1))
    expect(screen.getByTestId('sidebar-conversation')).toHaveTextContent('Ôn thi đồ thị')
    expect(queries.at(-1)).toBe('ôn thi')

    await user.clear(input)
    await user.type(input, 'không có')
    expect(await screen.findByText('Không có hội thoại nào khớp “không có”.')).toBeInTheDocument()

    await user.keyboard('{Escape}')
    expect(screen.queryByRole('searchbox')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Tìm hội thoại' })).toHaveFocus()
    await waitFor(() => expect(screen.getAllByTestId('sidebar-conversation')).toHaveLength(3))
  })

  it('đổi tên và ghim qua trình đơn của hội thoại', async () => {
    const changes: unknown[] = []
    server.use(
      http.patch(`${LIST}/:id`, async ({ params, request }) => {
        const body = (await request.json()) as { title?: string; pinned?: boolean }
        changes.push({ id: params.id, ...body })
        conversations = conversations.map((item) => (item.id === params.id ? { ...item, ...body } : item))
        return HttpResponse.json(conversations.find((item) => item.id === params.id))
      }),
    )
    const { user } = renderSidebar()

    await user.click(await screen.findByRole('button', { name: 'Thao tác với “Câu hỏi hôm nay”' }))
    await user.click(screen.getByRole('menuitem', { name: 'Đổi tên' }))
    const field = screen.getByTestId('conversation-title-input')
    await user.clear(field)
    await user.click(screen.getByTestId('conversation-rename-save'))
    expect(await screen.findByText('Nhập tên hội thoại.')).toBeInTheDocument()
    await user.type(field, '  Ôn   duyệt cây ')
    await user.click(screen.getByTestId('conversation-rename-save'))
    expect(await screen.findByRole('link', { name: 'Ôn duyệt cây' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Thao tác với “Ôn duyệt cây”' }))
    await user.click(screen.getByRole('menuitem', { name: 'Ghim lên đầu' }))
    await waitFor(() =>
      expect(within(screen.getAllByTestId('conversation-group')[0]).getByRole('link', { name: 'Ôn duyệt cây (Đã ghim)' })).toBeInTheDocument(),
    )
    expect(changes).toEqual([
      { id: 'c-today', title: 'Ôn duyệt cây' },
      { id: 'c-today', pinned: true },
    ])
  })

  it('TC-04: chuyển hội thoại vào không gian học đã chọn; hội thoại đang mở thì mở trong không gian học đó', async () => {
    let moved: unknown
    server.use(
      http.get('*/api/v1/workspaces', () =>
        HttpResponse.json([
          { id: 'ws-1', name: 'Cấu trúc dữ liệu', icon: 'BOOK', color: 'SUN' },
          { id: 'ws-2', name: 'Giải tích', icon: 'BOOK', color: 'SKY' },
        ]),
      ),
      http.put(`${LIST}/:id/workspace`, async ({ params, request }) => {
        moved = { id: params.id, ...((await request.json()) as object) }
        conversations = conversations.filter((item) => item.id !== params.id)
        return HttpResponse.json({ ...summary(String(params.id), 'Câu hỏi hôm nay', new Date().toISOString()), workspaceId: 'ws-2' })
      }),
    )
    const { user, router } = renderSidebar()

    await user.click(await screen.findByRole('button', { name: 'Thao tác với “Câu hỏi hôm nay”' }))
    await user.click(screen.getByRole('menuitem', { name: 'Chuyển vào không gian học' }))
    const dialog = await screen.findByRole('dialog', { name: 'Chuyển “Câu hỏi hôm nay” vào không gian học' })
    await user.selectOptions(await within(dialog).findByTestId('conversation-move-target'), 'ws-2')
    await user.click(within(dialog).getByTestId('conversation-move-confirm'))

    expect(await screen.findByText('Đã chuyển hội thoại vào “Giải tích”.')).toBeInTheDocument()
    expect(moved).toEqual({ id: 'c-today', workspaceId: 'ws-2' })
    expect(router.state.location.pathname).toBe('/workspaces/ws-2')
    expect(router.state.location.search).toBe('?tab=assistant&conversation=c-today')
  })

  it('tải hội thoại về và xóa sau khi xác nhận; đang mở hội thoại đó thì quay về hội thoại mới', async () => {
    let deleted = ''
    server.use(
      http.get(`${LIST}/:id/export`, () =>
        new HttpResponse('---\nai_generated: true\n---\n# Câu hỏi hôm nay\n', {
          headers: {
            'Content-Type': 'text/markdown;charset=UTF-8',
            'Content-Disposition': `attachment; filename="chat.md"; filename*=UTF-8''${encodeURIComponent('Câu hỏi hôm nay.md')}`,
          },
        }),
      ),
      http.delete(`${LIST}/:id`, ({ params }) => {
        deleted = String(params.id)
        conversations = conversations.filter((item) => item.id !== params.id)
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const { user, router } = renderSidebar()

    await user.click(await screen.findByRole('button', { name: 'Thao tác với “Câu hỏi hôm nay”' }))
    await user.click(screen.getByRole('menuitem', { name: 'Tải về (.md)' }))
    await waitFor(() => expect(saveBlob).toHaveBeenCalledWith(expect.any(Blob), 'Câu hỏi hôm nay.md'))

    await user.click(screen.getByRole('button', { name: 'Thao tác với “Câu hỏi hôm nay”' }))
    await user.click(screen.getByRole('menuitem', { name: 'Xóa' }))
    await user.click(await screen.findByRole('button', { name: 'Xóa hội thoại' }))

    expect(await screen.findByText('Đã xóa hội thoại.')).toBeInTheDocument()
    expect(deleted).toBe('c-today')
    await waitFor(() => expect(screen.getAllByTestId('sidebar-conversation')).toHaveLength(2))
    expect(router.state.location.pathname).toBe('/chat')
  })
})
