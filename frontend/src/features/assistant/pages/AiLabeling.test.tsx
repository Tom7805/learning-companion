import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { routes } from '@/app/routes'
import { apiError } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import { fileNameOf } from '../api/assistantApi'
import type { Conversation } from '../types'
import { ChatPage } from './ChatPage'

const saveBlob = vi.hoisted(() => vi.fn())
vi.mock('@/shared/lib/download', () => ({ saveBlob, downloadFromUrl: vi.fn() }))

const CONVERSATION = '00000000-0000-4000-8000-0000000000e1'
const ANSWER = '00000000-0000-4000-8000-0000000000e3'
const FILE = '---\nai_generated: true\n---\n> Nội dung do trí tuệ nhân tạo sinh ra.\n'

const event = (name: string, data: unknown) => `event:${name}\ndata:${JSON.stringify(data)}\n\n`

function saved(): Conversation {
  return {
    id: CONVERSATION,
    title: 'Cách duyệt cây theo thứ tự giữa?',
    createdAt: '2026-11-10T03:00:00Z',
    updatedAt: '2026-11-10T03:00:00Z',
    scope: [],
    messages: [
      {
        id: '00000000-0000-4000-8000-0000000000e2',
        role: 'USER',
        content: 'Cách duyệt cây theo thứ tự giữa?',
        status: 'SENT',
        documentIds: [],
        suggestions: [],
        citations: [],
        aiGenerated: false,
        createdAt: '2026-11-10T03:00:00Z',
      },
      {
        id: ANSWER,
        role: 'ASSISTANT',
        content: 'Duyệt theo thứ tự giữa thăm cây con trái trước.',
        status: 'COMPLETE',
        documentIds: [],
        suggestions: [],
        citations: [],
        aiGenerated: true,
        createdAt: '2026-11-10T03:00:01Z',
      },
      {
        id: '00000000-0000-4000-8000-0000000000e4',
        role: 'ASSISTANT',
        content: '',
        status: 'NO_SOURCES',
        documentIds: [],
        suggestions: [],
        citations: [],
        aiGenerated: false,
        createdAt: '2026-11-10T03:00:02Z',
      },
    ],
  }
}

let acknowledged: number

beforeEach(() => {
  saveBlob.mockReset()
  acknowledged = 0
  server.use(
    http.get('*/api/v1/library/documents', () => HttpResponse.json([])),
    http.get('*/api/v1/assistant/conversations/:id', () => HttpResponse.json(saved())),
    http.get(`*/api/v1/assistant/conversations/${CONVERSATION}/messages/:message/export`, ({ params }) =>
      params.message === ANSWER
        ? new HttpResponse(FILE, {
            headers: {
              'Content-Type': 'text/markdown;charset=UTF-8',
              'Content-Disposition': `attachment; filename="answer.md"; filename*=UTF-8''${encodeURIComponent('Duyệt cây.md')}`,
              'X-AI-Generated': 'true',
            },
          })
        : apiError(404, 'NOT_FOUND'),
    ),
  )
})

function renderChat(path: string) {
  return renderWithProviders(<ChatPage />, { path: routes.conversation, initialEntries: [path] })
}

describe('Gắn nhãn nội dung do trí tuệ nhân tạo sinh ra', () => {
  it('TC-01: lần đầu mở trợ lý, thông báo đang trò chuyện với hệ thống trí tuệ nhân tạo hiện trước câu hỏi đầu tiên', async () => {
    server.use(
      http.get('*/api/v1/assistant/disclosure', () => HttpResponse.json({ version: 1, acknowledged: false })),
      http.post('*/api/v1/assistant/disclosure', () => {
        acknowledged += 1
        return HttpResponse.json({ version: 1, acknowledged: true, acknowledgedAt: '2026-11-11T03:00:00Z' })
      }),
    )
    const { user } = renderChat(routes.chat)

    const dialog = await screen.findByRole('dialog', { name: 'Bạn đang trò chuyện với hệ thống trí tuệ nhân tạo' })
    expect(within(dialog).getByTestId('ai-disclosure')).toHaveTextContent('có thể sai hoặc thiếu')
    expect(within(dialog).getByTestId('ai-disclosure')).toHaveTextContent('Do AI sinh')
    expect(screen.queryByTestId('assistant-message')).not.toBeInTheDocument()

    await user.click(within(dialog).getByTestId('ai-disclosure-accept'))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(acknowledged).toBe(1)
  })

  it('đã đọc thông báo thì không hiện lại', async () => {
    renderChat(routes.chat)

    expect(await screen.findByTestId('chat-greeting')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('TC-03: mở lại hội thoại cũ, câu trả lời cũ vẫn có nhãn do AI sinh; câu hỏi và lời nhắc của hệ thống thì không', async () => {
    renderChat(`/chat/${CONVERSATION}`)

    const answers = await screen.findAllByTestId('assistant-message')
    expect(within(answers[0]).getByTestId('ai-label')).toHaveTextContent('Do AI sinh')
    expect(within(answers[1]).queryByTestId('ai-label')).not.toBeInTheDocument()
    expect(within(screen.getByTestId('user-message')).queryByTestId('ai-label')).not.toBeInTheDocument()
  })

  it('câu trả lời vừa hiện trong phiên cũng có nhãn ngay khi đang hiện', async () => {
    server.use(
      http.post('*/api/v1/assistant/ask', () =>
        new HttpResponse(
          event('meta', {
            conversationId: CONVERSATION,
            title: 'Hỏi',
            questionId: 'q-1',
            answerId: 'a-1',
            documentIds: [],
          }) +
            event('chunk', { text: 'Câu trả lời mới.' }) +
            event('done', { status: 'COMPLETE', text: 'Câu trả lời mới.', suggestions: [], citations: [], elsewhere: [], flaggedSources: [] }),
          { headers: { 'Content-Type': 'text/event-stream' } },
        ),
      ),
    )
    const { user } = renderChat(routes.chat)

    await user.type(await screen.findByTestId('chat-input'), 'Hỏi')
    await user.click(screen.getByTestId('chat-send'))
    const answer = await screen.findByTestId('assistant-message')
    await waitFor(() => expect(answer).toHaveAttribute('data-status', 'COMPLETE'))
    expect(within(answer).getByTestId('ai-label')).toBeInTheDocument()
  })

  it('TC-02: tải câu trả lời về dạng Markdown, tệp có dấu nhận biết và giữ tên theo hội thoại', async () => {
    const { user } = renderChat(`/chat/${CONVERSATION}`)

    const [answer] = await screen.findAllByTestId('assistant-message')
    await user.click(within(answer).getByTestId('answer-download'))
    await waitFor(() => expect(saveBlob).toHaveBeenCalledTimes(1))
    const [blob, name] = saveBlob.mock.calls[0] as [Blob, string]
    expect(name).toBe('Duyệt cây.md')
    expect(await blob.text()).toContain('ai_generated: true')
  })

  it('TC-04: bộ đính dấu nhận biết lỗi thì chặn tải và báo lỗi tạm thời', async () => {
    server.use(
      http.get(`*/api/v1/assistant/conversations/${CONVERSATION}/messages/:message/export`, () =>
        apiError(503, 'AI_LABEL_UNAVAILABLE'),
      ),
    )
    const { user } = renderChat(`/chat/${CONVERSATION}`)

    const [answer] = await screen.findAllByTestId('assistant-message')
    await user.click(within(answer).getByTestId('answer-download'))
    expect(await screen.findByText(/Chưa gắn được dấu nhận biết/)).toBeInTheDocument()
    expect(saveBlob).not.toHaveBeenCalled()
    expect(within(answer).getByTestId('answer-download')).toBeEnabled()
  })
})

describe('fileNameOf', () => {
  it('ưu tiên tên mã UTF-8, rồi tên thường, rồi tên mặc định', () => {
    expect(fileNameOf(`attachment; filename="a.md"; filename*=UTF-8''${encodeURIComponent('Cây nhị phân.md')}`)).toBe(
      'Cây nhị phân.md',
    )
    expect(fileNameOf('attachment; filename="answer.md"')).toBe('answer.md')
    expect(fileNameOf('')).toBe('cau-tra-loi.md')
  })
})
