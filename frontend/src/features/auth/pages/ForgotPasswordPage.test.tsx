import { screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { routes } from '@/app/routes'
import { apiError } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import { ForgotPasswordPage } from './ForgotPasswordPage'

function renderPage(state?: unknown) {
  return renderWithProviders(<ForgotPasswordPage />, {
    path: routes.forgotPassword,
    initialEntries: [{ pathname: routes.forgotPassword, state }],
  })
}

describe('ForgotPasswordPage', () => {
  it('gửi yêu cầu rồi báo đã gửi hướng dẫn, kèm nút mở hộp thư và đếm ngược gửi lại', async () => {
    let body: unknown
    server.use(
      http.post('*/api/v1/auth/password/forgot', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({ resendAvailableInSeconds: 60 }, { status: 202 })
      }),
    )
    const { user } = renderPage()

    await user.type(screen.getByLabelText('Thư điện tử'), '  lan.anh@gmail.com ')
    await user.click(screen.getByRole('button', { name: /Gửi hướng dẫn/ }))

    expect(await screen.findByRole('heading', { name: 'Kiểm tra hộp thư của bạn' })).toHaveFocus()
    expect(body).toEqual({ email: 'lan.anh@gmail.com' })
    expect(screen.getByTestId('forgot-sent-body')).toHaveTextContent(
      'Nếu lan.anh@gmail.com có tài khoản Learning Companion, hướng dẫn đặt lại mật khẩu đã được gửi tới địa chỉ này.',
    )
    expect(screen.getByRole('link', { name: 'Mở Gmail' })).toHaveAttribute('href', 'https://mail.google.com/')
    expect(screen.getByTestId('forgot-resend')).toBeDisabled()
    expect(screen.getByTestId('forgot-resend')).toHaveTextContent(/Gửi lại sau 0?1:00|Gửi lại sau 0:5\d/)
  })

  it('kiểm định dạng địa chỉ trước khi gửi', async () => {
    let calls = 0
    server.use(
      http.post('*/api/v1/auth/password/forgot', () => {
        calls += 1
        return HttpResponse.json({ resendAvailableInSeconds: 60 }, { status: 202 })
      }),
    )
    const { user } = renderPage()

    await user.click(screen.getByRole('button', { name: /Gửi hướng dẫn/ }))
    expect(await screen.findByText('Hãy nhập địa chỉ thư điện tử.')).toBeInTheDocument()
    await user.type(screen.getByLabelText('Thư điện tử'), 'lan.anh')
    await user.click(screen.getByRole('button', { name: /Gửi hướng dẫn/ }))
    expect(await screen.findByText(/Địa chỉ thư chưa đúng định dạng/)).toBeInTheDocument()
    expect(calls).toBe(0)
  })

  it('điền sẵn địa chỉ mang sang từ màn hình đăng nhập', () => {
    renderPage({ email: 'lan.anh@example.com' })
    expect(screen.getByLabelText('Thư điện tử')).toHaveValue('lan.anh@example.com')
  })

  it('"Dùng địa chỉ khác" quay lại biểu mẫu với địa chỉ cũ để sửa', async () => {
    const { user } = renderPage({ sent: { email: 'lan.anh@example.com', resendAvailableAt: Date.now() + 60_000 } })

    await user.click(screen.getByRole('button', { name: 'Dùng địa chỉ khác' }))

    expect(await screen.findByLabelText('Thư điện tử')).toHaveValue('lan.anh@example.com')
  })

  it('hết thời gian chờ thì gửi lại được và báo thư cũ hết hiệu lực', async () => {
    let calls = 0
    server.use(
      http.post('*/api/v1/auth/password/forgot', () => {
        calls += 1
        return HttpResponse.json({ resendAvailableInSeconds: 60 }, { status: 202 })
      }),
    )
    const { user } = renderPage({ sent: { email: 'lan.anh@example.com', resendAvailableAt: Date.now() - 1 } })

    await user.click(screen.getByRole('button', { name: 'Gửi lại thư' }))

    expect(await screen.findByTestId('forgot-resent')).toHaveTextContent('đường dẫn trong thư cũ không còn hiệu lực')
    expect(calls).toBe(1)
    await waitFor(() => expect(screen.getByTestId('forgot-resend')).toBeDisabled())
  })

  it('lỗi máy chủ hiện thông báo và giữ nguyên biểu mẫu', async () => {
    server.use(http.post('*/api/v1/auth/password/forgot', () => apiError(429, 'RATE_LIMITED')))
    const { user } = renderPage()

    await user.type(screen.getByLabelText('Thư điện tử'), 'lan.anh@example.com')
    await user.click(screen.getByRole('button', { name: /Gửi hướng dẫn/ }))

    expect(await screen.findByTestId('forgot-error')).toHaveTextContent('Bạn thao tác quá nhanh')
    await waitFor(() => expect(screen.getByLabelText('Thư điện tử')).toHaveFocus())
  })
})
