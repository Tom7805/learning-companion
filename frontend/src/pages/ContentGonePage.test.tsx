import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { routes } from '@/app/routes'
import { SidePanel } from '@/layouts/SidePanel'
import { renderWithProviders } from '@/test/renderWithProviders'
import { ContentGonePage } from './ContentGonePage'

describe('ContentGonePage', () => {
  it('TC-03: nói rõ nội dung không còn, cho về trang chính hoặc xem lại thông báo', async () => {
    const { user } = renderWithProviders(
      <>
        <ContentGonePage />
        <SidePanel />
      </>,
      { path: routes.contentGone },
    )

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Nội dung này không còn nữa')
    expect(screen.getByText(/đã bị xóa, đã hết hạn hoặc đã được thay bằng bản mới hơn/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Về trang chính' })).toHaveAttribute('href', routes.home)

    await user.click(screen.getByRole('button', { name: 'Xem thông báo' }))

    expect(screen.getByTestId('side-panel')).toHaveAccessibleName('Thông báo')
    expect(await screen.findByText('Chưa có thông báo nào')).toBeInTheDocument()
  })
})
