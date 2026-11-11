import { act, screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiError } from '@/test/mocks/handlers'
import { setMedia } from '@/test/media'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import type { ChatMessage, Citation } from '../types'
import { AssistantMessage } from './AssistantMessage'

const EXCERPT = 'Cây nhị phân có tối đa hai con. Duyệt theo thứ tự giữa thăm cây con trái trước. Rồi tới gốc.'

const lecture: Citation = {
  id: '00000000-0000-4000-8000-0000000000c1',
  number: 1,
  documentId: 'doc-1',
  documentTitle: 'Bài giảng cây nhị phân',
  location: { kind: 'PAGE', number: 8, end: 8 },
  passage: 2,
  excerpt: EXCERPT,
  available: true,
  highlight: { start: 32, end: 80 },
}

const slides: Citation = {
  ...lecture,
  id: '00000000-0000-4000-8000-0000000000c2',
  number: 2,
  location: { kind: 'PAGE', number: 9, end: 9 },
  passage: 1,
  highlight: null,
}

const deleted: Citation = {
  id: '00000000-0000-4000-8000-0000000000c3',
  number: 3,
  documentId: null,
  documentTitle: 'Ghi chép đã xóa',
  location: { kind: 'PARAGRAPH', number: 3, end: 5 },
  passage: 1,
  excerpt: 'Đoạn gốc của ghi chép đã bị xóa.',
  available: false,
}

const message: ChatMessage = {
  id: 'answer-1',
  role: 'ASSISTANT',
  content: 'Duyệt theo thứ tự giữa thăm cây con trái trước [1, 2]. Ghi chép cũ cũng nói vậy [3].',
  status: 'COMPLETE',
  documentIds: [],
  suggestions: [],
  citations: [lecture, slides, deleted],
  createdAt: '2026-11-10T03:00:00Z',
}

function renderAnswer() {
  const onOpen = vi.fn()
  const utils = renderWithProviders(
    <AssistantMessage
      message={message}
      last
      busy={false}
      onOpenCitation={onOpen}
      onSuggestion={vi.fn()}
      onRegenerate={vi.fn()}
    />,
  )
  return { ...utils, onOpen }
}

beforeEach(() => {
  server.use(
    http.get('*/api/v1/assistant/citations/:id', ({ params }) => {
      const found = [lecture, slides, deleted].find((citation) => citation.id === params.id)
      return found ? HttpResponse.json(found) : apiError(404, 'NOT_FOUND')
    }),
  )
})

describe('CitationMarker', () => {
  it('rê chuột vào số trích dẫn: khung xem nhanh hiện tên tài liệu, vị trí và đoạn gốc với phần liên quan được tô sáng', async () => {
    const { user } = renderAnswer()

    const markers = screen.getAllByTestId('citation-marker')
    // Một ý có hai trích dẫn [1, 2]: hai số riêng, mỗi số mở được.
    expect(markers.map((marker) => marker.textContent)).toEqual(['1', '2', '3'])
    await user.hover(markers[0])
    const popover = await screen.findByTestId('citation-popover')
    expect(within(popover).getByTestId('citation-title')).toHaveTextContent('Bài giảng cây nhị phân')
    expect(within(popover).getByTestId('citation-location')).toHaveTextContent('Trang 8')
    expect(within(popover).getByTestId('citation-highlight')).toHaveTextContent(
      'Duyệt theo thứ tự giữa thăm cây con trái trước.',
    )
    expect(markers[0]).toHaveAttribute('aria-describedby', popover.id)

    await user.unhover(markers[0])
    await waitFor(() => expect(screen.queryByTestId('citation-popover')).not.toBeInTheDocument())
  })

  it('TC-01: bấm số trích dẫn thì mở tài liệu đúng chỗ; danh sách tài liệu đã trích ở cuối gom theo tài liệu', async () => {
    const { user, onOpen } = renderAnswer()

    await user.click(screen.getAllByTestId('citation-marker')[1])
    expect(onOpen).toHaveBeenCalledWith(expect.objectContaining({ number: 2, passage: 1 }))

    const sources = screen.getAllByTestId('answer-source')
    expect(sources).toHaveLength(2)
    expect(sources[0]).toHaveTextContent('12Bài giảng cây nhị phân · Trang 8')
    expect(sources[1]).toHaveTextContent('Ghi chép đã xóa · Tài liệu không còn')
    expect(within(sources[1]).getByRole('button')).toBeDisabled()
  })

  it('TC-03: tài liệu đã bị xóa hẳn thì trích dẫn báo tài liệu không còn, không mở khung xem', async () => {
    const { user, onOpen } = renderAnswer()

    const marker = screen.getAllByTestId('citation-marker')[2]
    expect(marker).toHaveAttribute('data-available', 'false')
    expect(marker).toHaveAccessibleName('Trích dẫn 3: Tài liệu không còn, Đoạn 3–5')
    await user.click(marker)
    expect(onOpen).not.toHaveBeenCalled()
    const popover = await screen.findByTestId('citation-popover')
    expect(within(popover).getByTestId('citation-gone')).toHaveTextContent('Tài liệu này đã bị xóa')
    expect(within(popover).getByTestId('citation-excerpt')).toHaveTextContent('Đoạn gốc của ghi chép đã bị xóa.')
  })

  it('tài liệu vừa bị xóa sau khi trả lời: khi mở khung xem nhanh, máy chủ báo không còn và không mở khung xem', async () => {
    server.use(
      http.get('*/api/v1/assistant/citations/:id', () => HttpResponse.json({ ...lecture, available: false })),
    )
    const { user, onOpen } = renderAnswer()

    const marker = screen.getAllByTestId('citation-marker')[0]
    await user.hover(marker)
    await waitFor(() => expect(marker).toHaveAttribute('data-available', 'false'))
    await user.click(marker)
    expect(onOpen).not.toHaveBeenCalled()
    expect(await screen.findByTestId('citation-gone')).toBeInTheDocument()
  })

  it('TC-02: trên điện thoại chạm vào số trích dẫn mở ngăn trượt có tên tài liệu, số trang và đoạn gốc', async () => {
    act(() => setMedia({ width: 390 }))
    const { user, onOpen } = renderAnswer()

    await user.click(screen.getAllByTestId('citation-marker')[0])
    const sheet = await screen.findByRole('dialog', { name: 'Trích dẫn 1' })
    expect(within(sheet).getByTestId('citation-title')).toHaveTextContent('Bài giảng cây nhị phân')
    expect(within(sheet).getByTestId('citation-location')).toHaveTextContent('Trang 8')
    expect(within(sheet).getByTestId('citation-excerpt')).toHaveTextContent('Duyệt theo thứ tự giữa')
    expect(screen.queryByTestId('citation-popover')).not.toBeInTheDocument()

    await user.click(within(sheet).getByTestId('citation-open'))
    expect(onOpen).toHaveBeenCalledWith(expect.objectContaining({ number: 1, passage: 2 }))
  })
})
