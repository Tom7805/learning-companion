import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { routes } from '@/app/routes'
import { apiError, deviceSessions, verifiedAccount } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import { SecurityPage } from './SecurityPage'

const CURRENT = 'song xanh mua thu 2026'
const NEXT = 'bien xanh gio dong 2027'

function signedIn(account = verifiedAccount) {
  server.use(http.get('*/api/v1/auth/session', () => HttpResponse.json({ authenticated: true, account })))
}

function renderPage() {
  return renderWithProviders(<SecurityPage />, { path: routes.security })
}

async function fillChangeForm(user: ReturnType<typeof renderPage>['user'], current = CURRENT, next = NEXT) {
  await user.type(await screen.findByLabelText('Mật khẩu hiện tại'), current)
  await user.type(screen.getByLabelText('Mật khẩu mới'), next)
  await user.type(screen.getByLabelText('Nhập lại mật khẩu mới'), next)
}

describe('SecurityPage · đổi mật khẩu', () => {
  it('đổi thành công thì xóa trống biểu mẫu và báo số thiết bị khác đã bị đăng xuất', async () => {
    signedIn()
    let body: unknown
    server.use(
      http.post('*/api/v1/auth/password/change', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({ revokedSessions: 2 })
      }),
    )
    const { user } = renderPage()

    await fillChangeForm(user)
    await user.click(screen.getByRole('button', { name: 'Đổi mật khẩu' }))

    expect(await screen.findByTestId('change-password-success')).toHaveTextContent(
      'Đã đổi mật khẩu và đăng xuất 2 thiết bị khác.',
    )
    expect(body).toEqual({ currentPassword: CURRENT, newPassword: NEXT })
    expect(screen.getByLabelText('Mật khẩu hiện tại')).toHaveValue('')
    expect(screen.getByLabelText('Mật khẩu mới')).toHaveValue('')
  })

  it('không có thiết bị nào khác thì chỉ báo đã đổi và đã gửi thư xác nhận', async () => {
    signedIn()
    server.use(http.post('*/api/v1/auth/password/change', () => HttpResponse.json({ revokedSessions: 0 })))
    const { user } = renderPage()

    await fillChangeForm(user)
    await user.click(screen.getByRole('button', { name: 'Đổi mật khẩu' }))

    expect(await screen.findByTestId('change-password-success')).toHaveTextContent(
      'Đã đổi mật khẩu. Thư xác nhận đã được gửi tới hộp thư của bạn.',
    )
  })

  it('mật khẩu mới trùng mật khẩu hiện tại bị chặn ngay trên giao diện', async () => {
    signedIn()
    const { user } = renderPage()

    await fillChangeForm(user, CURRENT, CURRENT)
    await user.click(screen.getByRole('button', { name: 'Đổi mật khẩu' }))

    expect(await screen.findByText('Mật khẩu mới cần khác mật khẩu hiện tại.')).toBeInTheDocument()
  })

  it('TC-03: sai mật khẩu hiện tại thì báo dưới ô, xóa ô đó, giữ mật khẩu mới và cảnh báo số lần còn lại', async () => {
    signedIn()
    server.use(
      http.post('*/api/v1/auth/password/change', () =>
        apiError(400, 'CURRENT_PASSWORD_INVALID', 'currentPassword', { remainingAttempts: 2 }),
      ),
    )
    const { user } = renderPage()

    await fillChangeForm(user, 'sai mat khau roi')
    await user.click(screen.getByRole('button', { name: 'Đổi mật khẩu' }))

    expect(await screen.findByText('Mật khẩu hiện tại chưa đúng.')).toBeInTheDocument()
    expect(screen.getByTestId('change-attempts-left')).toHaveTextContent(
      'Còn 2 lần thử trước khi tài khoản bị tạm khóa 15 phút.',
    )
    expect(screen.getByLabelText('Mật khẩu hiện tại')).toHaveValue('')
    expect(screen.getByLabelText('Mật khẩu mới')).toHaveValue(NEXT)
    await waitFor(() => expect(screen.getByLabelText('Mật khẩu hiện tại')).toHaveFocus())
  })

  it('sai đủ năm lần thì khóa biểu mẫu và hiện thời gian chờ', async () => {
    signedIn()
    server.use(
      http.post('*/api/v1/auth/password/change', () =>
        apiError(423, 'ACCOUNT_LOCKED', undefined, { retryAfterSeconds: 900 }),
      ),
    )
    const { user } = renderPage()

    await fillChangeForm(user)
    await user.click(screen.getByRole('button', { name: 'Đổi mật khẩu' }))

    const notice = await screen.findByTestId('lockout-notice')
    expect(notice).toHaveTextContent('Tạm khóa đổi mật khẩu')
    expect(within(notice).getByTestId('lockout-countdown')).toHaveTextContent(/1[45]:\d\d/)
    expect(screen.getByLabelText('Mật khẩu hiện tại')).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Đổi mật khẩu' })).toBeDisabled()
  })

  it('mật khẩu mới đã bị lộ thì báo dưới ô mật khẩu mới', async () => {
    signedIn()
    server.use(http.post('*/api/v1/auth/password/change', () => apiError(400, 'PASSWORD_BREACHED', 'newPassword')))
    const { user } = renderPage()

    await fillChangeForm(user, CURRENT, 'Password1234')
    await user.click(screen.getByRole('button', { name: 'Đổi mật khẩu' }))

    expect(await screen.findByText(/đã từng bị lộ/)).toBeInTheDocument()
    await waitFor(() => expect(screen.getByLabelText('Mật khẩu mới')).toHaveFocus())
  })

  it('hiện lần đổi mật khẩu gần nhất', async () => {
    signedIn({ ...verifiedAccount, passwordChangedAt: new Date(Date.now() - 5 * 86_400_000).toISOString() })
    renderPage()
    expect(await screen.findByText('Đổi mật khẩu lần cuối 5 ngày trước')).toBeInTheDocument()
  })

  it('liên kết quên mật khẩu mang theo địa chỉ thư của tài khoản', async () => {
    signedIn()
    renderPage()
    expect(await screen.findByRole('link', { name: 'Không nhớ mật khẩu hiện tại?' })).toHaveAttribute(
      'href',
      routes.forgotPassword,
    )
  })
})

describe('SecurityPage · đăng xuất khỏi mọi thiết bị', () => {
  it('TC-04: xác nhận thì thu hồi các phiên khác và báo số thiết bị đã đăng xuất', async () => {
    signedIn()
    let calls = 0
    server.use(
      http.post('*/api/v1/auth/sessions/revoke-others', () => {
        calls += 1
        return HttpResponse.json({ revokedCount: 1 })
      }),
    )
    const { user } = renderPage()

    expect(await screen.findByTestId('other-sessions-count')).toHaveTextContent('1 thiết bị khác đang đăng nhập')
    await user.click(screen.getByTestId('sign-out-everywhere'))
    const dialog = await screen.findByRole('dialog', { name: 'Đăng xuất mọi thiết bị khác?' })
    expect(dialog).toHaveTextContent('1 thiết bị khác sẽ phải đăng nhập lại. Thiết bị này vẫn giữ đăng nhập.')
    await user.click(within(dialog).getByRole('button', { name: 'Đăng xuất' }))

    expect(await screen.findByTestId('security-notice')).toHaveTextContent('Đã đăng xuất 1 thiết bị khác.')
    expect(calls).toBe(1)
  })

  it('chỉ còn thiết bị này thì nút bị vô hiệu và nói rõ lý do', async () => {
    signedIn()
    server.use(http.get('*/api/v1/auth/sessions', () => HttpResponse.json(deviceSessions.filter((s) => s.current))))
    renderPage()

    expect(await screen.findByTestId('other-sessions-count')).toHaveTextContent(
      'Không có thiết bị nào khác đang đăng nhập.',
    )
    expect(screen.getByTestId('sign-out-everywhere')).toBeDisabled()
  })
})
