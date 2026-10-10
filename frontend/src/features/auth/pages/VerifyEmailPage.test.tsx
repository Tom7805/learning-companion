import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { routes } from '@/app/routes'
import { queryKeys } from '@/shared/api/queryKeys'
import { apiError, verifiedAccount } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import { VerifyEmailPage } from './VerifyEmailPage'

let tokenCounter = 0
function renderWithToken(token: string | null = `tok-${++tokenCounter}`) {
  return renderWithProviders(<VerifyEmailPage />, {
    path: routes.verifyEmail,
    initialEntries: [token ? `${routes.verifyEmail}?token=${token}` : routes.verifyEmail],
    extraRoutes: [
      { path: routes.home, element: <p>trang chính</p> },
      { path: routes.checkEmail, element: <p>kiểm tra hộp thư</p> },
    ],
  })
}

describe('VerifyEmailPage', () => {
  it('xác thực thành công: chào theo tên, đăng nhập luôn và xóa chuỗi bí mật khỏi địa chỉ', async () => {
    let calls = 0
    server.use(
      http.post('*/api/v1/auth/verify-email', () => {
        calls += 1
        return HttpResponse.json(verifiedAccount)
      }),
    )
    const { router, queryClient, user } = renderWithToken()

    expect(await screen.findByRole('heading', { name: 'Tài khoản đã được kích hoạt' })).toBeInTheDocument()
    expect(screen.getByText('Lan Anh')).toBeInTheDocument()
    expect(router.state.location.search).toBe('')
    expect(queryClient.getQueryData(queryKeys.session)).toEqual({ authenticated: true, account: verifiedAccount })
    expect(screen.getByTestId('redirect-countdown')).toHaveTextContent('5 giây')
    expect(calls).toBe(1)

    await user.click(screen.getByRole('button', { name: /Bắt đầu học/ }))
    expect(router.state.location.pathname).toBe(routes.home)
  })

  it('đường dẫn hết hạn: gửi thư mới bằng chính đường dẫn đó, không phải nhập lại địa chỉ', async () => {
    let resendBody: unknown
    server.use(
      http.post('*/api/v1/auth/verify-email', () => apiError(410, 'VERIFICATION_TOKEN_EXPIRED')),
      http.post('*/api/v1/auth/verify-email/resend', async ({ request }) => {
        resendBody = await request.json()
        return HttpResponse.json({ resendAvailableInSeconds: 60 }, { status: 202 })
      }),
    )
    const { user } = renderWithToken('het-han-123')

    expect(await screen.findByRole('heading', { name: 'Đường dẫn đã hết hạn' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /Gửi thư xác thực mới/ }))

    expect(await screen.findByTestId('expired-resent')).toHaveTextContent('Đã gửi thư mới')
    expect(resendBody).toEqual({ token: 'het-han-123' })
    expect(screen.getByRole('button', { name: /Gửi lại sau/ })).toBeDisabled()
  })

  it('đường dẫn đã dùng mà chưa đăng nhập thì dẫn tới trang đăng nhập', async () => {
    server.use(http.post('*/api/v1/auth/verify-email', () => apiError(409, 'VERIFICATION_TOKEN_USED')))
    renderWithToken()

    expect(await screen.findByRole('heading', { name: 'Đường dẫn này đã được dùng' })).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: 'Đi tới đăng nhập' })).toHaveAttribute('href', routes.login)
  })

  it('đường dẫn đã dùng khi đang đăng nhập thì dẫn vào ứng dụng', async () => {
    server.use(
      http.post('*/api/v1/auth/verify-email', () => apiError(409, 'VERIFICATION_TOKEN_USED')),
      http.get('*/api/v1/auth/session', () => HttpResponse.json({ authenticated: true, account: verifiedAccount })),
    )
    renderWithToken()
    expect(await screen.findByRole('link', { name: 'Vào ứng dụng' })).toHaveAttribute('href', routes.home)
  })

  it('đường dẫn không hợp lệ cho nhập địa chỉ để nhận thư khác', async () => {
    server.use(http.post('*/api/v1/auth/verify-email', () => apiError(400, 'VERIFICATION_TOKEN_INVALID')))
    const { user, router } = renderWithToken()

    expect(await screen.findByRole('heading', { name: 'Đường dẫn không hợp lệ' })).toBeInTheDocument()
    await user.type(screen.getByLabelText('Thư điện tử'), 'lan.anh@example.com')
    await user.click(screen.getByRole('button', { name: 'Gửi thư xác thực' }))

    expect(await screen.findByText('kiểm tra hộp thư')).toBeInTheDocument()
    expect(router.state.location.state).toMatchObject({ email: 'lan.anh@example.com' })
  })

  it('mở trang không kèm đường dẫn thì báo không hợp lệ, không gọi máy chủ', async () => {
    let calls = 0
    server.use(
      http.post('*/api/v1/auth/verify-email', () => {
        calls += 1
        return HttpResponse.json(verifiedAccount)
      }),
    )
    renderWithToken(null)
    expect(await screen.findByRole('heading', { name: 'Đường dẫn không hợp lệ' })).toBeInTheDocument()
    expect(calls).toBe(0)
  })

  it('lỗi mạng cho thử lại và thử lại thành công', async () => {
    let attempts = 0
    server.use(
      http.post('*/api/v1/auth/verify-email', () => {
        attempts += 1
        return attempts === 1 ? HttpResponse.error() : HttpResponse.json(verifiedAccount)
      }),
    )
    const { user } = renderWithToken()

    expect(await screen.findByRole('heading', { name: 'Chưa xác thực được' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Thử lại' }))

    expect(await screen.findByRole('heading', { name: 'Tài khoản đã được kích hoạt' })).toBeInTheDocument()
    expect(attempts).toBe(2)
  })
})
