import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ProtectedRoute } from '@/app/ProtectedRoute'
import { routes } from '@/app/routes'
import { reloadTo } from '@/shared/lib/navigation'
import { apiError, readyExport, verifiedAccount } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import { DeleteAccountFlow } from './DeleteAccountFlow'

vi.mock('@/shared/lib/navigation', () => ({ reloadTo: vi.fn() }))

const SCHEDULED_FOR = '2026-11-12T03:00:00Z'

/** Ghi lại thân các lần yêu cầu xóa để kiểm có gửi mật khẩu hay không, hoặc không gửi gì. */
let requests: Record<string, unknown>[] = []

function renderFlow({ hasPassword = true } = {}) {
  server.use(
    http.get('*/api/v1/auth/session', () =>
      HttpResponse.json({ authenticated: true, account: { ...verifiedAccount, hasPassword } }),
    ),
  )
  return renderWithProviders(
    <Routes>
      <Route element={<ProtectedRoute />}>
        <Route path="delete" element={<DeleteAccountFlow />} />
      </Route>
    </Routes>,
    {
      path: '/account/*',
      initialEntries: [routes.deleteAccount],
      extraRoutes: [
        { path: routes.login, element: <p>trang đăng nhập</p> },
        { path: routes.home, element: <p>trang chủ</p> },
      ],
    },
  )
}

function acceptDeletion(respond: (body: Record<string, unknown>) => Response | Promise<Response>) {
  server.use(
    http.post('*/api/v1/accounts/:id/deletion', async ({ request }) => {
      const body = (await request.json()) as Record<string, unknown>
      requests.push(body)
      return respond(body)
    }),
  )
}

const scheduled = () =>
  HttpResponse.json(
    { pending: true, requestedAt: '2026-10-13T03:00:00Z', scheduledFor: SCHEDULED_FOR, graceDays: 30 },
    { status: 202 },
  )

describe('DeleteAccountFlow', () => {
  beforeEach(() => {
    requests = []
    vi.mocked(reloadTo).mockClear()
  })

  it('gợi ý xuất dữ liệu trước và nêu rõ những gì bị xóa, hạn chót đổi ý', async () => {
    server.use(http.get('*/api/v1/exports', () => HttpResponse.json({ latest: readyExport, canRequest: false })))
    renderFlow()

    expect(await screen.findByRole('heading', { level: 1, name: 'Xóa tài khoản' })).toBeInTheDocument()
    expect(await screen.findByTestId('deletion-export-status')).toHaveTextContent('Bạn đã có một gói dữ liệu')
    expect(screen.getByRole('link', { name: 'Mở mục xuất dữ liệu' })).toHaveAttribute(
      'href',
      '/account/delete?settings=accounts',
    )
    const timeline = screen.getByTestId('deletion-timeline')
    expect(timeline).toHaveTextContent(`gửi thư xác nhận tới ${verifiedAccount.email}`)
    expect(timeline).toHaveTextContent('Trong 30 ngày, tới')
    expect(timeline).toHaveTextContent('Tài liệu, tệp gốc và nội dung đã trích')
    expect(timeline).toHaveTextContent('chỉ còn mã đã ẩn danh')
  })

  it('chưa đánh dấu đã hiểu và chưa nhập mật khẩu thì báo lỗi, không gửi yêu cầu', async () => {
    acceptDeletion(scheduled)
    const { user } = renderFlow()

    await user.click(await screen.findByRole('button', { name: 'Xóa tài khoản' }))

    expect(screen.getByText('Hãy đánh dấu để xác nhận bạn đã hiểu.')).toBeInTheDocument()
    expect(screen.getByText('Hãy nhập mật khẩu.')).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: /Tôi hiểu tài khoản/ })).toHaveFocus()
    expect(requests).toEqual([])
  })

  it('TC-01: xác nhận đúng mật khẩu thì tải lại ở trang đã lên lịch xóa kèm hạn xóa', async () => {
    acceptDeletion(scheduled)
    const { user } = renderFlow()

    await user.click(await screen.findByRole('checkbox', { name: /Tôi hiểu tài khoản/ }))
    await user.type(screen.getByLabelText('Mật khẩu hiện tại'), 'dung-mat-khau-2026')
    await user.click(screen.getByRole('button', { name: 'Xóa tài khoản' }))

    await waitFor(() =>
      expect(reloadTo).toHaveBeenCalledWith(`/account/deletion-scheduled?until=${encodeURIComponent(SCHEDULED_FOR)}`),
    )
    expect(requests).toEqual([{ password: 'dung-mat-khau-2026' }])
  })

  it('TC-04: mật khẩu sai thì báo ngay dưới ô, xóa ô và không xóa tài khoản', async () => {
    acceptDeletion(() => apiError(400, 'CURRENT_PASSWORD_INVALID', 'password', { remainingAttempts: 2 }))
    const { user } = renderFlow()

    await user.click(await screen.findByRole('checkbox', { name: /Tôi hiểu tài khoản/ }))
    const password = screen.getByLabelText('Mật khẩu hiện tại')
    await user.type(password, 'doan-mo-mat-khau')
    await user.click(screen.getByRole('button', { name: 'Xóa tài khoản' }))

    expect(await screen.findByText('Mật khẩu chưa đúng. Còn 2 lần thử trước khi tạm khóa đăng nhập.')).toBeInTheDocument()
    expect(password).toHaveValue('')
    await waitFor(() => expect(password).toHaveFocus())
    expect(reloadTo).not.toHaveBeenCalled()
  })

  it('nhập sai quá số lần thì tạm khóa và không bấm xóa được', async () => {
    acceptDeletion(() => apiError(423, 'ACCOUNT_LOCKED', undefined, { retryAfterSeconds: 900 }))
    const { user } = renderFlow()

    await user.click(await screen.findByRole('checkbox', { name: /Tôi hiểu tài khoản/ }))
    await user.type(screen.getByLabelText('Mật khẩu hiện tại'), 'sai-lan-thu-nam')
    await user.click(screen.getByRole('button', { name: 'Xóa tài khoản' }))

    expect(await screen.findByTestId('lockout-notice')).toHaveTextContent('Tạm khóa xác nhận mật khẩu')
    expect(screen.getByRole('button', { name: 'Xóa tài khoản' })).toBeDisabled()
  })

  it('tài khoản chỉ dùng Google: không có ô mật khẩu, quá hạn xác nhận thì đăng nhập lại rồi quay về trang này', async () => {
    acceptDeletion(() => apiError(403, 'REAUTHENTICATION_REQUIRED', undefined, { hasPassword: false }))
    server.use(http.post('*/api/v1/auth/logout', () => new HttpResponse(null, { status: 204 })))
    const { user, router } = renderFlow({ hasPassword: false })

    await user.click(await screen.findByRole('checkbox', { name: /Tôi hiểu tài khoản/ }))
    expect(screen.queryByLabelText('Mật khẩu hiện tại')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Xóa tài khoản' }))
    expect(requests).toEqual([{}])

    await user.click(await screen.findByRole('button', { name: 'Đăng nhập lại để xác nhận' }))
    expect(await screen.findByText('trang đăng nhập')).toBeInTheDocument()
    expect(router.state.location.state).toEqual({ from: routes.deleteAccount, reason: 'REAUTHENTICATE' })
  })

  it('đổi ý thì "Giữ tài khoản" đưa về trang chủ', async () => {
    const { user } = renderFlow()

    const form = await screen.findByRole('form', { name: 'Xác nhận xóa tài khoản' })
    await user.click(within(form).getByRole('link', { name: 'Giữ tài khoản' }))

    expect(await screen.findByText('trang chủ')).toBeInTheDocument()
  })
})
