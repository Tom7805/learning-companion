import { screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '@/test/renderWithProviders'
import { AccountMenu } from './AccountMenu'

function renderMenu() {
  const onSettings = vi.fn()
  const onLogout = vi.fn()
  const utils = renderWithProviders(
    <AccountMenu placement="sidebar" name="Lan Anh" email="lan.anh@example.com" onSettings={onSettings} onLogout={onLogout} />,
  )
  return { ...utils, onSettings, onLogout }
}

describe('AccountMenu', () => {
  it('mở trình đơn, mục đầu nhận tiêu điểm và chọn Cài đặt', async () => {
    const { user, onSettings } = renderMenu()
    const trigger = screen.getByRole('button', { name: 'Trình đơn tài khoản của Lan Anh' })

    await user.click(trigger)

    expect(trigger).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByRole('menu')).toHaveTextContent('lan.anh@example.com')
    expect(screen.getByRole('menuitem', { name: 'Cài đặt' })).toHaveFocus()
    await user.click(screen.getByRole('menuitem', { name: 'Cài đặt' }))
    expect(onSettings).toHaveBeenCalledOnce()
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })

  it('dùng phím mũi tên chuyển mục, Esc đóng và trả tiêu điểm về nút', async () => {
    const { user, onLogout } = renderMenu()
    const trigger = screen.getByRole('button', { name: 'Trình đơn tài khoản của Lan Anh' })
    trigger.focus()

    await user.keyboard('{Enter}')
    await user.keyboard('{ArrowDown}')
    expect(screen.getByRole('menuitem', { name: 'Đăng xuất' })).toHaveFocus()
    await user.keyboard('{ArrowDown}')
    expect(screen.getByRole('menuitem', { name: 'Cài đặt' })).toHaveFocus()
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
    expect(trigger).toHaveFocus()

    await user.keyboard('{Enter}')
    await user.keyboard('{ArrowUp}{Enter}')
    expect(onLogout).toHaveBeenCalledOnce()
  })

  it('bấm ra ngoài thì đóng trình đơn', async () => {
    const { user } = renderMenu()
    await user.click(screen.getByRole('button', { name: 'Trình đơn tài khoản của Lan Anh' }))

    await user.click(document.body)

    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })
})
