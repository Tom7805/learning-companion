import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiError } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import type { TopicNode, TopicTree, Workspace } from '../types'
import { TopicOutline } from './TopicOutline'

const WORKSPACE = { id: 'ws-1', name: 'Sinh học tế bào' } as Workspace
const BASE = '*/api/v1/workspaces/ws-1/topics'

const topic = (id: string, title: string, extra: Partial<TopicNode> = {}): TopicNode => ({
  id,
  parentId: null,
  title,
  goal: '',
  aiGenerated: false,
  edited: false,
  sourceCount: 0,
  sources: [],
  children: [],
  ...extra,
})

const suggested: TopicTree = {
  readySources: 3,
  added: 2,
  topics: [
    topic('t1', 'Màng tế bào', {
      aiGenerated: true,
      goal: 'Mô tả được cấu trúc màng tế bào',
      sourceCount: 1,
      sources: [
        {
          documentId: 'doc-1',
          documentTitle: 'Bài 1 Màng tế bào',
          location: { kind: 'PAGE', number: 4, end: 4 },
          passage: 2,
          excerpt: 'Màng tế bào bao quanh tế bào chất.',
          available: true,
        },
      ],
      children: [topic('t2', 'Kênh ion', { parentId: 't1', aiGenerated: true, edited: true, goal: 'Nêu được cơ chế' })],
    }),
  ],
}

let tree: TopicTree

beforeEach(() => {
  tree = { readySources: 3, topics: [] }
  server.use(http.get(BASE, () => HttpResponse.json(tree)))
})

function render() {
  const onOpenSource = vi.fn()
  const utils = renderWithProviders(<TopicOutline workspace={WORKSPACE} onOpenSource={onOpenSource} />)
  return { ...utils, onOpenSource }
}

describe('TopicOutline', () => {
  it('TC-01: đề xuất dàn ý hiện cây hai cấp có mục tiêu học, nhãn do AI sinh và nguồn mở được đúng đoạn', async () => {
    server.use(http.post(`${BASE}/suggest`, () => HttpResponse.json(suggested)))
    const { user, onOpenSource } = render()

    expect(await screen.findByText('Chưa có dàn ý')).toBeInTheDocument()
    await user.click(screen.getByTestId('topic-suggest'))

    const items = await screen.findAllByTestId('topic')
    expect(items).toHaveLength(2)
    expect(items[0]).toHaveAttribute('data-depth', '1')
    expect(items[1]).toHaveAttribute('data-depth', '2')
    expect(within(items[0]).getAllByTestId('topic-goal')[0]).toHaveTextContent('Mô tả được cấu trúc màng tế bào')
    expect(within(items[0]).getAllByTestId('ai-label')[0]).toHaveTextContent('Do AI sinh')
    expect(within(items[1]).getByTestId('ai-label')).toHaveTextContent('Đã chỉnh sửa')
    expect(await screen.findByText('Đã thêm 2 chủ đề mới vào dàn ý.')).toBeInTheDocument()

    await user.click(within(items[0]).getAllByTestId('topic-source-count')[0])
    await user.click(screen.getByTestId('topic-source'))
    expect(onOpenSource).toHaveBeenCalledWith('doc-1', { kind: 'PAGE', number: 4, passage: 2 })
  })

  it('chưa có tài liệu sẵn sàng thì không đề xuất được và nói rõ lý do', async () => {
    tree = { readySources: 0, topics: [] }
    render()

    expect(await screen.findByTestId('topic-suggest')).toBeDisabled()
    expect(screen.getByText(/Gắn ít nhất một tài liệu đã xử lý xong/)).toBeInTheDocument()
  })

  it('TC-02: tự thêm chủ đề qua hộp thoại, kiểm tra tên bắt buộc rồi gửi tên và mục tiêu học', async () => {
    let body: unknown
    server.use(
      http.post(BASE, async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({ readySources: 3, topics: [topic('m1', 'Chủ đề 1', { goal: 'Mục tiêu 1' })] })
      }),
    )
    const { user } = render()

    await user.click(await screen.findByTestId('topic-add'))
    await user.click(screen.getByTestId('topic-save'))
    expect(await screen.findByText('Nhập tên chủ đề.')).toBeInTheDocument()
    await user.type(screen.getByTestId('topic-title-input'), 'Chủ đề 1')
    await user.type(screen.getByTestId('topic-goal-input'), 'Mục tiêu 1')
    await user.click(screen.getByTestId('topic-save'))

    expect(await screen.findByTestId('topic-title')).toHaveTextContent('Chủ đề 1')
    expect(body).toEqual({ title: 'Chủ đề 1', goal: 'Mục tiêu 1', parentId: null })
    expect(screen.queryByTestId('ai-label')).not.toBeInTheDocument()
  })

  it('TC-04: thụt chủ đề xuống cấp thứ tư bị máy chủ từ chối thì báo tối đa ba cấp và cây giữ nguyên', async () => {
    tree = {
      readySources: 1,
      topics: [
        topic('a', 'Cấp 1', {
          children: [topic('b', 'Cấp 2', { parentId: 'a', children: [topic('c', 'Cấp 3', { parentId: 'b' })] })],
        }),
        topic('d', 'Rời'),
      ],
    }
    server.use(http.put(`${BASE}/:topicId/position`, () => apiError(422, 'TOPIC_DEPTH_EXCEEDED')))
    const { user } = render()

    await screen.findAllByTestId('topic')
    await user.click(screen.getByRole('button', { name: 'Thao tác với “Rời”' }))
    await user.click(screen.getByRole('menuitem', { name: 'Thụt vào thành chủ đề con' }))

    expect(await screen.findByText(/Dàn ý chủ đề có tối đa ba cấp/)).toBeInTheDocument()
    await waitFor(() => expect(screen.getAllByTestId('topic').map((item) => item.dataset.depth)).toEqual(['1', '2', '3', '1']))
  })

  it('chủ đề ở cấp ba không có thao tác thêm chủ đề con', async () => {
    tree = {
      readySources: 1,
      topics: [topic('a', 'A', { children: [topic('b', 'B', { parentId: 'a', children: [topic('c', 'C', { parentId: 'b' })] })] })],
    }
    const { user } = render()

    await screen.findAllByTestId('topic')
    await user.click(screen.getByRole('button', { name: 'Thao tác với “C”' }))
    expect(screen.queryByRole('menuitem', { name: 'Thêm chủ đề con' })).not.toBeInTheDocument()
    expect(screen.getByRole('menuitem', { name: 'Đưa ra ngoài một cấp' })).toBeInTheDocument()
  })
})
