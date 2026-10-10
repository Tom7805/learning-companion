import { screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { routes } from '@/app/routes'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import { pendingVerification } from '../lib/pendingVerification'
import { CheckEmailPage } from './CheckEmailPage'

function renderAt(state: unknown) {
  return renderWithProviders(<CheckEmailPage />, {
    path: routes.checkEmail,
    initialEntries: [{ pathname: routes.checkEmail, state }],
    extraRoutes: [{ path: routes.register, element: <p>trang đăng ký</p> }],
  })
}

describe('CheckEmailPage', () => {
  it('hiện địa chỉ đã gửi, thời hạn và nút mở Gmail', () => {
    renderAt({ email: 'lan.anh@gmail.com', resendAvailableAt: Date.now() + 60_000 })

    expect(screen.getByRole('heading', { name: 'Kiểm tra hộp thư của bạn' })).toBeInTheDocument()
    expect(screen.getByTestId('check-email-body')).toHaveTextContent('lan.anh@gmail.com')
    expect(screen.getByText(/hiệu lực 24 giờ/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Mở Gmail/ })).toHaveAttribute('href', 'https://mail.google.com/')
  })

  it('khóa nút gửi lại trong một phút và hiện thời gian đếm ngược', () => {
    renderAt({ email: 'lan.anh@example.com', resendAvailableAt: Date.now() + 42_000 })

    const button = screen.getByTestId('resend-button')
    expect(button).toBeDisabled()
    expect(button).toHaveTextContent(/Gửi lại sau 0:4[12]/)
    expect(screen.queryByRole('link', { name: /Mở/ })).not.toBeInTheDocument()
  })

  it('gửi lại khi hết thời gian chờ, báo đã gửi và khóa lại nút', async () => {
    let body: unknown
    server.use(
      http.post('*/api/v1/auth/verify-email/resend', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({ resendAvailableInSeconds: 60 }, { status: 202 })
      }),
    )
    const { user } = renderAt({ email: 'lan.anh@example.com', resendAvailableAt: Date.now() - 1 })

    await user.click(screen.getByRole('button', { name: 'Gửi lại thư' }))

    expect(await screen.findByTestId('resent-notice')).toHaveTextContent('một thư mới vừa được gửi')
    expect(body).toEqual({ email: 'lan.anh@example.com' })
    await waitFor(() => expect(screen.getByTestId('resend-button')).toBeDisabled())
    expect(pendingVerification.load()?.resendAvailableAt).toBeGreaterThan(Date.now())
  })

  it('khôi phục màn hình từ sessionStorage khi tải lại trang', () => {
    pendingVerification.save({ email: 'luu.tam@example.com', resendAvailableAt: Date.now() + 10_000 })
    renderAt(null)
    expect(screen.getByTestId('check-email-body')).toHaveTextContent('luu.tam@example.com')
  })

  it('không biết địa chỉ thì cho nhập địa chỉ để gửi lại', async () => {
    const { user } = renderAt(null)

    await user.type(screen.getByLabelText('Thư điện tử'), 'lan.anh@example.com')
    await user.click(screen.getByRole('button', { name: 'Gửi thư xác thực' }))

    expect(await screen.findByTestId('check-email-body')).toHaveTextContent('lan.anh@example.com')
    expect(screen.getByTestId('resent-notice')).toBeInTheDocument()
  })

  it('đổi địa chỉ thư quay về biểu mẫu đăng ký', async () => {
    const { user, router } = renderAt({
      email: 'lan.anh@example.com',
      displayName: 'Lan Anh',
      resendAvailableAt: Date.now() + 60_000,
    })

    await user.click(screen.getByRole('button', { name: 'Đổi địa chỉ thư' }))

    expect(router.state.location.pathname).toBe(routes.register)
    expect(router.state.location.state).toEqual({ prefill: { email: 'lan.anh@example.com', displayName: 'Lan Anh' } })
    expect(pendingVerification.load()).toBeNull()
  })
})
