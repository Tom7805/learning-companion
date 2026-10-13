import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { Route, Routes } from 'react-router'
import { ProtectedRoute } from '@/app/ProtectedRoute'
import { routes } from '@/app/routes'
import { downloadFromUrl } from '@/shared/lib/download'
import { apiError, readyExport, verifiedAccount } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import type { DataExport } from '../types'
import { DataExportDownloadPage } from './DataExportDownloadPage'

vi.mock('@/shared/lib/download', () => ({ downloadFromUrl: vi.fn(), saveBlob: vi.fn() }))

const FILE_URL = '/api/v1/exports/files/chu-ky-thu'

/** Ghi lại thân các lần xin đường dẫn tải để kiểm có gửi mật khẩu hay không. */
let downloads: Record<string, unknown>[] = []

function renderPage(data: DataExport | null, { hasPassword = true } = {}) {
  server.use(
    http.get('*/api/v1/auth/session', () =>
      HttpResponse.json({ authenticated: true, account: { ...verifiedAccount, hasPassword } }),
    ),
    http.get('*/api/v1/exports/:id', () => (data ? HttpResponse.json(data) : apiError(404, 'NOT_FOUND'))),
  )
  // Trang nằm trong ProtectedRoute như ứng dụng thật, để mất phiên thì chuyển sang đăng nhập kèm lý do.
  return renderWithProviders(
    <Routes>
      <Route element={<ProtectedRoute />}>
        <Route path=":id" element={<DataExportDownloadPage />} />
      </Route>
    </Routes>,
    {
      path: '/exports/*',
      initialEntries: [`/exports/${readyExport.id}`],
      extraRoutes: [{ path: routes.login, element: <p>trang đăng nhập</p> }],
    },
  )
}

function acceptDownloads(respond: (body: Record<string, unknown>) => Response | Promise<Response>) {
  server.use(
    http.post('*/api/v1/exports/:id/download', async ({ request }) => {
      const body = (await request.json()) as Record<string, unknown>
      downloads.push(body)
      return respond(body)
    }),
  )
}

const link = () => HttpResponse.json({ url: FILE_URL, expiresAt: '2026-10-13T03:05:00Z' })

describe('DataExportDownloadPage', () => {
  beforeEach(() => {
    downloads = []
    vi.mocked(downloadFromUrl).mockClear()
  })

  it('vừa đăng nhập thì tải được ngay, gói nêu đủ các phần dữ liệu', async () => {
    acceptDownloads(link)
    const { user } = renderPage(readyExport)

    expect(await screen.findByRole('heading', { level: 1, name: 'Tải gói dữ liệu của bạn' })).toBeInTheDocument()
    const sections = screen.getByTestId('export-sections')
    expect(within(sections).getAllByRole('listitem')).toHaveLength(4)
    expect(sections).toHaveTextContent('Thư viện tài liệu5 tệp')
    expect(screen.getByText('Bạn vừa đăng nhập nên tải được ngay.')).toBeInTheDocument()
    expect(screen.queryByLabelText('Mật khẩu')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /Tải gói dữ liệu/ }))

    expect(await screen.findByTestId('export-downloading')).toHaveTextContent(readyExport.fileName!)
    expect(downloadFromUrl).toHaveBeenCalledWith(FILE_URL)
    expect(downloads).toEqual([{}])
    expect(screen.getByRole('link', { name: 'Không thấy tệp? Bấm để tải lại' })).toHaveAttribute('href', FILE_URL)
  })

  it('mỗi lần tải sau năm phút phải nhập lại mật khẩu; sai thì báo và không tải', async () => {
    acceptDownloads((body) =>
      body.password === 'dung-mat-khau-2026'
        ? link()
        : apiError(400, 'CURRENT_PASSWORD_INVALID', 'password', { remainingAttempts: 2 }),
    )
    const { user } = renderPage({ ...readyExport, reauthenticationRequired: true })

    const password = await screen.findByLabelText('Mật khẩu')
    expect(screen.getByText(/mỗi lần tải bạn cần nhập lại mật khẩu/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /Tải gói dữ liệu/ }))
    expect(await screen.findByText('Hãy nhập mật khẩu.')).toBeInTheDocument()
    expect(downloads).toEqual([])

    await user.type(password, 'sai-mat-khau')
    await user.click(screen.getByRole('button', { name: /Tải gói dữ liệu/ }))
    expect(await screen.findByText(/Mật khẩu chưa đúng\. Còn 2 lần thử/)).toBeInTheDocument()
    expect(password).toHaveValue('')
    await waitFor(() => expect(password).toHaveFocus())
    expect(downloadFromUrl).not.toHaveBeenCalled()

    await user.type(password, 'dung-mat-khau-2026')
    await user.click(screen.getByRole('button', { name: /Tải gói dữ liệu/ }))
    await waitFor(() => expect(downloadFromUrl).toHaveBeenCalledWith(FILE_URL))
    expect(downloads.at(-1)).toEqual({ password: 'dung-mat-khau-2026' })
  })

  it('máy chủ báo cần xác nhận lại thì hiện ô mật khẩu', async () => {
    acceptDownloads(() => apiError(403, 'REAUTHENTICATION_REQUIRED', undefined, { hasPassword: true }))
    const { user } = renderPage(readyExport)

    await user.click(await screen.findByRole('button', { name: /Tải gói dữ liệu/ }))

    expect(await screen.findByLabelText('Mật khẩu')).toBeInTheDocument()
    expect(downloadFromUrl).not.toHaveBeenCalled()
  })

  it('nhập sai quá số lần thì tạm khóa và hiện thời gian chờ', async () => {
    acceptDownloads(() => apiError(423, 'ACCOUNT_LOCKED', undefined, { retryAfterSeconds: 900 }))
    const { user } = renderPage({ ...readyExport, reauthenticationRequired: true })

    await user.type(await screen.findByLabelText('Mật khẩu'), 'sai-lan-thu-nam')
    await user.click(screen.getByRole('button', { name: /Tải gói dữ liệu/ }))

    const notice = await screen.findByTestId('lockout-notice')
    expect(notice).toHaveTextContent('Tạm khóa xác nhận mật khẩu')
    expect(screen.getByTestId('lockout-countdown')).toHaveTextContent(/1[45]:\d\d/)
    expect(screen.getByRole('button', { name: /Tải gói dữ liệu/ })).toBeDisabled()
  })

  it('tài khoản chỉ đăng nhập bằng Google thì được mời đăng nhập lại rồi quay về trang tải', async () => {
    let loggedOut = false
    server.use(
      http.post('*/api/v1/auth/logout', () => {
        loggedOut = true
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const { user, router } = renderPage({ ...readyExport, reauthenticationRequired: true }, { hasPassword: false })

    expect(await screen.findByText(/mỗi lần tải bạn cần đăng nhập lại/)).toBeInTheDocument()
    expect(screen.queryByLabelText('Mật khẩu')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Đăng nhập lại để tải' }))

    expect(await screen.findByText('trang đăng nhập')).toBeInTheDocument()
    expect(loggedOut).toBe(true)
    expect(router.state.location.pathname).toBe(routes.login)
    expect(router.state.location.state).toEqual({ from: `/exports/${readyExport.id}`, reason: 'REAUTHENTICATE' })
  })

  it('TC-04: gói của người khác hoặc mã không tồn tại hiện "không tìm thấy"', async () => {
    renderPage(null)

    const screenNotFound = await screen.findByTestId('export-not-found')
    expect(within(screenNotFound).getByRole('heading', { name: 'Không tìm thấy gói dữ liệu' })).toBeInTheDocument()
    expect(screenNotFound).toHaveTextContent('không thuộc tài khoản bạn đang đăng nhập')
    expect(screen.queryByRole('button', { name: /Tải gói dữ liệu/ })).not.toBeInTheDocument()
  })

  it('TC-03: gói quá bảy ngày báo hết hạn và yêu cầu xuất lại được ngay tại trang', async () => {
    server.use(
      http.post('*/api/v1/exports', () =>
        HttpResponse.json(
          { latest: { id: 'moi', status: 'QUEUED', requestedAt: '2026-10-20T03:00:00Z', sections: [] }, canRequest: false },
          { status: 202 },
        ),
      ),
    )
    const { user } = renderPage({ ...readyExport, status: 'EXPIRED', reauthenticationRequired: null })

    const expired = await screen.findByTestId('export-expired')
    expect(within(expired).getByRole('heading', { name: 'Gói dữ liệu đã hết hạn' })).toBeInTheDocument()
    expect(expired).toHaveTextContent('chỉ dùng được 7 ngày')

    await user.click(within(expired).getByRole('button', { name: /Yêu cầu xuất lại/ }))

    const requested = await screen.findByTestId('export-requested')
    expect(requested).toHaveTextContent(`gửi thư có đường dẫn tải mới tới ${verifiedAccount.email}`)
    expect(within(requested).getByRole('link', { name: 'Xem trong cài đặt tài khoản' })).toHaveAttribute(
      'href',
      `/exports/${readyExport.id}?settings=accounts`,
    )
  })

  it('yêu cầu xuất lại trong cùng ngày thì báo giới hạn mỗi ngày', async () => {
    server.use(
      http.post('*/api/v1/exports', () =>
        apiError(429, 'EXPORT_DAILY_LIMIT', undefined, { nextAvailableAt: '2026-10-21T03:00:00Z' }),
      ),
    )
    const { user } = renderPage({ ...readyExport, status: 'EXPIRED' })

    await user.click(await screen.findByRole('button', { name: /Yêu cầu xuất lại/ }))

    expect(await screen.findByTestId('export-request-error')).toHaveTextContent(
      'Mỗi ngày chỉ yêu cầu xuất dữ liệu được một lần. Bạn có thể yêu cầu lại sau',
    )
  })

  it('gói đang được chuẩn bị thì báo chờ và chỉ sang cài đặt', async () => {
    renderPage({ ...readyExport, status: 'RUNNING', sizeBytes: null, fileCount: null })

    const preparing = await screen.findByTestId('export-preparing')
    expect(preparing).toHaveTextContent('Gói dữ liệu đang được chuẩn bị')
  })
})
