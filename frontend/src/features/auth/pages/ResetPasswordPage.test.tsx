import { screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { useLocation } from 'react-router'
import { describe, expect, it } from 'vitest'
import { routes } from '@/app/routes'
import { queryKeys } from '@/shared/api/queryKeys'
import { apiError, verifiedAccount } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import { ResetPasswordPage } from './ResetPasswordPage'

const NEW_PASSWORD = 'bien xanh gio dong 2027'

function LoginProbe() {
  const location = useLocation()
  return <p data-testid="login-state">{JSON.stringify(location.state)}</p>
}

function renderPage(search = '?token=ma-bi-mat') {
  return renderWithProviders(<ResetPasswordPage />, {
    path: routes.resetPassword,
    initialEntries: [`${routes.resetPassword}${search}`],
    extraRoutes: [{ path: routes.login, element: <LoginProbe /> }],
  })
}

describe('ResetPasswordPage', () => {
  it('kiểm tra đường dẫn, xóa mã khỏi thanh địa chỉ rồi mới cho nhập mật khẩu mới', async () => {
    let checked: unknown
    server.use(
      http.post('*/api/v1/auth/password/reset/check', async ({ request }) => {
        checked = await request.json()
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const { router } = renderPage()

    expect(screen.getByRole('heading', { name: 'Đang kiểm tra đường dẫn…' })).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Đặt mật khẩu mới' })).toBeInTheDocument()
    expect(checked).toEqual({ token: 'ma-bi-mat' })
    expect(router.state.location.search).toBe('')
  })

  it('đặt mật khẩu mới thành công, đăng xuất phiên hiện tại và đưa sang đăng nhập với địa chỉ điền sẵn', async () => {
    let body: unknown
    server.use(
      http.post('*/api/v1/auth/password/reset', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({ email: verifiedAccount.email })
      }),
    )
    const { user, queryClient } = renderPage()
    queryClient.setQueryData(queryKeys.session, { authenticated: true, account: verifiedAccount })

    await user.type(await screen.findByLabelText('Mật khẩu mới'), NEW_PASSWORD)
    await user.type(screen.getByLabelText('Nhập lại mật khẩu mới'), NEW_PASSWORD)
    await user.click(screen.getByRole('button', { name: /Lưu mật khẩu mới/ }))

    expect(await screen.findByRole('heading', { name: 'Đã đặt mật khẩu mới' })).toBeInTheDocument()
    expect(body).toEqual({ token: 'ma-bi-mat', password: NEW_PASSWORD })
    expect(screen.getByTestId('reset-success-body')).toHaveTextContent(verifiedAccount.email)
    expect(queryClient.getQueryData(queryKeys.session)).toEqual({ authenticated: false })

    await user.click(screen.getByRole('button', { name: /Đăng nhập bằng mật khẩu mới/ }))
    expect(await screen.findByTestId('login-state')).toHaveTextContent(
      JSON.stringify({ email: verifiedAccount.email, passwordReset: true }),
    )
  })

  it('kiểm quy tắc mật khẩu và hai ô phải khớp trước khi gửi', async () => {
    let calls = 0
    server.use(
      http.post('*/api/v1/auth/password/reset', () => {
        calls += 1
        return HttpResponse.json({ email: verifiedAccount.email })
      }),
    )
    const { user } = renderPage()

    await user.type(await screen.findByLabelText('Mật khẩu mới'), 'ngan')
    await user.type(screen.getByLabelText('Nhập lại mật khẩu mới'), 'ngan')
    await user.click(screen.getByRole('button', { name: /Lưu mật khẩu mới/ }))
    expect(await screen.findByText('Mật khẩu cần dài ít nhất 10 ký tự.')).toBeInTheDocument()

    await user.clear(screen.getByLabelText('Mật khẩu mới'))
    await user.type(screen.getByLabelText('Mật khẩu mới'), NEW_PASSWORD)
    expect(await screen.findByText('Hai mật khẩu chưa khớp nhau.')).toBeInTheDocument()
    expect(calls).toBe(0)
  })

  it('mật khẩu trùng mật khẩu cũ bị máy chủ từ chối, báo ngay dưới ô và đưa tiêu điểm về đó', async () => {
    server.use(http.post('*/api/v1/auth/password/reset', () => apiError(400, 'PASSWORD_REUSED', 'password')))
    const { user } = renderPage()

    await user.type(await screen.findByLabelText('Mật khẩu mới'), NEW_PASSWORD)
    await user.type(screen.getByLabelText('Nhập lại mật khẩu mới'), NEW_PASSWORD)
    await user.click(screen.getByRole('button', { name: /Lưu mật khẩu mới/ }))

    expect(await screen.findByText('Mật khẩu mới cần khác mật khẩu đang dùng.')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByLabelText('Mật khẩu mới')).toHaveFocus())
    expect(screen.getByLabelText('Mật khẩu mới')).toHaveAttribute('aria-invalid', 'true')
  })

  it('TC-02: đường dẫn quá 30 phút báo hết hạn và gửi được đường dẫn mới bằng chính mã cũ', async () => {
    let resendBody: unknown
    server.use(
      http.post('*/api/v1/auth/password/reset/check', () => apiError(410, 'RESET_TOKEN_EXPIRED')),
      http.post('*/api/v1/auth/password/forgot', async ({ request }) => {
        resendBody = await request.json()
        return HttpResponse.json({ resendAvailableInSeconds: 60 }, { status: 202 })
      }),
    )
    const { user } = renderPage()

    expect(await screen.findByRole('heading', { name: 'Đường dẫn đã hết hạn' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /Gửi đường dẫn mới/ }))

    expect(await screen.findByTestId('reset-resent')).toHaveTextContent('Đã gửi đường dẫn mới')
    expect(resendBody).toEqual({ token: 'ma-bi-mat' })
    expect(screen.getByRole('button', { name: /Gửi lại sau/ })).toBeDisabled()
  })

  it('đường dẫn hết hạn ngay lúc lưu cũng chuyển sang màn hình hết hạn', async () => {
    server.use(http.post('*/api/v1/auth/password/reset', () => apiError(410, 'RESET_TOKEN_EXPIRED')))
    const { user } = renderPage()

    await user.type(await screen.findByLabelText('Mật khẩu mới'), NEW_PASSWORD)
    await user.type(screen.getByLabelText('Nhập lại mật khẩu mới'), NEW_PASSWORD)
    await user.click(screen.getByRole('button', { name: /Lưu mật khẩu mới/ }))

    expect(await screen.findByRole('heading', { name: 'Đường dẫn đã hết hạn' })).toBeInTheDocument()
  })

  it('đường dẫn đã dùng hoặc thiếu mã thì hướng dẫn yêu cầu đường dẫn mới', async () => {
    server.use(http.post('*/api/v1/auth/password/reset/check', () => apiError(400, 'RESET_TOKEN_INVALID')))
    renderPage()
    expect(await screen.findByRole('heading', { name: 'Đường dẫn không còn dùng được' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Yêu cầu đường dẫn mới' })).toHaveAttribute('href', routes.forgotPassword)
  })

  it('mở trang không có mã thì báo đường dẫn không dùng được mà không gọi máy chủ', () => {
    renderPage('')
    expect(screen.getByRole('heading', { name: 'Đường dẫn không còn dùng được' })).toBeInTheDocument()
  })

  it('lỗi mạng khi kiểm tra thì cho thử lại', async () => {
    let attempt = 0
    server.use(
      http.post('*/api/v1/auth/password/reset/check', () => {
        attempt += 1
        return attempt === 1 ? HttpResponse.error() : new HttpResponse(null, { status: 204 })
      }),
    )
    const { user } = renderPage()

    expect(await screen.findByRole('heading', { name: 'Chưa kiểm tra được đường dẫn' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByRole('heading', { name: 'Đặt mật khẩu mới' })).toBeInTheDocument()
  })
})
