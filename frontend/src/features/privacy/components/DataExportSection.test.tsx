import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { routes } from '@/app/routes'
import { apiError, readyExport, verifiedAccount } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import type { DataExportOverview } from '../types'
import { DataExportSection } from './DataExportSection'

function renderSection(overview: DataExportOverview) {
  server.use(
    http.get('*/api/v1/auth/session', () => HttpResponse.json({ authenticated: true, account: verifiedAccount })),
    http.get('*/api/v1/exports', () => HttpResponse.json(overview)),
  )
  return renderWithProviders(<DataExportSection onNavigate={() => undefined} />, {
    path: '/',
    extraRoutes: [{ path: routes.dataExport, element: <p>trang tải gói</p> }],
  })
}

describe('DataExportSection', () => {
  it('nêu rõ gói gồm gì, định dạng mở và không tính vào hạn mức AI', async () => {
    renderSection({ canRequest: true })

    const section = await screen.findByRole('region', { name: 'Xuất toàn bộ dữ liệu' })
    expect(section).toHaveTextContent('Tài liệu kèm tệp gốc, theo thư mục thư viện')
    expect(section).toHaveTextContent('Lịch sử đồng ý theo từng mục đích')
    expect(section).toHaveTextContent('JSON, CSV, Markdown và tệp gốc')
    expect(section).toHaveTextContent('Không tính vào hạn mức AI')
    expect(section).toHaveTextContent('Mỗi ngày một lần')
  })

  it('TC-01: yêu cầu xuất thì chuyển sang đang chuẩn bị và báo sẽ gửi thư', async () => {
    let requested = 0
    server.use(
      http.post('*/api/v1/exports', () => {
        requested += 1
        return HttpResponse.json(
          {
            latest: { id: 'moi', status: 'QUEUED', requestedAt: '2026-10-13T03:00:00Z', sections: [] },
            canRequest: false,
            nextAvailableAt: '2026-10-14T03:00:00Z',
          },
          { status: 202 },
        )
      }),
    )
    const { user } = renderSection({ canRequest: true })

    await user.click(await screen.findByRole('button', { name: /Yêu cầu xuất dữ liệu/ }))

    expect(await screen.findByTestId('export-progress')).toHaveTextContent('Đang chuẩn bị gói dữ liệu…')
    expect(screen.getByTestId('export-notice')).toHaveTextContent(`gửi thư tới ${verifiedAccount.email}`)
    expect(screen.queryByRole('button', { name: /Yêu cầu xuất/ })).not.toBeInTheDocument()
    await waitFor(() => expect(screen.getByTestId('export-progress')).toHaveFocus())
    expect(requested).toBe(1)
  })

  it('gói sẵn sàng thì có nút sang trang tải, kèm số tệp, dung lượng và hạn tải', async () => {
    const { user } = renderSection({
      latest: readyExport,
      canRequest: false,
      nextAvailableAt: '2026-10-13T03:00:00Z',
    })

    const ready = await screen.findByTestId('export-ready')
    expect(ready).toHaveTextContent('Gói dữ liệu đã sẵn sàng')
    expect(ready).toHaveTextContent('12 tệp')
    expect(ready).toHaveTextContent('47,5 KB')
    expect(ready).toHaveTextContent('Tải được tới')
    expect(screen.getByTestId('export-next')).toHaveTextContent('Bạn có thể yêu cầu lại sau')

    await user.click(within(ready).getByRole('link', { name: 'Tải gói dữ liệu' }))
    expect(await screen.findByText('trang tải gói')).toBeInTheDocument()
  })

  it('đã yêu cầu trong ngày thì báo thời điểm được yêu cầu lại', async () => {
    server.use(
      http.post('*/api/v1/exports', () =>
        apiError(429, 'EXPORT_DAILY_LIMIT', undefined, { nextAvailableAt: '2026-10-14T03:00:00Z' }),
      ),
    )
    const { user } = renderSection({ canRequest: true })

    await user.click(await screen.findByRole('button', { name: /Yêu cầu xuất dữ liệu/ }))

    const notice = await screen.findByTestId('export-notice')
    expect(notice).toHaveTextContent('Mỗi ngày chỉ yêu cầu xuất dữ liệu được một lần.')
    expect(notice).toHaveTextContent('Bạn có thể yêu cầu lại sau')
  })

  it('lần trước thất bại thì báo không tính vào giới hạn và cho yêu cầu lại ngay', async () => {
    renderSection({
      latest: { id: 'loi', status: 'FAILED', requestedAt: '2026-10-13T03:00:00Z', sections: [] },
      canRequest: true,
    })

    expect(await screen.findByTestId('export-failed')).toHaveTextContent('không tính vào giới hạn mỗi ngày')
    expect(screen.getByRole('button', { name: /Yêu cầu xuất lại/ })).toBeEnabled()
  })

  it('TC-03: gói đã hết hạn thì báo đã xóa và cho yêu cầu xuất lại', async () => {
    renderSection({
      latest: { ...readyExport, status: 'EXPIRED', sizeBytes: null, fileCount: null },
      canRequest: true,
    })

    expect(await screen.findByTestId('export-expired')).toHaveTextContent('đã hết hạn tải')
    expect(screen.getByRole('button', { name: /Yêu cầu xuất lại/ })).toBeInTheDocument()
  })
})
