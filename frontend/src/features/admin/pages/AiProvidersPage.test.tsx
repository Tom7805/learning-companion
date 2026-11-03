import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { apiError } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import type { AiGatewayOverview, AiProvider, AiRoute, AiUsage } from '../types'
import { AiProvidersPage } from './AiProvidersPage'

const BASE = '*/api/v1/admin/ai'

function provider(overrides: Partial<AiProvider>): AiProvider {
  return {
    id: 'p1',
    code: 'mock-primary',
    displayName: 'Bản mô phỏng chính',
    adapter: 'MOCK',
    capabilities: ['TEXT_GENERATION', 'EMBEDDING', 'VISION', 'OCR'],
    adapterCapabilities: ['TEXT_GENERATION', 'EMBEDDING', 'VISION', 'OCR'],
    secretRequired: false,
    secretConfigured: false,
    inputPricePerMillion: 0.5,
    outputPricePerMillion: 1.5,
    noTrainingConfirmed: true,
    enabled: true,
    primaryFor: ['TEXT_GENERATION'],
    fallbackFor: [],
    updatedAt: '2026-11-03T08:00:00Z',
    ...overrides,
  }
}

const primary = provider({})
const backup = provider({
  id: 'p2',
  code: 'mock-backup',
  displayName: 'Bản mô phỏng dự phòng',
  primaryFor: [],
  fallbackFor: ['TEXT_GENERATION'],
})
const unconfirmed = provider({
  id: 'p3',
  code: 'openai-thu',
  displayName: 'Dịch vụ thử',
  adapter: 'OPENAI_COMPATIBLE',
  capabilities: ['TEXT_GENERATION'],
  secretRequired: true,
  secretConfigured: true,
  secretName: 'LC_AI_TEST_KEY',
  noTrainingConfirmed: false,
  enabled: false,
  primaryFor: [],
})

function route(circuit: 'CLOSED' | 'OPEN' = 'CLOSED'): AiRoute {
  return {
    capability: 'TEXT_GENERATION',
    primary: {
      id: 'p1',
      code: 'mock-primary',
      displayName: 'Bản mô phỏng chính',
      enabled: true,
      circuit,
      recentFailures: circuit === 'OPEN' ? 5 : 0,
      openedAt: null,
      retryAt: null,
    },
    fallback: {
      id: 'p2',
      code: 'mock-backup',
      displayName: 'Bản mô phỏng dự phòng',
      enabled: true,
      circuit: 'CLOSED',
      recentFailures: 0,
    },
    timeoutSeconds: 30,
    maxRetries: 1,
    breakerFailureThreshold: 5,
    breakerWindowSeconds: 60,
    breakerOpenSeconds: 60,
    updatedAt: '2026-11-03T08:00:00Z',
  }
}

const usage: AiUsage = {
  since: '2026-10-27T08:00:00Z',
  totals: { calls: 3, failures: 1, inputUnits: 120, outputUnits: 40, costMicroUsd: 120 },
  rows: [
    {
      capability: 'TEXT_GENERATION',
      providerCode: 'mock-primary',
      calls: 3,
      failures: 1,
      fallbackCalls: 0,
      inputUnits: 120,
      outputUnits: 40,
      costMicroUsd: 120,
      averageLatencyMs: 210,
    },
  ],
  recentCalls: [
    {
      id: 'c1',
      occurredAt: '2026-11-03T08:00:00Z',
      capability: 'TEXT_GENERATION',
      feature: 'ASSISTANT_CHAT',
      providerCode: 'mock-backup',
      outcome: 'SUCCESS',
      usedFallback: true,
      attempts: 2,
      inputUnits: 40,
      outputUnits: 12,
      latencyMs: 180,
      costMicroUsd: 22,
    },
  ],
}

function serve(overview: Partial<AiGatewayOverview> = {}) {
  const calls: { method: string; path: string; body?: unknown }[] = []
  server.use(
    http.get(`${BASE}/gateway`, () =>
      HttpResponse.json({
        routes: [route()],
        providers: [primary, backup, unconfirmed],
        alerts: [],
        ...overview,
      }),
    ),
    http.get(`${BASE}/usage`, () => HttpResponse.json(usage)),
    http.all(`${BASE}/*`, async ({ request }) => {
      const path = new URL(request.url).pathname.replace('/api/v1/admin/ai', '')
      const body =
        request.method === 'GET'
          ? undefined
          : await request.text().then((text) => (text ? JSON.parse(text) : undefined))
      calls.push({ method: request.method, path, body })
      if (path === `/providers/${unconfirmed.id}/enable`) {
        return apiError(409, 'AI_PROVIDER_TRAINING_NOT_CONFIRMED', undefined, {
          missing: ['NO_TRAINING_CONFIRMATION'],
        })
      }
      if (path === '/routes/TEXT_GENERATION/switch') {
        return HttpResponse.json({ ...route(), primary: route().fallback, fallback: route().primary })
      }
      if (path === '/routes/TEXT_GENERATION') return HttpResponse.json(route())
      return new HttpResponse(null, { status: 204 })
    }),
  )
  return calls
}

function renderPage() {
  return renderWithProviders(<AiProvidersPage />, { path: '/admin/ai', initialEntries: ['/admin/ai'] })
}

describe('AiProvidersPage', () => {
  it('hiện định tuyến, trạng thái mạch, nhà cung cấp và mức sử dụng không kèm nội dung', async () => {
    serve({ routes: [route('OPEN')] })
    renderPage()

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Dịch vụ trí tuệ nhân tạo' }),
    ).toBeInTheDocument()
    const card = await screen.findByTestId('ai-route-TEXT_GENERATION')
    expect(within(card).getByRole('heading', { name: 'Sinh văn bản' })).toBeInTheDocument()
    const [main, fallback] = within(card).getAllByTestId('ai-route-endpoint')
    expect(main).toHaveTextContent('Bản mô phỏng chính')
    expect(main).toHaveTextContent('Đã ngắt mạch')
    expect(fallback).toHaveTextContent('Hoạt động bình thường')
    expect(card).toHaveTextContent('5 lỗi trong 60 giây, ngắt 60 giây')

    const unconfirmedRow = screen.getByTestId('ai-provider-openai-thu')
    expect(unconfirmedRow).toHaveTextContent('Chưa xác nhận không huấn luyện')
    expect(unconfirmedRow).toHaveTextContent('Kho bí mật có khóa LC_AI_TEST_KEY')

    expect(await screen.findByTestId('ai-usage-calls')).toHaveTextContent('3')
    expect(screen.getByTestId('ai-recent-calls')).toHaveTextContent('qua dự phòng')
    expect(screen.getByText(/không ghi nội dung lời gọi/)).toBeInTheDocument()
  })

  it('TC-02: cảnh báo ngắt mạch hiện ở đầu trang và xác nhận được', async () => {
    const calls = serve({
      alerts: [
        {
          id: 'a1',
          capability: 'TEXT_GENERATION',
          providerCode: 'mock-primary',
          fallbackProviderCode: 'mock-backup',
          openedAt: '2026-11-03T08:00:00Z',
        },
      ],
    })
    const { user } = renderPage()

    const alert = await screen.findByTestId('ai-alert')
    expect(alert).toHaveTextContent('mock-primary lỗi liên tiếp khi Sinh văn bản')
    expect(alert).toHaveTextContent('chuyển sang mock-backup')
    await user.click(within(alert).getByRole('button', { name: 'Đã xem' }))
    await waitFor(() =>
      expect(calls).toContainEqual({ method: 'POST', path: '/alerts/a1/acknowledge', body: undefined }),
    )
  })

  it('TC-04: bật nhà cung cấp chưa xác nhận bị từ chối và nêu điều kiện còn thiếu', async () => {
    const calls = serve()
    const { user } = renderPage()

    const row = await screen.findByTestId('ai-provider-openai-thu')
    await user.click(within(row).getByRole('switch', { name: 'Bật hoặc tắt Dịch vụ thử' }))

    const error = await within(row).findByTestId('ai-provider-error')
    expect(error).toHaveTextContent('chưa được xác nhận cam kết không dùng dữ liệu học tập để huấn luyện')
    expect(error).toHaveTextContent('Còn thiếu: xác nhận cam kết không huấn luyện.')
    expect(calls.map((call) => call.path)).toContain('/providers/p3/enable')
  })

  it('TC-06: chuyển dự phòng cần xác nhận rồi mới gửi', async () => {
    const calls = serve()
    const { user } = renderPage()

    await user.click(await screen.findByRole('button', { name: 'Chuyển Sinh văn bản sang dự phòng' }))
    const dialog = await screen.findByRole('dialog', { name: 'Chuyển Sinh văn bản sang dự phòng?' })
    expect(dialog).toHaveTextContent('Bản mô phỏng dự phòng sẽ thành nhà cung cấp chính')
    expect(calls).toHaveLength(0)
    await user.click(within(dialog).getByRole('button', { name: 'Chuyển ngay' }))

    await waitFor(() =>
      expect(calls).toContainEqual({
        method: 'POST',
        path: '/routes/TEXT_GENERATION/switch',
        body: undefined,
      }),
    )
    expect(await screen.findByText('Đã chuyển Sinh văn bản sang Bản mô phỏng dự phòng.')).toBeInTheDocument()
  })

  it('sửa định tuyến gửi thời gian chờ, thử lại và ngưỡng ngắt mạch', async () => {
    const calls = serve()
    const { user } = renderPage()

    await user.click(await screen.findByRole('button', { name: 'Sửa định tuyến Sinh văn bản' }))
    const dialog = await screen.findByRole('dialog', { name: 'Định tuyến Sinh văn bản' })
    // Nhà cung cấp chưa bật không được chọn làm chính.
    expect(within(dialog).queryByRole('option', { name: /Dịch vụ thử/ })).not.toBeInTheDocument()
    const timeout = within(dialog).getByLabelText('Thời gian chờ (giây)')
    await user.clear(timeout)
    await user.type(timeout, '20')
    await user.click(within(dialog).getByRole('button', { name: 'Lưu định tuyến' }))

    await waitFor(() =>
      expect(calls).toContainEqual({
        method: 'PUT',
        path: '/routes/TEXT_GENERATION',
        body: {
          primaryProviderId: 'p1',
          fallbackProviderId: 'p2',
          timeoutSeconds: 20,
          maxRetries: 1,
          breakerFailureThreshold: 5,
          breakerWindowSeconds: 60,
          breakerOpenSeconds: 60,
        },
      }),
    )
  })

  it('thêm nhà cung cấp chỉ gửi tên khóa trong kho bí mật', async () => {
    const calls = serve()
    const { user } = renderPage()

    await user.click(await screen.findByRole('button', { name: 'Thêm nhà cung cấp' }))
    const dialog = await screen.findByRole('dialog', { name: 'Thêm nhà cung cấp' })
    await user.type(within(dialog).getByLabelText('Tên hiển thị'), 'Dịch vụ mới')
    await user.type(within(dialog).getByLabelText('Mã'), 'dich-vu-moi')
    await user.type(within(dialog).getByLabelText('Địa chỉ dịch vụ'), 'https://ai.example.com/v1')
    await user.type(within(dialog).getByLabelText('Mô hình sinh văn bản'), 'model-a')
    await user.type(within(dialog).getByLabelText('Tên khóa trong kho bí mật'), 'lc_ai_new_key')
    await user.click(within(dialog).getByRole('checkbox', { name: /cam kết không dùng dữ liệu/ }))
    await user.click(within(dialog).getByRole('button', { name: 'Thêm nhà cung cấp' }))

    await waitFor(() =>
      expect(calls.some((call) => call.method === 'POST' && call.path === '/providers')).toBe(true),
    )
    const body = calls.find((call) => call.path === '/providers')?.body as Record<string, unknown>
    expect(body).toMatchObject({
      code: 'dich-vu-moi',
      adapter: 'OPENAI_COMPATIBLE',
      secretName: 'LC_AI_NEW_KEY',
      capabilities: ['TEXT_GENERATION'],
      noTrainingConfirmed: true,
    })
  })

  it('TC-01: gọi thử hiện câu trả lời theo luồng và nhà cung cấp đã trả lời', async () => {
    serve()
    server.use(
      http.post(`${BASE}/test-call`, () => {
        const encoder = new TextEncoder()
        const stream = new ReadableStream({
          start(controller) {
            controller.enqueue(encoder.encode('event:chunk\ndata:{"text":"Xin"}\n\n'))
            controller.enqueue(
              encoder.encode(
                'event:chunk\ndata:{"text":" chào"}\n\nevent:done\ndata:{"providerCode":"mock-primary",',
              ),
            )
            controller.enqueue(encoder.encode('"inputUnits":7,"outputUnits":2}\n\n'))
            controller.close()
          },
        })
        return new HttpResponse(stream, { headers: { 'Content-Type': 'text/event-stream' } })
      }),
    )
    const { user } = renderPage()

    await user.click(await screen.findByRole('button', { name: 'Gọi thử' }))

    await waitFor(() => expect(screen.getByTestId('ai-test-output')).toHaveTextContent('Xin chào'))
    expect(await screen.findByTestId('ai-test-done')).toHaveTextContent(
      'mock-primary đã trả lời · 7 đơn vị vào, 2 đơn vị ra.',
    )
  })

  it('TC-03: lỗi tạm thời của lời gọi thử được báo rõ', async () => {
    serve()
    server.use(
      http.post(
        `${BASE}/test-call`,
        () =>
          new HttpResponse('event:error\ndata:{"code":"AI_TEMPORARILY_UNAVAILABLE"}\n\n', {
            headers: { 'Content-Type': 'text/event-stream' },
          }),
      ),
    )
    const { user } = renderPage()

    await user.click(await screen.findByRole('button', { name: 'Gọi thử' }))

    expect(await screen.findByTestId('ai-test-error')).toHaveTextContent('đang tạm gián đoạn')
  })
})
