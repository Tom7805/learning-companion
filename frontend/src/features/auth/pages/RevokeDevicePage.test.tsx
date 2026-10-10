import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { routes } from '@/app/routes'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import { RevokeDevicePage } from './RevokeDevicePage'

let counter = 0
function renderWithToken(token: string | null = `lien-ket-${++counter}`) {
  return renderWithProviders(<RevokeDevicePage />, {
    path: routes.revokeDevice,
    initialEntries: [token ? `${routes.revokeDevice}?token=${token}` : routes.revokeDevice],
  })
}

describe('RevokeDevicePage', () => {
  it('đăng xuất thiết bị trong thư, xóa chuỗi bí mật khỏi địa chỉ và khuyên đổi mật khẩu', async () => {
    let body: unknown
    server.use(
      http.post('*/api/v1/auth/sessions/revoke-link', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({
          browser: 'Chrome',
          operatingSystem: 'Windows',
          signedInAt: '2026-10-10T01:00:00Z',
          alreadyRevoked: false,
        })
      }),
    )
    const { router } = renderWithToken('lien-ket-a')

    expect(await screen.findByRole('heading', { name: 'Đã đăng xuất thiết bị đó' })).toBeInTheDocument()
    expect(screen.getByTestId('revoke-result')).toHaveTextContent('Chrome trên Windows')
    expect(screen.getByText(/đổi sang một mật khẩu mới/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Đi tới đăng nhập' })).toHaveAttribute('href', routes.login)
    expect(router.state.location.search).toBe('')
    expect(body).toEqual({ token: 'lien-ket-a' })
  })

  it('bấm lại đường dẫn đã dùng thì báo thiết bị đã được đăng xuất trước đó', async () => {
    server.use(
      http.post('*/api/v1/auth/sessions/revoke-link', () =>
        HttpResponse.json({
          browser: 'Safari',
          operatingSystem: 'iOS',
          signedInAt: '2026-10-10T01:00:00Z',
          alreadyRevoked: true,
        }),
      ),
    )
    renderWithToken()
    expect(await screen.findByRole('heading', { name: 'Thiết bị đó đã được đăng xuất' })).toBeInTheDocument()
  })

  it('đường dẫn sai hoặc thiếu thì báo không hợp lệ', async () => {
    server.use(
      http.post('*/api/v1/auth/sessions/revoke-link', () =>
        HttpResponse.json({ code: 'REVOKE_LINK_INVALID', message: '', status: 400, fieldErrors: [] }, { status: 400 }),
      ),
    )
    renderWithToken()
    expect(await screen.findByRole('heading', { name: 'Đường dẫn không hợp lệ' })).toBeInTheDocument()
  })

  it('máy chủ gặp sự cố thì báo đúng ngữ cảnh và cho thử lại thành công', async () => {
    let attempts = 0
    server.use(
      http.post('*/api/v1/auth/sessions/revoke-link', () => {
        attempts += 1
        return attempts === 1
          ? HttpResponse.json({ code: 'INTERNAL_ERROR', message: '', status: 500, fieldErrors: [] }, { status: 500 })
          : HttpResponse.json({
              browser: 'Chrome',
              operatingSystem: 'Windows',
              signedInAt: '2026-10-10T01:00:00Z',
              alreadyRevoked: false,
            })
      }),
    )
    const { user } = renderWithToken()

    expect(await screen.findByRole('heading', { name: 'Chưa đăng xuất được thiết bị' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByRole('heading', { name: 'Đã đăng xuất thiết bị đó' })).toBeInTheDocument()
    expect(attempts).toBe(2)
  })

  it('không có đường dẫn thì không gọi máy chủ', async () => {
    let calls = 0
    server.use(
      http.post('*/api/v1/auth/sessions/revoke-link', () => {
        calls += 1
        return HttpResponse.json({})
      }),
    )
    renderWithToken(null)
    expect(await screen.findByRole('heading', { name: 'Đường dẫn không hợp lệ' })).toBeInTheDocument()
    expect(calls).toBe(0)
  })
})
