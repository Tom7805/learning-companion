import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { apiError } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import type { AiConfigOverview, AiConfigVersion } from '../types'
import { AiConfigPage } from './AiConfigPage'

const BASE = '*/api/v1/admin/ai/configs'
const admin = { id: 'u1', displayName: 'Lan Anh' }

function version(number: number, overrides: Partial<AiConfigVersion> = {}): AiConfigVersion {
  return {
    id: `v${number}`,
    feature: 'ASSISTANT_CHAT',
    versionNumber: number,
    status: 'SUPERSEDED',
    providerCode: null,
    model: null,
    temperature: 0.3,
    maxOutputTokens: 1024,
    systemPrompt: `Bạn là trợ lý học tập, phiên bản ${number}.`,
    basedOnVersion: number > 1 ? number - 1 : null,
    createdAt: '2026-11-03T08:00:00Z',
    createdBy: number > 1 ? admin : null,
    lastTrialAt: '2026-11-03T08:05:00Z',
    publishedAt: `2026-11-03T0${number}:10:00Z`,
    publishedBy: number > 1 ? admin : null,
    ...overrides,
  }
}

const history: AiConfigVersion[] = [
  version(5, { status: 'DRAFT', lastTrialAt: null, publishedAt: null, publishedBy: null }),
  version(4, { status: 'PUBLISHED' }),
  version(3),
  version(2),
  version(1),
]

const overview: AiConfigOverview = {
  features: [
    { feature: 'ASSISTANT_CHAT', capability: 'TEXT_GENERATION', published: history[1], latestVersion: 5, draftCount: 1 },
    {
      feature: 'ADMIN_TEST',
      capability: 'TEXT_GENERATION',
      published: version(1, { feature: 'ADMIN_TEST', status: 'PUBLISHED' }),
      latestVersion: 1,
      draftCount: 0,
    },
  ],
  sampleSets: [
    {
      id: 'physics',
      title: 'Vật lý 11: Dòng điện không đổi',
      suggestedQuestion: 'Định luật Ohm cho đoạn mạch nói gì?',
      sources: [{ title: 'Định luật Ohm', excerpt: 'I = U / R.' }],
    },
  ],
}

function serve(save?: () => Response) {
  const calls: { method: string; path: string; body?: unknown }[] = []
  server.use(
    http.get('*/api/v1/admin/ai/gateway', () => HttpResponse.json({ routes: [], providers: [], alerts: [] })),
    http.get(BASE, () => HttpResponse.json(overview)),
    http.get(`${BASE}/ASSISTANT_CHAT/versions`, () => HttpResponse.json(history)),
    http.get(`${BASE}/ASSISTANT_CHAT/diff`, ({ request }) => {
      const params = new URL(request.url).searchParams
      return HttpResponse.json({
        fromVersion: Number(params.get('from')),
        toVersion: Number(params.get('to')),
        fields: [{ field: 'temperature', before: '0.3', after: '0.7' }],
        promptLines: [
          { change: 'SAME', text: 'Bạn là trợ lý học tập.' },
          { change: 'REMOVED', text: 'Trả lời ngắn.' },
          { change: 'ADDED', text: 'Trả lời đầy đủ, có ví dụ.' },
        ],
      })
    }),
    http.post(`${BASE}/*`, async ({ request }) => {
      const path = new URL(request.url).pathname.replace('/api/v1/admin/ai/configs', '')
      const text = await request.text()
      calls.push({ method: request.method, path, body: text ? JSON.parse(text) : undefined })
      if (path === '/ASSISTANT_CHAT/versions') {
        return save ? save() : HttpResponse.json(version(6, { status: 'DRAFT', lastTrialAt: null }), { status: 201 })
      }
      if (path === '/ASSISTANT_CHAT/rollback') return HttpResponse.json(version(3, { status: 'PUBLISHED' }))
      if (path.endsWith('/publish')) return HttpResponse.json(version(3, { status: 'PUBLISHED' }))
      return new HttpResponse(null, { status: 204 })
    }),
  )
  return calls
}

function renderPage() {
  return renderWithProviders(<AiConfigPage />, { path: '/admin/ai-config', initialEntries: ['/admin/ai-config'] })
}

describe('AiConfigPage', () => {
  it('hiện từng chức năng, phiên bản đang phát hành, người phát hành và lịch sử', async () => {
    serve()
    renderPage()

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Cấu hình mô hình và lời nhắc' }),
    ).toBeInTheDocument()
    const feature = await screen.findByTestId('ai-config-feature-ASSISTANT_CHAT')
    expect(feature).toHaveTextContent('Đang chạy phiên bản 4')
    expect(feature).toHaveTextContent('1 bản nháp')
    expect(feature).toHaveAttribute('aria-current', 'true')
    expect(screen.getByTestId('ai-config-live-version')).toHaveTextContent('Phiên bản 4')
    expect(screen.getByTestId('ai-config-live-published')).toHaveTextContent('Lan Anh')

    const five = await screen.findByTestId('ai-config-version-5')
    expect(within(five).getByTestId('ai-config-status')).toHaveTextContent('Nháp')
    // Bản nháp chưa thử thì chưa có nút phát hành.
    expect(within(five).queryByRole('button', { name: /Phát hành/ })).not.toBeInTheDocument()
    const one = screen.getByTestId('ai-config-version-1')
    expect(one).toHaveTextContent('Lưu bởi Hệ thống')
    expect(within(one).getByRole('button', { name: 'Phát hành lại phiên bản 1' })).toBeInTheDocument()
  })

  it('TC-03: lời nhắc có địa chỉ thư bị chặn lưu và đoạn đó được tô đánh dấu', async () => {
    const prompt = 'Bạn là trợ lý. Liên hệ thay.minh@truong.edu.vn khi cần.'
    const start = prompt.indexOf('thay.minh')
    serve(() =>
      apiError(400, 'AI_CONFIG_PROMPT_HAS_PII', 'systemPrompt', {
        findings: [{ kind: 'EMAIL', start, end: start + 'thay.minh@truong.edu.vn'.length }],
      }),
    )
    const { user } = renderPage()

    const editor = await screen.findByTestId('ai-config-editor')
    const textarea = within(editor).getByLabelText('Lời nhắc hệ thống')
    await user.clear(textarea)
    await user.type(textarea, prompt)
    await user.click(within(editor).getByRole('button', { name: 'Lưu thành bản nháp' }))

    const marks = await within(editor).findAllByTestId('ai-config-pii-mark')
    expect(marks).toHaveLength(1)
    expect(marks[0]).toHaveTextContent('thay.minh@truong.edu.vn')
    expect(within(editor).getByTestId('ai-config-pii')).toHaveTextContent('địa chỉ thư điện tử')
    expect(textarea).toHaveAttribute('aria-invalid', 'true')
  })

  it('lưu tạo phiên bản nháp mới dựa trên phiên bản đang soạn', async () => {
    const calls = serve()
    const { user } = renderPage()

    const editor = await screen.findByTestId('ai-config-editor')
    expect(editor).toHaveTextContent('Đang dựa trên phiên bản 5')
    const temperature = within(editor).getByLabelText('Nhiệt độ')
    await user.clear(temperature)
    await user.type(temperature, '0.7')
    await user.click(within(editor).getByRole('button', { name: 'Lưu thành bản nháp' }))

    await waitFor(() => expect(calls).toHaveLength(1))
    expect(calls[0]).toMatchObject({
      path: '/ASSISTANT_CHAT/versions',
      body: { temperature: 0.7, maxOutputTokens: 1024, basedOnVersion: 5, providerCode: null, model: null },
    })
    expect(await screen.findByText('Đã lưu phiên bản nháp 6.')).toBeInTheDocument()
  })

  it('TC-02: quay lại phiên bản trước cần xác nhận rồi gọi máy chủ một lần', async () => {
    const calls = serve()
    const { user } = renderPage()

    await user.click(await screen.findByRole('button', { name: 'Quay lại phiên bản 3' }))
    const dialog = await screen.findByRole('dialog', { name: 'Quay lại phiên bản trước?' })
    expect(dialog).toHaveTextContent('Phiên bản 4 sẽ bị thu hồi và phiên bản 3 được phát hành lại ngay')
    await user.click(within(dialog).getByRole('button', { name: 'Quay lại' }))

    await waitFor(() => expect(calls.map((call) => call.path)).toEqual(['/ASSISTANT_CHAT/rollback']))
    expect(await screen.findByText('Đã quay lại: Trợ lý hỏi đáp đang chạy phiên bản 3.')).toBeInTheDocument()
  })

  it('phát hành lại một phiên bản cũ từ lịch sử', async () => {
    const calls = serve()
    const { user } = renderPage()

    const three = await screen.findByTestId('ai-config-version-3')
    await user.click(within(three).getByRole('button', { name: 'Phát hành lại phiên bản 3' }))
    const dialog = await screen.findByRole('dialog', { name: 'Phát hành phiên bản 3?' })
    await user.click(within(dialog).getByRole('button', { name: 'Phát hành' }))

    await waitFor(() => expect(calls.map((call) => call.path)).toEqual(['/ASSISTANT_CHAT/versions/3/publish']))
  })

  it('xem khác biệt giữa bản đang chạy và bản mới nhất', async () => {
    serve()
    renderPage()

    const diff = await screen.findByTestId('ai-config-diff')
    expect(within(diff).getByLabelText('Phiên bản cũ')).toHaveValue('4')
    expect(within(diff).getByLabelText('Phiên bản mới')).toHaveValue('5')
    expect(await within(diff).findByTestId('ai-config-diff-fields')).toHaveTextContent('Nhiệt độ:0.3')
    const lines = within(diff).getByTestId('ai-config-diff-lines')
    expect(lines.querySelector('[data-change="REMOVED"]')).toHaveTextContent('Bỏ:Trả lời ngắn.')
    expect(lines.querySelector('[data-change="ADDED"]')).toHaveTextContent('Thêm:Trả lời đầy đủ, có ví dụ.')
  })
})
