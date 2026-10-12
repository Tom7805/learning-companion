import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { routes } from '@/app/routes'
import { apiError, privacyStatus, verifiedAccount } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import { PrivacyPage } from './PrivacyPage'

let updates: { purpose: string; granted: boolean }[] = []

function renderPage(status = privacyStatus) {
  server.use(
    http.get('*/api/v1/auth/session', () => HttpResponse.json({ authenticated: true, account: verifiedAccount })),
    http.get('*/api/v1/accounts/:id/privacy', () => HttpResponse.json(status)),
  )
  return renderWithProviders(<PrivacyPage />, { path: routes.privacySettings })
}

describe('PrivacyPage', () => {
  beforeEach(() => {
    updates = []
    server.use(
      http.put('*/api/v1/accounts/:id/consents/:purpose', async ({ params, request }) => {
        const { granted } = (await request.json()) as { granted: boolean }
        updates.push({ purpose: params.purpose as string, granted })
        return HttpResponse.json({
          ...privacyStatus,
          purposes: privacyStatus.purposes.map((item) =>
            item.purpose === params.purpose
              ? { ...item, granted, documentVersion: '2026-10-01', updatedAt: '2026-10-12T03:20:00Z' }
              : item,
          ),
        })
      }),
    )
  })

  it('mục đích không bắt buộc đều tắt sẵn, văn bản đang áp dụng có điểm chính dễ hiểu', async () => {
    renderPage()

    const analytics = await screen.findByRole('switch', { name: 'Thống kê sử dụng ẩn danh' })
    expect(analytics).toHaveAttribute('aria-checked', 'false')
    expect(screen.getByRole('switch', { name: 'Thư giới thiệu tính năng mới' })).toHaveAttribute(
      'aria-checked',
      'false',
    )
    expect(screen.getByTestId('required-terms')).toHaveTextContent('Bạn đã đồng ý phiên bản 2026-10-01')
    expect(screen.getByTestId('summary-privacy')).toHaveTextContent('Dữ liệu học tập thuộc về bạn.')
  })

  it('TC-01: bật thống kê ẩn danh thì lưu ngay và hiện thời điểm kèm phiên bản chính sách', async () => {
    const { user } = renderPage()

    const analytics = await screen.findByRole('switch', { name: 'Thống kê sử dụng ẩn danh' })
    await user.click(analytics)

    await waitFor(() => expect(analytics).toHaveAttribute('aria-checked', 'true'))
    expect(updates).toEqual([{ purpose: 'ANONYMOUS_USAGE_ANALYTICS', granted: true }])
    expect(within(screen.getByTestId('purpose-ANONYMOUS_USAGE_ANALYTICS')).getByTestId('purpose-meta')).toHaveTextContent(
      /Đang bật từ .* · chính sách 2026-10-01/,
    )
    expect(screen.getByRole('status')).toHaveTextContent('Đã bật Thống kê sử dụng ẩn danh')
  })

  it('TC-02: tắt thư giới thiệu đang bật thì lưu ngay', async () => {
    const { user } = renderPage({
      ...privacyStatus,
      purposes: [
        privacyStatus.purposes[0],
        { purpose: 'PRODUCT_NEWS_EMAIL', granted: true, documentVersion: '2026-10-01', updatedAt: '2026-10-11T03:00:00Z' },
      ],
    })

    const news = await screen.findByRole('switch', { name: 'Thư giới thiệu tính năng mới' })
    expect(news).toHaveAttribute('aria-checked', 'true')
    await user.click(news)

    await waitFor(() => expect(news).toHaveAttribute('aria-checked', 'false'))
    expect(updates).toEqual([{ purpose: 'PRODUCT_NEWS_EMAIL', granted: false }])
  })

  it('lỗi khi lưu thì giữ nguyên trạng thái và báo dưới mục đó', async () => {
    server.use(http.put('*/api/v1/accounts/:id/consents/:purpose', () => apiError(500, 'INTERNAL_ERROR')))
    const { user } = renderPage()

    const analytics = await screen.findByRole('switch', { name: 'Thống kê sử dụng ẩn danh' })
    await user.click(analytics)

    expect(await screen.findByText(/Hệ thống đang gặp sự cố/)).toBeInTheDocument()
    expect(analytics).toHaveAttribute('aria-checked', 'false')
  })

  it('lịch sử đồng ý liệt kê từng lần kèm phiên bản', async () => {
    renderPage()

    const entries = await screen.findAllByTestId('history-entry')
    expect(entries).toHaveLength(2)
    expect(entries[0]).toHaveTextContent('Đồng ý chính sách quyền riêng tư')
    expect(entries[0]).toHaveTextContent('phiên bản 2026-10-01')
  })
})
