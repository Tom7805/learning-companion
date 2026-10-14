import { screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { notificationPath, routes } from '@/app/routes'
import { apiError } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import { NotificationOpenPage } from './NotificationOpenPage'

function renderPage(id: string) {
  return renderWithProviders(<NotificationOpenPage />, {
    path: routes.notification,
    initialEntries: [notificationPath(id)],
    extraRoutes: [
      { path: routes.devices, element: <h1>Thiết bị đang đăng nhập</h1> },
      { path: routes.contentGone, element: <h1>Nội dung không còn</h1> },
    ],
  })
}

describe('NotificationOpenPage', () => {
  it('mở bằng đường dẫn trực tiếp thì đánh dấu đã đọc rồi chuyển thẳng tới đối tượng', async () => {
    const calls: string[] = []
    server.use(
      http.post('*/api/v1/notifications/:id/open', ({ params }) => {
        calls.push(String(params.id))
        return HttpResponse.json({ available: true, path: '/devices', unreadCount: 0 })
      }),
    )
    const { router } = renderPage('thong-bao-1')

    expect(await screen.findByRole('heading', { name: 'Thiết bị đang đăng nhập' })).toBeInTheDocument()
    expect(calls).toEqual(['thong-bao-1'])
    // Thay chỗ trong lịch sử: bấm Quay lại không mở lại thông báo.
    expect(router.state.historyAction).toBe('REPLACE')
  })

  it('đối tượng không còn thì sang trang nội dung không còn', async () => {
    server.use(
      http.post('*/api/v1/notifications/:id/open', () => HttpResponse.json({ available: false, unreadCount: 0 })),
    )
    renderPage('thong-bao-2')

    expect(await screen.findByRole('heading', { name: 'Nội dung không còn' })).toBeInTheDocument()
  })

  it('TC-04: thông báo của người khác được trả lời như không tồn tại', async () => {
    server.use(http.post('*/api/v1/notifications/:id/open', () => apiError(404, 'NOT_FOUND')))
    renderPage('cua-nguoi-khac')

    expect(await screen.findByTestId('notification-not-found')).toHaveTextContent('Không tìm thấy thông báo')
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Không tìm thấy thông báo')
    expect(screen.getByRole('link', { name: 'Về trang chính' })).toHaveAttribute('href', routes.home)
  })

  it('lỗi mạng thì cho thử lại', async () => {
    let attempt = 0
    server.use(
      http.post('*/api/v1/notifications/:id/open', () => {
        attempt += 1
        return attempt === 1 ? HttpResponse.error() : HttpResponse.json({ available: true, path: '/devices', unreadCount: 0 })
      }),
    )
    const { user } = renderPage('thong-bao-3')

    await user.click(await screen.findByRole('button', { name: 'Thử lại' }))

    await waitFor(() => expect(screen.getByRole('heading', { name: 'Thiết bị đang đăng nhập' })).toBeInTheDocument())
  })
})
