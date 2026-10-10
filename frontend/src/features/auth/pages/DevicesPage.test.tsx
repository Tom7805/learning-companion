import { screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { routes } from '@/app/routes'
import { deviceSessions } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import { DevicesPage } from './DevicesPage'

function renderPage() {
  return renderWithProviders(<DevicesPage />, { path: routes.devices })
}

describe('DevicesPage', () => {
  it('liệt kê thiết bị, thiết bị đang dùng đứng đầu kèm vị trí và trạng thái', async () => {
    renderPage()

    const rows = await screen.findAllByTestId('device-row')
    expect(rows).toHaveLength(2)
    expect(rows[0]).toHaveAttribute('data-current', 'true')
    expect(within(rows[0]).getByText('Chrome trên Android')).toBeInTheDocument()
    expect(within(rows[0]).getByText('Thiết bị này')).toBeInTheDocument()
    expect(within(rows[0]).getByText('Mạng nội bộ')).toBeInTheDocument()
    expect(within(rows[0]).getByText('Đang hoạt động')).toBeInTheDocument()
    expect(within(rows[0]).getByText(/Ghi nhớ 30 ngày/)).toBeInTheDocument()
    expect(within(rows[1]).getByText('Chrome trên Windows')).toBeInTheDocument()
    expect(within(rows[1]).getByText('Hà Nội, VN')).toBeInTheDocument()
    expect(within(rows[1]).getByText(/trước/)).toBeInTheDocument()
  })

  it('đăng xuất một thiết bị khác sau khi xác nhận', async () => {
    let deleted: string | undefined
    server.use(
      http.delete('*/api/v1/auth/sessions/:id', ({ params }) => {
        deleted = params.id as string
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const { user } = renderPage()

    await user.click(await screen.findByRole('button', { name: 'Đăng xuất Chrome trên Windows' }))
    const dialog = await screen.findByRole('dialog', { name: 'Đăng xuất Chrome trên Windows?' })
    expect(within(dialog).getByRole('button', { name: 'Hủy' })).toHaveFocus()
    await user.click(within(dialog).getByRole('button', { name: 'Đăng xuất' }))

    expect(await screen.findByTestId('devices-notice')).toHaveTextContent('Đã đăng xuất Chrome trên Windows.')
    expect(deleted).toBe('phien-thu-vien')
  })

  it('hủy xác nhận thì không đăng xuất gì', async () => {
    let calls = 0
    server.use(
      http.delete('*/api/v1/auth/sessions/:id', () => {
        calls += 1
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const { user } = renderPage()

    await user.click(await screen.findByRole('button', { name: 'Đăng xuất Chrome trên Windows' }))
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Hủy' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(calls).toBe(0)
  })

  it('đăng xuất mọi thiết bị khác', async () => {
    const { user } = renderPage()
    await screen.findAllByTestId('device-row')

    await user.click(screen.getByTestId('revoke-others'))
    const dialog = await screen.findByRole('dialog', { name: 'Đăng xuất mọi thiết bị khác?' })
    expect(dialog).toHaveTextContent('1 thiết bị khác sẽ phải đăng nhập lại')
    await user.click(within(dialog).getByRole('button', { name: 'Đăng xuất' }))

    expect(await screen.findByTestId('devices-notice')).toHaveTextContent('Đã đăng xuất 1 thiết bị khác.')
  })

  it('chỉ có thiết bị này thì không cho đăng xuất thiết bị khác', async () => {
    server.use(http.get('*/api/v1/auth/sessions', () => HttpResponse.json([deviceSessions[1]])))
    renderPage()

    expect(await screen.findByText('Chỉ có thiết bị này đang đăng nhập.')).toBeInTheDocument()
    expect(screen.getByTestId('revoke-others')).toBeDisabled()
  })

  it('tải thất bại thì cho thử lại', async () => {
    server.use(http.get('*/api/v1/auth/sessions', () => HttpResponse.error()))
    renderPage()
    expect(await screen.findByText('Chưa tải được danh sách thiết bị.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Thử lại' })).toBeInTheDocument()
  })
})
