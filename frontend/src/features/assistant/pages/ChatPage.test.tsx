import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { chatPath, routes } from '@/app/routes'
import type { DocumentCard } from '@/features/library/types'
import { SidePanel } from '@/layouts/SidePanel'
import { apiError } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import type { Citation, Conversation } from '../types'
import { ChatPage } from './ChatPage'

const CONVERSATION = '00000000-0000-4000-8000-0000000000e1'
const ANSWER = '00000000-0000-4000-8000-0000000000e3'

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

const tree = card('doc-1', 'Bài giảng cây nhị phân')
const calculus = card('doc-2', 'Giải tích chương hai')

const event = (name: string, data: unknown) => `event:${name}\ndata:${JSON.stringify(data)}\n\n`

/** Câu hỏi mới trong phiên: mã câu hỏi, câu trả lời khác với tin nhắn đã lưu. */
const meta = {
  conversationId: CONVERSATION,
  title: 'Cách duyệt cây theo thứ tự giữa?',
  questionId: '00000000-0000-4000-8000-0000000000f2',
  answerId: '00000000-0000-4000-8000-0000000000f3',
  documentIds: ['doc-1'],
}

const citation: Citation = {
  id: '00000000-0000-4000-8000-0000000000c9',
  number: 1,
  documentId: 'doc-1',
  documentTitle: 'Bài giảng cây nhị phân',
  location: { kind: 'PAGE', number: 8, end: 8 },
  passage: 2,
  excerpt: 'Duyệt theo thứ tự giữa thăm cây con trái trước.',
  available: true,
  highlight: { start: 0, end: 22 },
}

function sse(body: string) {
  return new HttpResponse(body, { headers: { 'Content-Type': 'text/event-stream' } })
}

let asked: Record<string, unknown>[] = []

beforeEach(() => {
  asked = []
  server.use(
    http.get('*/api/v1/library/documents', () => HttpResponse.json([tree, calculus])),
    http.post('*/api/v1/assistant/ask', async ({ request }) => {
      asked.push((await request.json()) as Record<string, unknown>)
      return sse(
        event('meta', meta) +
          event('chunk', { text: 'Duyệt theo thứ tự giữa ' }) +
          event('chunk', { text: 'thăm cây con trái trước [1].' }) +
          event('done', {
            status: 'COMPLETE',
            text: 'Duyệt theo thứ tự giữa thăm cây con trái trước [1].',
            suggestions: ['Duyệt theo thứ tự trước là gì?', 'Cho ví dụ.', 'Khi nào dùng cây nhị phân?'],
            citations: [citation],
            elsewhere: [],
            flaggedSources: [],
          }),
      )
    }),
    http.get('*/api/v1/assistant/conversations/:id', ({ params }) =>
      params.id === CONVERSATION ? HttpResponse.json(saved()) : apiError(404, 'NOT_FOUND'),
    ),
  )
})

function saved(): Conversation {
  return {
    id: CONVERSATION,
    title: 'Cách duyệt cây theo thứ tự giữa?',
    createdAt: '2026-11-10T03:00:00Z',
    updatedAt: '2026-11-10T03:00:00Z',
    scope: [{ id: 'doc-1', title: 'Bài giảng cây nhị phân', type: 'PDF', status: 'READY' }],
    messages: [
      {
        id: '00000000-0000-4000-8000-0000000000e2',
        role: 'USER',
        content: 'Cách duyệt cây theo thứ tự giữa?',
        status: 'SENT',
        documentIds: ['doc-1'],
        suggestions: [],
        citations: [],
        createdAt: '2026-11-10T03:00:00Z',
      },
      {
        id: ANSWER,
        role: 'ASSISTANT',
        content: 'Duyệt theo thứ tự giữa thăm cây con trái trước [1].',
        status: 'COMPLETE',
        documentIds: [],
        suggestions: ['Duyệt theo thứ tự trước là gì?', 'Cho ví dụ.', 'Khi nào dùng cây nhị phân?'],
        citations: [citation],
        createdAt: '2026-11-10T03:00:01Z',
      },
    ],
  }
}

function renderChat(path: string = routes.chat) {
  return renderWithProviders(
    <>
      <ChatPage />
      <SidePanel />
    </>,
    { path: routes.conversation, initialEntries: [path] },
  )
}

describe('ChatPage', () => {
  it('TC-02: hội thoại mới, đính kèm hai tài liệu từ kho, câu trả lời hiện dần kèm trích dẫn và ba câu hỏi gợi ý', async () => {
    const { user, router } = renderChat()

    expect(await screen.findByTestId('chat-greeting')).toHaveTextContent('bạn muốn tìm hiểu gì?')
    expect(screen.getByTestId('source-scope-summary')).toHaveTextContent('Chưa chọn tài liệu nào')

    await user.click(screen.getByTestId('composer-attach'))
    const picker = screen.getByRole('dialog', { name: 'Đính kèm tài liệu' })
    await user.click(await within(picker).findByRole('checkbox', { name: /Bài giảng cây nhị phân/ }))
    await user.click(within(picker).getByRole('checkbox', { name: /Giải tích chương hai/ }))
    await user.click(within(picker).getByTestId('picker-apply'))
    expect(screen.getByTestId('source-scope-summary')).toHaveTextContent('Đang dùng 2/2 tài liệu')

    await user.type(screen.getByTestId('chat-input'), 'Cách duyệt cây theo thứ tự giữa?{Enter}')

    const answer = await screen.findByTestId('assistant-message')
    await waitFor(() => expect(answer).toHaveAttribute('data-status', 'COMPLETE'))
    expect(asked).toEqual([{ question: 'Cách duyệt cây theo thứ tự giữa?', documentIds: ['doc-1', 'doc-2'] }])
    expect(within(answer).getByTestId('answer-text')).toHaveTextContent('Duyệt theo thứ tự giữa thăm cây con trái trước 1.')
    expect(within(answer).getAllByTestId('suggested-question')).toHaveLength(3)
    expect(within(answer).getByTestId('answer-sources')).toHaveTextContent('Bài giảng cây nhị phân · Trang 8')
    expect(screen.getByTestId('answer-announcement')).toHaveTextContent('Trợ lý đã trả lời xong.')
    // Địa chỉ chuyển sang hội thoại vừa tạo mà không mất câu trả lời đang hiện.
    await waitFor(() => expect(router.state.location.pathname).toBe(chatPath(CONVERSATION)))
    expect(screen.getByTestId('assistant-message')).toHaveAttribute('data-status', 'COMPLETE')

    // Bấm số trích dẫn: mở khung xem bên đúng trang và đúng đoạn.
    await user.click(within(answer).getByRole('button', { name: 'Trích dẫn 1: Bài giảng cây nhị phân, Trang 8' }))
    await waitFor(() =>
      expect(new URLSearchParams(router.state.location.search).toString()).toBe('viewer=doc-1&kind=PAGE&number=8&passage=2'),
    )
  })

  it('bấm câu hỏi gợi ý là hỏi tiếp trong cùng hội thoại', async () => {
    const { user } = renderChat(chatPath(CONVERSATION))

    const answer = await screen.findByTestId('assistant-message')
    await user.click(within(answer).getAllByTestId('suggested-question')[0])

    await waitFor(() =>
      expect(asked).toEqual([
        { conversationId: CONVERSATION, question: 'Duyệt theo thứ tự trước là gì?', documentIds: ['doc-1'] },
      ]),
    )
  })

  it('TC-03 và TC-04: không tìm thấy thì nói rõ, gợi ý chọn thêm tài liệu khác có nói về câu hỏi', async () => {
    server.use(
      http.post('*/api/v1/assistant/ask', async ({ request }) => {
        asked.push((await request.json()) as Record<string, unknown>)
        return sse(
          event('meta', meta) +
            event('done', {
              status: 'NOT_FOUND',
              text: '',
              suggestions: [],
              citations: [],
              elsewhere: asked.length === 1 ? [{ id: 'doc-2', title: 'Giải tích chương hai' }] : [],
              flaggedSources: [],
            }),
        )
      }),
    )
    const { user } = renderChat(chatPath(CONVERSATION))
    await screen.findAllByTestId('assistant-message')
    await user.type(screen.getByTestId('chat-input'), 'Đạo hàm riêng là gì?{Enter}')

    const notFound = await screen.findByTestId('answer-not-found')
    expect(notFound).toHaveTextContent('Không tìm thấy thông tin này trong các tài liệu đang chọn.')
    await user.click(within(notFound).getByRole('button', { name: 'Chọn thêm “Giải tích chương hai”' }))
    expect(screen.getByTestId('source-scope-summary')).toHaveTextContent('Đang dùng 2/2 tài liệu')
  })

  it('TC-05: dịch vụ lỗi giữa chừng: giữ phần đã hiện, báo lượt được hoàn, thử lại là trả lời lại', async () => {
    let regenerated = 0
    server.use(
      http.post('*/api/v1/assistant/ask', () =>
        sse(
          event('meta', meta) +
            event('chunk', { text: 'Duyệt theo thứ tự giữa ' }) +
            event('error', { code: 'AI_TEMPORARILY_UNAVAILABLE', message: 'x', partial: 'Duyệt theo thứ tự giữa' }),
        ),
      ),
      http.post('*/api/v1/assistant/conversations/:id/regenerate', () => {
        regenerated += 1
        return sse(
          event('meta', meta) +
            event('done', { status: 'COMPLETE', text: 'Trả lời lại [1].', suggestions: [], citations: [citation], elsewhere: [], flaggedSources: [] }),
        )
      }),
    )
    const { user } = renderChat()
    await user.click(screen.getByTestId('composer-attach'))
    const picker = screen.getByRole('dialog', { name: 'Đính kèm tài liệu' })
    await user.click(await within(picker).findByRole('checkbox', { name: /Bài giảng cây nhị phân/ }))
    await user.click(within(picker).getByTestId('picker-apply'))
    await user.type(screen.getByTestId('chat-input'), 'Cách duyệt cây theo thứ tự giữa?{Enter}')

    const failed = await screen.findByTestId('answer-failed')
    expect(failed).toHaveTextContent('Dịch vụ trợ lý bị gián đoạn giữa chừng.')
    expect(failed).toHaveTextContent('Lượt của câu hỏi này đã được hoàn.')
    expect(screen.getByTestId('answer-text')).toHaveTextContent('Duyệt theo thứ tự giữa')

    await user.click(within(failed).getByTestId('answer-retry'))
    await waitFor(() => expect(regenerated).toBe(1))
    await waitFor(() => expect(screen.getByTestId('assistant-message')).toHaveAttribute('data-status', 'COMPLETE'))
    expect(screen.getByTestId('answer-text')).toHaveTextContent('Trả lời lại 1.')
  })

  it('chưa chọn tài liệu nào thì trợ lý nhắc chọn nguồn và có nút chọn tài liệu', async () => {
    server.use(
      http.post('*/api/v1/assistant/ask', () =>
        sse(event('meta', meta) + event('done', { status: 'NO_SOURCES', text: '', suggestions: [], citations: [], elsewhere: [], flaggedSources: [] })),
      ),
    )
    const { user } = renderChat()
    await user.type(await screen.findByTestId('chat-input'), 'Cây nhị phân là gì?{Enter}')

    const notice = await screen.findByTestId('answer-no-sources')
    await user.click(within(notice).getByRole('button', { name: 'Chọn tài liệu' }))
    expect(screen.getByRole('dialog', { name: 'Đính kèm tài liệu' })).toBeInTheDocument()
  })

  it('TC-06: hội thoại của người khác hay không tồn tại thì hiện không tìm thấy', async () => {
    renderChat(chatPath('00000000-0000-4000-8000-00000000dead'))
    expect(await screen.findByRole('heading', { level: 1, name: 'Không tìm thấy hội thoại' })).toBeInTheDocument()
  })
})
