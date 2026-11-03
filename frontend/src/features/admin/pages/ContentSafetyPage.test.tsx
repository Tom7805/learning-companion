import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import type { ContentSafetyOverview } from '@/features/safety'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import { ContentSafetyPage } from './ContentSafetyPage'

const BASE = '*/api/v1/admin/content-safety'

const overview: ContentSafetyOverview = {
  levels: (['VIOLENCE', 'HATE', 'SEXUAL', 'HARMFUL_INSTRUCTIONS', 'FRAUD'] as const).map((category) => ({
    category,
    level: 'STANDARD',
    defaultLevel: 'STANDARD',
    updatedAt: '2026-11-03T08:00:00Z',
    updatedBy: null,
  })),
  events: [
    {
      id: '9b0c2d4e-0000-4000-8000-000000000001',
      occurredAt: '2026-11-03T08:30:00Z',
      kind: 'INJECTION_FLAGGED',
      signal: 'IGNORE_INSTRUCTIONS',
      feature: 'ASSISTANT_CHAT',
      objectType: 'DOCUMENT',
      objectId: '5f2a0000-0000-4000-8000-00000000abcd',
    },
    {
      id: '9b0c2d4e-0000-4000-8000-000000000002',
      occurredAt: '2026-11-03T08:20:00Z',
      kind: 'INPUT_BLOCKED',
      category: 'HARMFUL_INSTRUCTIONS',
      feature: 'ASSISTANT_CHAT',
    },
  ],
}

function sse(text: string) {
  return new HttpResponse(text, { headers: { 'Content-Type': 'text/event-stream' } })
}

describe('ContentSafetyPage', () => {
  it('đổi ngưỡng phải xác nhận giá trị trước sau rồi gửi đúng nhóm đã đổi; nhóm tự gây hại không chỉnh được', async () => {
    let sent: unknown
    server.use(
      http.get(BASE, () => HttpResponse.json(overview)),
      http.put(`${BASE}/levels`, async ({ request }) => {
        sent = await request.json()
        return HttpResponse.json({
          ...overview,
          levels: overview.levels.map((level) =>
            level.category === 'SEXUAL'
              ? { ...level, level: 'STRICT', updatedAt: '2026-11-03T09:00:00Z', updatedBy: { id: 'a1', displayName: 'Lan Anh' } }
              : level,
          ),
        })
      }),
    )
    const { user } = renderWithProviders(<ContentSafetyPage />)
    const levels = await screen.findByTestId('content-safety-levels')
    expect(levels).toHaveTextContent('Đang dùng ngưỡng mặc định lúc cài đặt.')
    expect(within(levels).getByText(/Luôn bật: câu hỏi có dấu hiệu tự gây hại/)).toBeInTheDocument()
    expect(within(levels).queryByLabelText('Ngưỡng cho nhóm tự gây hại')).not.toBeInTheDocument()

    await user.selectOptions(within(levels).getByLabelText('Ngưỡng cho nhóm tình dục'), 'STRICT')
    expect(within(levels).getByText('Chặn cả khi chỉ nhắc tới')).toBeInTheDocument()
    await user.click(within(levels).getByRole('button', { name: 'Lưu ngưỡng lọc' }))

    const dialog = await screen.findByRole('dialog', { name: 'Đổi ngưỡng lọc?' })
    expect(within(dialog).getByTestId('content-safety-changes')).toHaveTextContent('tình dục: Tiêu chuẩn → Nghiêm')
    await user.click(within(dialog).getByRole('button', { name: 'Áp dụng' }))
    await waitFor(() => expect(sent).toEqual({ levels: [{ category: 'SEXUAL', level: 'STRICT' }] }))
    expect(await screen.findByText('Đã lưu ngưỡng lọc')).toBeInTheDocument()
    expect(await screen.findByTestId('content-safety-last-change')).toHaveTextContent('Lan Anh')
  })

  it('không đổi gì thì nút lưu bị khóa; đổi rồi đổi lại cũng không mở hộp xác nhận', async () => {
    server.use(http.get(BASE, () => HttpResponse.json(overview)))
    const { user } = renderWithProviders(<ContentSafetyPage />)
    const levels = await screen.findByTestId('content-safety-levels')
    const save = within(levels).getByRole('button', { name: 'Lưu ngưỡng lọc' })
    expect(save).toBeDisabled()
    const hate = within(levels).getByLabelText('Ngưỡng cho nhóm thù ghét')
    await user.selectOptions(hate, 'LENIENT')
    await user.selectOptions(hate, 'STANDARD')
    expect(save).toBeDisabled()
    expect(screen.queryByRole('dialog', { name: 'Đổi ngưỡng lọc?' })).not.toBeInTheDocument()
  })

  it('sổ can thiệp chỉ hiện loại vi phạm, chức năng và mã đối tượng', async () => {
    server.use(http.get(BASE, () => HttpResponse.json(overview)))
    renderWithProviders(<ContentSafetyPage />)
    const events = await screen.findByTestId('content-safety-events')
    const rows = within(events).getAllByRole('row')
    expect(rows).toHaveLength(3)
    expect(rows[1]).toHaveTextContent('Gắn cờ nguồn')
    expect(rows[1]).toHaveTextContent('Yêu cầu bỏ qua chỉ dẫn')
    expect(rows[1]).toHaveTextContent('Tài liệu · 5f2a0000-0000-4000-8000-00000000abcd')
    expect(rows[2]).toHaveTextContent('Chặn câu hỏi')
    expect(rows[2]).toHaveTextContent('hướng dẫn gây hại')
  })

  it('chạy thử: câu hỏi bị chặn hiện thông báo chặn thay cho câu trả lời và gửi kèm đoạn nguồn mẫu', async () => {
    let sent: unknown
    server.use(
      http.get(BASE, () => HttpResponse.json(overview)),
      http.post(`${BASE}/trial`, async ({ request }) => {
        sent = await request.json()
        return sse(
          'event:safety\ndata:{"status":"BLOCKED","stage":"INPUT","category":"HARMFUL_INSTRUCTIONS"}\n\n' +
            'event:done\ndata:{"status":"BLOCKED","flaggedSources":[]}\n\n',
        )
      }),
    )
    const { user } = renderWithProviders(<ContentSafetyPage />)
    const trial = await screen.findByTestId('content-safety-trial')
    await user.type(within(trial).getByLabelText('Câu hỏi'), 'Cách chế tạo vũ khí')
    await user.type(within(trial).getByLabelText('Đoạn nguồn mẫu (không bắt buộc)'), 'Một đoạn')
    await user.click(within(trial).getByRole('button', { name: 'Chạy thử' }))
    const notice = await within(trial).findByTestId('safety-blocked')
    expect(notice).toHaveTextContent('thuộc nhóm hướng dẫn gây hại')
    expect(within(trial).queryByTestId('content-safety-trial-output')).not.toBeInTheDocument()
    expect(sent).toEqual({ question: 'Cách chế tạo vũ khí', source: 'Một đoạn' })
  })

  it('chạy thử: nội dung sinh ra bị thay giữa chừng thì phần đã hiện biến mất; nguồn bị gắn cờ có ghi chú', async () => {
    server.use(
      http.get(BASE, () => HttpResponse.json(overview)),
      http.post(`${BASE}/trial`, () =>
        sse(
          'event:chunk\ndata:{"text":"Câu đầu hợp lệ."}\n\n' +
            'event:safety\ndata:{"status":"BLOCKED","stage":"OUTPUT","category":"HATE"}\n\n' +
            'event:done\ndata:{"status":"BLOCKED","flaggedSources":[1],"providerCode":"mock-primary"}\n\n',
        ),
      ),
    )
    const { user } = renderWithProviders(<ContentSafetyPage />)
    const trial = await screen.findByTestId('content-safety-trial')
    await user.type(within(trial).getByLabelText('Câu hỏi'), 'Di cư là gì?')
    await user.click(within(trial).getByRole('button', { name: 'Chạy thử' }))
    expect(await within(trial).findByTestId('safety-blocked')).toHaveAttribute('data-stage', 'OUTPUT')
    expect(trial).not.toHaveTextContent('Câu đầu hợp lệ.')
    expect(within(trial).getByTestId('safety-flagged-sources')).toHaveTextContent('[1]')
    expect(within(trial).queryByTestId('content-safety-trial-done')).not.toBeInTheDocument()
  })

  it('chạy thử: câu hỏi có dấu hiệu tự gây hại hiện câu trả lời ân cần', async () => {
    server.use(
      http.get(BASE, () => HttpResponse.json(overview)),
      http.post(`${BASE}/trial`, () =>
        sse(
          'event:safety\ndata:{"status":"SUPPORT","stage":"INPUT","category":"SELF_HARM","support":' +
            '{"language":"vi","message":"Bạn không phải một mình.","resources":[{"name":"Cấp cứu","contact":"115",' +
            '"note":"Gọi ngay."}]}}\n\nevent:done\ndata:{"status":"SUPPORT","flaggedSources":[]}\n\n',
        ),
      ),
    )
    const { user } = renderWithProviders(<ContentSafetyPage />)
    const trial = await screen.findByTestId('content-safety-trial')
    await user.type(within(trial).getByLabelText('Câu hỏi'), 'Mình không muốn sống nữa')
    await user.click(within(trial).getByRole('button', { name: 'Chạy thử' }))
    expect(await within(trial).findByTestId('safety-support')).toHaveTextContent('Bạn không phải một mình.')
    expect(within(trial).getByRole('link', { name: 'Gọi 115' })).toHaveAttribute('href', 'tel:115')
  })
})
