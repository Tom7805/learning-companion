import { act, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { apiError, verifiedAccount } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import { ProtectedRoute } from './ProtectedRoute'
import { routes } from './routes'

function renderProtected(path: string = routes.library) {
  return renderWithProviders(null, {
    initialEntries: [path],
    routes: [
      {
        element: <ProtectedRoute />,
        children: [{ path: routes.library, element: <h1>Thư viện của Lan Anh</h1> }],
      },
      { path: routes.login, element: <h1>Đăng nhập</h1> },
    ],
  })
}

const signedIn = () =>
  http.get('*/api/v1/auth/session', () => HttpResponse.json({ authenticated: true, account: verifiedAccount }))

describe('ProtectedRoute', () => {
  it('trong lúc hỏi phiên thì hiện ngay phần khung dạng khung xương', async () => {
    server.use(signedIn())
    renderProtected()

    expect(screen.getByTestId('app-shell-skeleton')).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Đang tải…')
    expect(await screen.findByRole('heading', { name: 'Thư viện của Lan Anh' })).toBeInTheDocument()
  })

  it('TC-05: mạng chập chờn thì giữ khung xương kèm "đang kết nối lại", có mạng là vào thẳng', async () => {
    server.use(http.get('*/api/v1/auth/session', () => HttpResponse.error()))
    renderProtected()

    expect(await screen.findByTestId('reconnecting')).toHaveTextContent('Đang kết nối lại…')
    expect(screen.getByTestId('app-shell-skeleton')).toBeInTheDocument()

    server.use(signedIn())
    act(() => {
      window.dispatchEvent(new Event('online'))
    })

    expect(await screen.findByRole('heading', { name: 'Thư viện của Lan Anh' })).toBeInTheDocument()
    expect(screen.queryByTestId('reconnecting')).not.toBeInTheDocument()
  })

  it('máy chủ lỗi thì báo kèm mã tra cứu và cho thử lại', async () => {
    server.use(http.get('*/api/v1/auth/session', () => apiError(500, 'INTERNAL_ERROR')))
    const { user } = renderProtected()

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Hệ thống đang gặp sự cố')
    expect(alert).toHaveTextContent('req-test-1')

    server.use(signedIn())
    await user.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByRole('heading', { name: 'Thư viện của Lan Anh' })).toBeInTheDocument()
  })

  it('chưa đăng nhập thì sang trang đăng nhập, nhớ trang đang mở để quay lại', async () => {
    const { router } = renderProtected(`${routes.library}?q=ohm`)

    expect(await screen.findByRole('heading', { name: 'Đăng nhập' })).toBeInTheDocument()
    expect(router.state.location.state).toEqual({ from: `${routes.library}?q=ohm`, reason: undefined })
  })
})
