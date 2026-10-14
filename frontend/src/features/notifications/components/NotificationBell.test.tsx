import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import '@/shared/i18n'
import { NotificationBell } from './NotificationBell'

describe('NotificationBell', () => {
  it('huy hiệu số chưa đọc, tên đọc được kèm số cho trình đọc màn hình', async () => {
    const onToggle = vi.fn()
    render(<NotificationBell unreadCount={2} expanded={false} onToggle={onToggle} />)

    const bell = screen.getByRole('button', { name: 'Thông báo, 2 chưa đọc' })
    expect(bell).toHaveAttribute('aria-expanded', 'false')
    expect(screen.getByTestId('notification-badge')).toHaveTextContent('2')
    await userEvent.click(bell)
    expect(onToggle).toHaveBeenCalledOnce()
  })

  it('không có gì chưa đọc thì không có huy hiệu; quá 99 thì hiện 99+', () => {
    const { rerender } = render(<NotificationBell unreadCount={0} expanded onToggle={() => undefined} />)
    expect(screen.getByRole('button', { name: 'Thông báo' })).toHaveAttribute('aria-expanded', 'true')
    expect(screen.queryByTestId('notification-badge')).not.toBeInTheDocument()

    rerender(<NotificationBell unreadCount={250} expanded={false} onToggle={() => undefined} />)
    expect(screen.getByTestId('notification-badge')).toHaveTextContent('99+')
    expect(screen.getByRole('button', { name: 'Thông báo, 250 chưa đọc' })).toBeInTheDocument()
  })
})
