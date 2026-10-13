import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { routes } from '@/app/routes'
import { renderWithProviders } from '@/test/renderWithProviders'
import { DeletionScheduledPage } from './DeletionScheduledPage'

function renderPage(search: string) {
  return renderWithProviders(<DeletionScheduledPage />, {
    path: routes.deletionScheduled,
    initialEntries: [`${routes.deletionScheduled}${search}`],
  })
}

describe('DeletionScheduledPage', () => {
  it('TC-01: nêu hạn xóa vĩnh viễn, cách đổi ý và lối về đăng nhập', async () => {
    renderPage('?until=2026-11-12T03%3A00%3A00Z')

    expect(await screen.findByRole('heading', { level: 1, name: 'Đã lên lịch xóa tài khoản' })).toBeInTheDocument()
    expect(screen.getByTestId('deletion-scheduled')).toHaveTextContent(/sẽ bị xóa vĩnh viễn vào .*12 thg 11, 2026/)
    expect(screen.getByText(/Đăng nhập lại trước hạn trên/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Về màn hình đăng nhập' })).toHaveAttribute('href', routes.login)
  })

  it('tham số hạn xóa hỏng thì chỉ nói sau 30 ngày', async () => {
    renderPage('?until=khong-phai-ngay')

    expect(await screen.findByTestId('deletion-scheduled')).toHaveTextContent('sẽ bị xóa vĩnh viễn sau 30 ngày')
  })
})
