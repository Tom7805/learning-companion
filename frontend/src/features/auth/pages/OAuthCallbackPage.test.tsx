import { screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { routes } from '@/app/routes'
import { queryKeys } from '@/shared/api/queryKeys'
import { apiError, verifiedAccount } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import { OAuthCallbackPage } from './OAuthCallbackPage'

let counter = 0

function renderCallback(search?: string) {
  counter += 1
  return renderWithProviders(<OAuthCallbackPage />, {
    path: routes.oauthCallback,
    initialEntries: [`/auth/callback/google${search ?? `?code=ma-${counter}&state=trang-thai-${counter}`}`],
    extraRoutes: [
      { path: routes.home, element: <p>trang chính</p> },
      { path: routes.login, element: <p>đăng nhập</p> },
    ],
  })
}

describe('OAuthCallbackPage', () => {
  it('đã liên kết từ trước thì vào thẳng ứng dụng và xóa mã khỏi thanh địa chỉ', async () => {
    let body: unknown
    server.use(
      http.post('*/api/v1/auth/oauth/google/callback', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({ status: 'SIGNED_IN', provider: 'google', account: verifiedAccount })
      }),
    )
    const { router, queryClient } = renderCallback('?code=ma-1&state=trang-thai-1')

    expect(await screen.findByText('trang chính')).toBeInTheDocument()
    expect(body).toEqual({ code: 'ma-1', state: 'trang-thai-1', error: null })
    expect(queryClient.getQueryData(queryKeys.session)).toEqual({ authenticated: true, account: verifiedAccount })
    expect(router.state.location.search).toBe('')
  })

  it('TC-01: người mới đồng ý điều khoản thì tạo tài khoản với thư đã xác thực', async () => {
    let signup: unknown
    server.use(
      http.post('*/api/v1/auth/oauth/google/callback', () =>
        HttpResponse.json({
          status: 'TERMS_REQUIRED',
          provider: 'google',
          pendingToken: 'tiep-tuc',
          email: 'minh.an@gmail.com',
          displayName: 'Nguyễn Minh An',
        }),
      ),
      http.post('*/api/v1/auth/oauth/signup', async ({ request }) => {
        signup = await request.json()
        return HttpResponse.json({ status: 'SIGNED_IN', provider: 'google', account: verifiedAccount })
      }),
    )
    const { user } = renderCallback()

    expect(await screen.findByRole('heading', { name: 'Hoàn tất tạo tài khoản' })).toBeInTheDocument()
    expect(screen.getByTestId('oauth-email')).toHaveTextContent('minh.an@gmail.com')
    expect(screen.getByTestId('oauth-email')).toHaveTextContent('Đã được Google xác thực')
    expect(screen.getByLabelText('Tên hiển thị')).toHaveValue('Nguyễn Minh An')

    await user.click(screen.getByRole('button', { name: /Tạo tài khoản và vào học/ }))
    expect(await screen.findByText(/Bạn cần đồng ý điều khoản/)).toBeInTheDocument()
    expect(signup).toBeUndefined()

    await user.click(screen.getByRole('checkbox'))
    await user.click(screen.getByRole('button', { name: /Tạo tài khoản và vào học/ }))
    expect(await screen.findByText('trang chính')).toBeInTheDocument()
    expect(signup).toEqual({
      token: 'tiep-tuc',
      displayName: 'Nguyễn Minh An',
      acceptTerms: true,
      termsVersion: '2026-10-01',
      privacyVersion: '2026-10-01',
    })
  })

  it('TC-02: địa chỉ thư đã có tài khoản thì yêu cầu mật khẩu để liên kết, sai mật khẩu báo số lần còn lại', async () => {
    let attempts = 0
    server.use(
      http.post('*/api/v1/auth/oauth/google/callback', () =>
        HttpResponse.json({ status: 'LINK_REQUIRED', provider: 'google', pendingToken: 'lien-ket', email: 'lan.anh@example.com' }),
      ),
      http.post('*/api/v1/auth/oauth/link', async ({ request }) => {
        attempts += 1
        const { password } = (await request.json()) as { password: string }
        return password === 'dung-mat-khau'
          ? HttpResponse.json({ status: 'SIGNED_IN', provider: 'google', account: verifiedAccount })
          : apiError(401, 'INVALID_CREDENTIALS', 'password', { remainingAttempts: 2 })
      }),
    )
    const { user } = renderCallback()

    expect(await screen.findByRole('heading', { name: 'Địa chỉ thư này đã có tài khoản' })).toBeInTheDocument()
    expect(screen.getByText(/không tự gộp tài khoản/)).toBeInTheDocument()

    await user.type(screen.getByLabelText('Mật khẩu'), 'sai-mat-khau')
    await user.click(screen.getByRole('button', { name: /Liên kết và đăng nhập/ }))
    expect(await screen.findByText('Mật khẩu chưa đúng.')).toBeInTheDocument()
    expect(screen.getByTestId('link-attempts-left')).toHaveTextContent('Còn 2 lần thử')
    expect(screen.getByLabelText('Mật khẩu')).toHaveValue('')

    await user.type(screen.getByLabelText('Mật khẩu'), 'dung-mat-khau')
    await user.click(screen.getByRole('button', { name: /Liên kết và đăng nhập/ }))
    expect(await screen.findByText('trang chính')).toBeInTheDocument()
    expect(attempts).toBe(2)
  })

  it('TC-03: mã chống giả mạo không khớp thì báo không đăng nhập được và hướng về màn hình đăng nhập', async () => {
    server.use(http.post('*/api/v1/auth/oauth/google/callback', () => apiError(400, 'OAUTH_STATE_INVALID')))
    renderCallback()

    expect(await screen.findByTestId('oauth-failed')).toHaveTextContent('Không xác minh được yêu cầu đăng nhập này')
    expect(screen.getByRole('link', { name: 'Về màn hình đăng nhập' })).toHaveAttribute('href', routes.login)
  })

  it('người dùng hủy ở nhà cung cấp thì báo đã hủy', async () => {
    let body: unknown
    server.use(
      http.post('*/api/v1/auth/oauth/google/callback', async ({ request }) => {
        body = await request.json()
        return apiError(400, 'OAUTH_CANCELLED')
      }),
    )
    renderCallback('?error=access_denied&state=huy-1')

    expect(await screen.findByTestId('oauth-failed')).toHaveTextContent('Bạn đã hủy đăng nhập bằng Google')
    await waitFor(() => expect(body).toEqual({ code: null, state: 'huy-1', error: 'access_denied' }))
  })

  it('liên kết từ cài đặt xong thì quay về đúng trang kèm phần tài khoản liên kết', async () => {
    server.use(
      http.post('*/api/v1/auth/oauth/google/callback', () =>
        HttpResponse.json({ status: 'LINKED', provider: 'google', returnTo: '/' }),
      ),
    )
    const { router } = renderCallback()

    await screen.findByText('trang chính')
    expect(router.state.location.search).toBe('?settings=accounts&linked=google')
  })
})
