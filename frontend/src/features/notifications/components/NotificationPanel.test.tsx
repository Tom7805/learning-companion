import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { useEffect } from 'react'
import { Outlet } from 'react-router'
import { beforeEach, describe, expect, it } from 'vitest'
import { routes } from '@/app/routes'
import { SidePanel } from '@/layouts/SidePanel'
import { setMedia } from '@/test/media'
import { apiError, sampleNotifications } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import { useNotificationPanel } from '../hooks/useNotificationPanel'
import type { NotificationPage } from '../types'

const [exportReady, newDevice] = sampleNotifications.items

/** Như khung ứng dụng: khung xem bên ở lại khi chuyển trang; ngăn thông báo mở ngay khi dựng, như khi bấm chuông. */
function Shell() {
  const notifications = useNotificationPanel()
  const { open } = notifications
  useEffect(() => {
    open()
    // Chỉ mở một lần lúc dựng.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  return (
    <>
      <Outlet />
      <SidePanel />
    </>
  )
}

function renderShell() {
  return renderWithProviders(null, {
    routes: [
      {
        element: <Shell />,
        children: [
          { path: '/', element: <p>Trang chính</p> },
          { path: routes.dataExport, element: <p>Trang tải gói dữ liệu</p> },
          { path: routes.contentGone, element: <p>Trang nội dung không còn</p> },
        ],
      },
    ],
  })
}

let opened: string[] = []

function renderPanel(page: NotificationPage = sampleNotifications) {
  let unread = page.unreadCount
  server.use(
    http.get('*/api/v1/notifications', () => HttpResponse.json({ ...page, unreadCount: unread })),
    http.get('*/api/v1/notifications/unread-count', () => HttpResponse.json({ unreadCount: unread })),
    http.post('*/api/v1/notifications/:id/open', ({ params }) => {
      opened.push(String(params.id))
      unread = Math.max(0, unread - 1)
      return params.id === exportReady.id
        ? HttpResponse.json({ available: true, path: `/exports/${exportReady.id}`, unreadCount: unread })
        : HttpResponse.json({ available: false, unreadCount: unread })
    }),
    http.post('*/api/v1/notifications/read-all', () => {
      const marked = unread
      unread = 0
      return HttpResponse.json({ marked, unreadCount: 0 })
    }),
  )
  return renderShell()
}

const panel = () => screen.getByTestId('side-panel')
const items = () => screen.getAllByTestId('notification-item')

describe('NotificationPanel', () => {
  beforeEach(() => {
    opened = []
  })

  it('liệt kê thông báo mới nhất trước với loại, tiêu đề, mô tả và dấu chưa đọc', async () => {
    renderPanel()

    expect(panel()).toHaveAccessibleName('Thông báo')
    await waitFor(() => expect(items()).toHaveLength(2))
    expect(items()[0]).toHaveTextContent('Dữ liệu của bạn')
    expect(items()[0]).toHaveTextContent('Gói dữ liệu của bạn đã sẵn sàng')
    expect(items()[0]).toHaveTextContent('12 tệp · 47,5 KB')
    expect(items()[1]).toHaveTextContent('Bảo mật')
    expect(items()[1]).toHaveTextContent('Đăng nhập từ thiết bị mới')
    expect(items()[1]).toHaveTextContent('Chrome trên Windows · Hà Nội, VN')
    expect(items()[0]).toHaveAttribute('data-unread', 'true')
    expect(within(items()[0]).getByText('Chưa đọc')).toBeInTheDocument()
    expect(screen.getByTestId('notification-summary')).toHaveTextContent('2 thông báo chưa đọc')
    expect(screen.getByText('Thông báo được giữ trong 90 ngày.')).toBeInTheDocument()
  })

  it('TC-01: bấm thông báo thứ nhất thì mở đúng gói dữ liệu, số chưa đọc còn một, ngăn vẫn mở trên màn hình rộng', async () => {
    const { user, router } = renderPanel()
    await waitFor(() => expect(items()).toHaveLength(2))

    await user.click(items()[0])

    await waitFor(() => expect(router.state.location.pathname).toBe(`/exports/${exportReady.id}`))
    expect(opened).toEqual([exportReady.id])
    expect(screen.getByTestId('notification-summary')).toHaveTextContent('1 thông báo chưa đọc')
    expect(items()[0]).not.toHaveAttribute('data-unread')
    expect(panel()).toBeInTheDocument()
  })

  it('TC-03: đối tượng không còn thì mở trang nội dung không còn thay vì báo lỗi', async () => {
    const { user, router } = renderPanel()
    await waitFor(() => expect(items()).toHaveLength(2))

    await user.click(items()[1])

    await waitFor(() => expect(router.state.location.pathname).toBe(routes.contentGone))
    expect(opened).toEqual([newDevice.id])
  })

  it('thông báo vừa bị dọn trong lúc ngăn đang mở cũng dẫn tới trang nội dung không còn', async () => {
    const { user, router } = renderPanel()
    await waitFor(() => expect(items()).toHaveLength(2))
    server.use(http.post('*/api/v1/notifications/:id/open', () => apiError(404, 'NOT_FOUND')))

    await user.click(items()[0])

    await waitFor(() => expect(router.state.location.pathname).toBe(routes.contentGone))
  })

  it('trên điện thoại, mở một thông báo thì đóng ngăn để thấy nội dung', async () => {
    setMedia({ width: 360 })
    const { user, router } = renderPanel()
    await waitFor(() => expect(items()).toHaveLength(2))

    await user.click(items()[0])

    await waitFor(() => expect(router.state.location.pathname).toBe(`/exports/${exportReady.id}`))
    expect(screen.queryByTestId('side-panel')).not.toBeInTheDocument()
  })

  it('đánh dấu tất cả đã đọc', async () => {
    const { user } = renderPanel()
    await waitFor(() => expect(items()).toHaveLength(2))

    await user.click(screen.getByRole('button', { name: 'Đánh dấu tất cả đã đọc' }))

    await waitFor(() => expect(screen.getByTestId('notification-summary')).toHaveTextContent('Bạn đã đọc hết thông báo.'))
    expect(items().every((item) => !item.hasAttribute('data-unread'))).toBe(true)
    expect(screen.getByRole('button', { name: 'Đánh dấu tất cả đã đọc' })).toBeDisabled()
  })

  it('tải thêm thông báo cũ hơn, nối tiếp sau trang đầu', async () => {
    const older = { ...newDevice, id: 'cu-hon', createdAt: '2026-09-01T00:00:00Z' }
    const requests: (string | null)[] = []
    server.use(
      http.get('*/api/v1/notifications', ({ request }) => {
        const before = new URL(request.url).searchParams.get('before')
        requests.push(before)
        return HttpResponse.json(
          before === null
            ? { items: [exportReady, newDevice], nextCursor: newDevice.id, unreadCount: 3 }
            : { items: [older], unreadCount: 3 },
        )
      }),
    )
    const { user } = renderShell()
    await waitFor(() => expect(items()).toHaveLength(2))

    await user.click(screen.getByRole('button', { name: 'Xem thông báo cũ hơn' }))

    await waitFor(() => expect(items()).toHaveLength(3))
    expect(requests).toEqual([null, newDevice.id])
    expect(screen.queryByRole('button', { name: 'Xem thông báo cũ hơn' })).not.toBeInTheDocument()
  })

  it('chưa có thông báo thì nói rõ sẽ có gì ở đây', async () => {
    renderPanel({ items: [], unreadCount: 0 })

    expect(await screen.findByText('Chưa có thông báo nào')).toBeInTheDocument()
    expect(screen.getByTestId('notification-summary')).toHaveTextContent('Bạn đã đọc hết thông báo.')
    expect(screen.queryByRole('button', { name: 'Đánh dấu tất cả đã đọc' })).not.toBeInTheDocument()
  })

  it('lỗi tải thì cho thử lại', async () => {
    let attempt = 0
    server.use(
      http.get('*/api/v1/notifications', () => {
        attempt += 1
        return attempt === 1 ? apiError(500, 'INTERNAL_ERROR') : HttpResponse.json(sampleNotifications)
      }),
    )
    const { user } = renderShell()

    await user.click(await screen.findByRole('button', { name: 'Thử lại' }))

    await waitFor(() => expect(items()).toHaveLength(2))
  })
})
