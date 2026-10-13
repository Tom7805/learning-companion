import { screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ProtectedRoute } from '@/app/ProtectedRoute'
import { reloadTo } from '@/shared/lib/navigation'
import { verifiedAccount } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'

vi.mock('@/shared/lib/navigation', () => ({ reloadTo: vi.fn() }))

const pendingAccount = {
  ...verifiedAccount,
  status: 'PENDING_DELETION',
  deletionScheduledFor: '2026-11-02T03:00:00Z',
}

/** Phiên trả về tài khoản chờ xóa cho tới khi người học hủy yêu cầu. */
function renderApp() {
  let cancelled = false
  server.use(
    http.get('*/api/v1/auth/session', () =>
      HttpResponse.json({ authenticated: true, account: cancelled ? verifiedAccount : pendingAccount }),
    ),
    http.delete('*/api/v1/accounts/:id/deletion', () => {
      cancelled = true
      return HttpResponse.json({ pending: false, graceDays: 30 })
    }),
  )
  const utils = renderWithProviders(
    <Routes>
      <Route element={<ProtectedRoute />}>
        <Route index element={<p>ứng dụng học tập</p>} />
      </Route>
    </Routes>,
    { path: '/*', initialEntries: ['/'] },
  )
  return { ...utils, wasCancelled: () => cancelled }
}

describe('PendingDeletionNotice', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-10-23T03:00:00Z'))
    vi.mocked(reloadTo).mockClear()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('TC-02: đăng nhập lại khi đang chờ xóa thì được hỏi có hủy không, kèm hạn xóa và số ngày còn lại', async () => {
    renderApp()

    expect(await screen.findByRole('heading', { level: 1, name: 'Tài khoản đang chờ xóa' })).toBeInTheDocument()
    expect(screen.getByTestId('pending-deletion-date')).toHaveTextContent('sẽ bị xóa vĩnh viễn vào')
    expect(screen.getByText('Còn 10 ngày để đổi ý')).toBeInTheDocument()
    expect(screen.queryByText('ứng dụng học tập')).not.toBeInTheDocument()
  })

  it('TC-02: hủy yêu cầu xóa thì báo tài khoản hoạt động lại rồi vào học tiếp với nguyên dữ liệu', async () => {
    const { user, wasCancelled } = renderApp()

    await user.click(await screen.findByRole('button', { name: /Hủy yêu cầu xóa và học tiếp/ }))

    expect(await screen.findByRole('heading', { name: 'Tài khoản đã hoạt động lại' })).toBeInTheDocument()
    expect(wasCancelled()).toBe(true)
    await user.click(screen.getByRole('button', { name: /Vào học tiếp/ }))
    expect(await screen.findByText('ứng dụng học tập')).toBeInTheDocument()
  })

  it('vẫn muốn xóa thì đăng xuất và giữ nguyên hạn xóa', async () => {
    let loggedOut = false
    server.use(
      http.post('*/api/v1/auth/logout', () => {
        loggedOut = true
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const { user, wasCancelled } = renderApp()

    await user.click(await screen.findByRole('button', { name: 'Vẫn xóa, đăng xuất' }))

    await waitFor(() => expect(reloadTo).toHaveBeenCalledWith('/login?loggedOut=1'))
    expect(loggedOut).toBe(true)
    expect(wasCancelled()).toBe(false)
  })
})
