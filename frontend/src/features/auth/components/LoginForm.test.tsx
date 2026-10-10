import { act, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { routes } from '@/app/routes'
import { verifiedAccount } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import { LoginForm } from './LoginForm'

function apiError(status: number, code: string, details?: Record<string, unknown>) {
  return HttpResponse.json(
    { code, message: code, status, fieldErrors: [], details, requestId: 'req-1' },
    { status },
  )
}

function setup(notice?: ReactNode) {
  const onLoggedIn = vi.fn()
  const utils = renderWithProviders(<LoginForm notice={notice} onLoggedIn={onLoggedIn} />, {
    path: routes.login,
    extraRoutes: [{ path: routes.checkEmail, element: <p>kiểm tra hộp thư</p> }],
  })
  const fields = {
    email: () => screen.getByLabelText('Thư điện tử'),
    password: () => screen.getByLabelText('Mật khẩu', { exact: true }),
    remember: () => screen.getByRole('checkbox', { name: 'Ghi nhớ thiết bị này' }),
    submit: () => screen.getByRole('button', { name: /Đăng nhập/ }),
  }
  const fill = async (email = 'lan.anh@example.com', password = 'mùa thu hà nội 2026') => {
    await utils.user.type(fields.email(), email)
    await utils.user.type(fields.password(), password)
  }
  return { ...utils, fields, fill, onLoggedIn }
}

describe('LoginForm', () => {
  it('đăng nhập thành công gửi lựa chọn ghi nhớ thiết bị', async () => {
    let body: unknown
    server.use(
      http.post('*/api/v1/auth/login', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json(verifiedAccount)
      }),
    )
    const { user, fields, fill, onLoggedIn } = setup()
    await fill()
    await user.click(fields.remember())
    await user.click(fields.submit())

    await waitFor(() => expect(onLoggedIn).toHaveBeenCalledWith(verifiedAccount))
    expect(body).toEqual({ email: 'lan.anh@example.com', password: 'mùa thu hà nội 2026', rememberDevice: true })
  })

  it('báo lỗi từng trường khi bỏ trống', async () => {
    const { user, fields } = setup()
    await user.click(fields.submit())
    expect(await screen.findByText('Hãy nhập địa chỉ thư điện tử.')).toBeInTheDocument()
    expect(screen.getByText('Hãy nhập mật khẩu.')).toBeInTheDocument()
    expect(fields.email()).toHaveFocus()
  })

  it('sai mật khẩu: báo lỗi chung, xóa ô mật khẩu và đưa tiêu điểm về đó', async () => {
    server.use(http.post('*/api/v1/auth/login', () => apiError(401, 'INVALID_CREDENTIALS', { remainingAttempts: 4 })))
    const { user, fields, fill } = setup()
    await fill('lan.anh@example.com', 'sai-mat-khau')
    await user.click(fields.submit())

    expect(await screen.findByTestId('login-error')).toHaveTextContent('Thư điện tử hoặc mật khẩu chưa đúng.')
    expect(screen.queryByTestId('attempts-left')).not.toBeInTheDocument()
    expect(fields.password()).toHaveValue('')
    await waitFor(() => expect(fields.password()).toHaveFocus())
    expect(fields.email()).toHaveValue('lan.anh@example.com')
  })

  it('cảnh báo khi chỉ còn ít lần thử', async () => {
    server.use(http.post('*/api/v1/auth/login', () => apiError(401, 'INVALID_CREDENTIALS', { remainingAttempts: 1 })))
    const { user, fields, fill } = setup()
    await fill('lan.anh@example.com', 'sai-mat-khau')
    await user.click(fields.submit())

    expect(await screen.findByTestId('attempts-left')).toHaveTextContent(
      'Còn 1 lần thử trước khi đăng nhập bị tạm khóa 15 phút.',
    )
  })

  it('tạm khóa: hiện thời gian chờ, khóa biểu mẫu rồi tự mở lại khi hết giờ', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    server.use(http.post('*/api/v1/auth/login', () => apiError(423, 'ACCOUNT_LOCKED', { retryAfterSeconds: 3 })))
    const { user, fields, fill } = setup()
    await fill()
    await user.click(fields.submit())

    expect(await screen.findByTestId('lockout-notice')).toHaveTextContent('Đăng nhập đang tạm khóa')
    expect(screen.getByTestId('lockout-countdown')).toHaveTextContent(/0:0[23]/)
    expect(fields.email()).toBeDisabled()
    expect(fields.submit()).toBeDisabled()

    await act(async () => {
      await vi.advanceTimersByTimeAsync(4000)
    })
    expect(screen.queryByTestId('lockout-notice')).not.toBeInTheDocument()
    expect(screen.getByText('Hết thời gian khóa, bạn có thể đăng nhập lại.')).toBeInTheDocument()
    expect(fields.email()).toBeEnabled()
    vi.useRealTimers()
  })

  it('tài khoản chưa xác thực: cho gửi lại thư xác thực ngay từ màn hình đăng nhập', async () => {
    server.use(http.post('*/api/v1/auth/login', () => apiError(403, 'EMAIL_NOT_VERIFIED')))
    const { user, fields, fill, router } = setup()
    await fill()
    await user.click(fields.submit())

    await user.click(await screen.findByRole('button', { name: 'Gửi lại thư xác thực' }))
    expect(await screen.findByText('kiểm tra hộp thư')).toBeInTheDocument()
    expect(router.state.location.state).toMatchObject({ email: 'lan.anh@example.com' })
  })

  it('thông báo đầu trang biến mất sau lần gửi đầu tiên', async () => {
    server.use(http.post('*/api/v1/auth/login', () => apiError(401, 'INVALID_CREDENTIALS', { remainingAttempts: 4 })))
    const { user, fields, fill } = setup(<p>Bạn đã đăng xuất. Hẹn gặp lại!</p>)
    expect(screen.getByText('Bạn đã đăng xuất. Hẹn gặp lại!')).toBeInTheDocument()

    await fill()
    await user.click(fields.submit())

    await screen.findByTestId('login-error')
    expect(screen.queryByText('Bạn đã đăng xuất. Hẹn gặp lại!')).not.toBeInTheDocument()
  })

  it('lỗi mạng hiện thông báo chung', async () => {
    server.use(http.post('*/api/v1/auth/login', () => HttpResponse.error()))
    const { user, fields, fill } = setup()
    await fill()
    await user.click(fields.submit())
    expect(await screen.findByTestId('login-error')).toHaveTextContent('Không kết nối được tới máy chủ')
  })
})
