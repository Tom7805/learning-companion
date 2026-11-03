import { screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { routes } from '@/app/routes'
import { queryKeys } from '@/shared/api/queryKeys'
import { apiError } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import { AdminTwoFactorPage } from './AdminTwoFactorPage'

const BASE = '*/api/v1/auth/admin/two-factor'
const adminAccount = {
  id: '0b6f8d2e-1111-4f0a-9c1e-0000000000ad',
  email: 'quan.tri@example.com',
  displayName: 'Quản trị viên',
  role: 'ADMIN',
  status: 'ACTIVE',
}
const recoveryCodes = Array.from({ length: 10 }, (_, index) => `abcd${index}-efgh${index}`)

function challenge(stage: 'VERIFY' | 'ENROLL') {
  server.use(
    http.get(BASE, () =>
      HttpResponse.json({ stage, email: 'quan.tri@example.com', expiresAt: '2026-11-03T05:00:00Z' }),
    ),
  )
}

function renderPage() {
  return renderWithProviders(<AdminTwoFactorPage />, {
    path: routes.adminTwoFactor,
    extraRoutes: [
      { path: routes.adminAudit, element: <p>Trang nhật ký</p> },
      { path: routes.login, element: <p>Trang đăng nhập</p> },
    ],
  })
}

describe('AdminTwoFactorPage', () => {
  it('TC-01: mã sáu chữ số đúng thì vào trang quản trị với phiên quản trị', async () => {
    challenge('VERIFY')
    let sent: unknown
    server.use(
      http.post(`${BASE}/verify`, async ({ request }) => {
        sent = await request.json()
        return HttpResponse.json({
          signedIn: true,
          account: adminAccount,
          sessionExpiresAt: '2026-11-03T13:00:00Z',
          recoveryCodes: [],
        })
      }),
    )
    const { user, queryClient } = renderPage()

    expect(await screen.findByRole('heading', { level: 1, name: 'Nhập mã xác thực' })).toBeInTheDocument()
    expect(screen.getByText(/quan\.tri@example\.com/)).toBeInTheDocument()
    const input = screen.getByLabelText('Mã sáu chữ số')
    expect(input).toHaveAttribute('autocomplete', 'one-time-code')
    expect(input).toHaveAttribute('inputmode', 'numeric')
    await user.type(input, '123456')
    await user.click(screen.getByRole('button', { name: /Xác thực và vào trang quản trị/ }))

    expect(await screen.findByText('Trang nhật ký')).toBeInTheDocument()
    expect(sent).toEqual({ code: '123456' })
    expect(queryClient.getQueryData(queryKeys.session)).toEqual({ authenticated: true, account: adminAccount })
  })

  it('kiểm tra định dạng mã trước khi gửi', async () => {
    challenge('VERIFY')
    const { user } = renderPage()
    await user.type(await screen.findByLabelText('Mã sáu chữ số'), '12a')
    await user.click(screen.getByRole('button', { name: /Xác thực/ }))
    expect(await screen.findByText('Mã gồm đúng sáu chữ số.')).toBeInTheDocument()
  })

  it('TC-02: sai mã báo số lần còn lại, lần thứ năm khóa mười lăm phút', async () => {
    challenge('VERIFY')
    let attempt = 0
    server.use(
      http.post(`${BASE}/verify`, () => {
        attempt += 1
        return attempt < 5
          ? apiError(400, 'TWO_FACTOR_CODE_INVALID', 'code', { remainingAttempts: 5 - attempt })
          : apiError(423, 'ADMIN_LOGIN_LOCKED', undefined, { retryAfterSeconds: 900 })
      }),
    )
    const { user } = renderPage()
    const input = await screen.findByLabelText('Mã sáu chữ số')
    for (let index = 0; index < 4; index += 1) {
      await user.type(input, '000000')
      await user.click(screen.getByRole('button', { name: /Xác thực/ }))
      await waitFor(() => expect(screen.getByTestId('two-factor-error')).toBeInTheDocument())
    }
    expect(screen.getByTestId('attempts-left')).toHaveTextContent('Còn 1 lần thử')
    await user.type(input, '000000')
    await user.click(screen.getByRole('button', { name: /Xác thực/ }))

    const notice = await screen.findByTestId('lockout-notice')
    expect(notice).toHaveTextContent('Đăng nhập quản trị đang tạm khóa')
    expect(notice).toHaveTextContent('Thư cảnh báo đã được gửi')
    expect(notice).toHaveTextContent('15:00')
    expect(screen.getByLabelText('Mã sáu chữ số')).toBeDisabled()
  })

  it('TC-03: mã khôi phục cho vào bước thiết lập lại, rồi nhận mười mã mới trước khi vào trang quản trị', async () => {
    challenge('VERIFY')
    let recoverySent: unknown
    server.use(
      http.post(`${BASE}/recover`, async ({ request }) => {
        recoverySent = await request.json()
        return HttpResponse.json({ signedIn: false, stage: 'ENROLL', recoveryCodes: [] })
      }),
      http.post(`${BASE}/enrollment`, () =>
        HttpResponse.json({
          secret: 'JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP',
          otpauthUri: 'otpauth://totp/Learning%20Companion:quan.tri%40example.com?secret=JBSWY3DPEHPK3PXP',
        }),
      ),
      http.post(`${BASE}/enrollment/confirm`, () =>
        HttpResponse.json({
          signedIn: true,
          account: adminAccount,
          sessionExpiresAt: '2026-11-03T13:00:00Z',
          recoveryCodes,
        }),
      ),
    )
    const { user } = renderPage()
    await user.click(await screen.findByRole('button', { name: /Dùng mã khôi phục/ }))
    expect(screen.getByRole('heading', { level: 1, name: 'Dùng mã khôi phục' })).toBeInTheDocument()
    await user.type(screen.getByLabelText('Mã khôi phục'), 'ABCDE-FGHJK')
    await user.click(screen.getByRole('button', { name: /Xác thực/ }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Thiết lập lại xác thực hai bước' })).toBeInTheDocument()
    expect(recoverySent).toEqual({ recoveryCode: 'ABCDE-FGHJK' })
    expect(screen.getByTestId('recovery-used')).toHaveTextContent('không dùng lại được')
    expect(await screen.findByTestId('totp-secret')).toHaveTextContent('JBSW Y3DP EHPK 3PXP JBSW Y3DP EHPK 3PXP')
    expect(screen.getByRole('link', { name: /Mở trong ứng dụng sinh mã/ })).toHaveAttribute(
      'href',
      expect.stringContaining('otpauth://totp/'),
    )

    await user.type(screen.getByLabelText('Mã sáu chữ số từ ứng dụng'), '654321')
    await user.click(screen.getByRole('button', { name: /Hoàn tất thiết lập/ }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Lưu mười mã khôi phục' })).toBeInTheDocument()
    expect(screen.getAllByTestId('recovery-code')).toHaveLength(10)
    const enter = screen.getByRole('button', { name: /Vào trang quản trị/ })
    expect(enter).toBeDisabled()
    await user.click(screen.getByRole('checkbox', { name: 'Tôi đã lưu các mã ở nơi an toàn' }))
    await user.click(enter)
    expect(await screen.findByText('Trang nhật ký')).toBeInTheDocument()
  })

  it('lần đầu đăng nhập buộc thiết lập ứng dụng sinh mã; mã sai thì báo kiểm tra khóa và giờ điện thoại', async () => {
    challenge('ENROLL')
    server.use(
      http.post(`${BASE}/enrollment`, () =>
        HttpResponse.json({ secret: 'JBSWY3DPEHPK3PXP', otpauthUri: 'otpauth://totp/x?secret=JBSWY3DPEHPK3PXP' }),
      ),
      http.post(`${BASE}/enrollment/confirm`, () =>
        apiError(400, 'TWO_FACTOR_CODE_INVALID', 'code', { remainingAttempts: 4 }),
      ),
    )
    const { user } = renderPage()
    expect(await screen.findByRole('heading', { level: 1, name: 'Thiết lập xác thực hai bước' })).toBeInTheDocument()
    await screen.findByTestId('totp-secret')
    await user.type(screen.getByLabelText('Mã sáu chữ số từ ứng dụng'), '111111')
    await user.click(screen.getByRole('button', { name: /Hoàn tất thiết lập/ }))
    expect(await screen.findByTestId('two-factor-error')).toHaveTextContent('giờ trên điện thoại')
  })

  it('bước xác thực hết hạn hoặc chưa qua bước mật khẩu thì mời đăng nhập lại', async () => {
    server.use(http.get(BASE, () => apiError(401, 'TWO_FACTOR_CHALLENGE_INVALID')))
    const { user } = renderPage()
    expect(await screen.findByTestId('two-factor-expired')).toHaveTextContent('Bước xác thực đã hết hạn')
    await user.click(screen.getByRole('link', { name: 'Quay lại đăng nhập' }))
    expect(await screen.findByText('Trang đăng nhập')).toBeInTheDocument()
  })
})
