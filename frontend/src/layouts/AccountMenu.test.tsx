import { screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '@/test/renderWithProviders'
import { AccountMenu } from './AccountMenu'

type Placement = 'sidebar' | 'sidebar-collapsed' | 'topbar'

function renderMenu({ installable = false, placement = 'sidebar' }: { installable?: boolean; placement?: Placement } = {}) {
  const handlers = {
    onSettings: vi.fn(),
    onAppearance: vi.fn(),
    onHelp: vi.fn(),
    onInstall: vi.fn(),
    onLogout: vi.fn(),
  }
  const utils = renderWithProviders(
    <AccountMenu
      placement={placement}
      name="Lan Anh"
      email="lan.anh@example.com"
      {...handlers}
      onInstall={installable ? handlers.onInstall : undefined}
    />,
  )
  return { ...utils, ...handlers }
}

const trigger = () => screen.getByRole('button', { name: 'Trình đơn tài khoản của Lan Anh' })

describe('AccountMenu', () => {
  it('nút ở thanh bên hiện tên, thư và mở trình đơn lên trên với mục đầu nhận tiêu điểm', async () => {
    const { user, onSettings } = renderMenu()
    expect(trigger()).toHaveTextContent('Lan Anh')
    expect(trigger()).toHaveTextContent('lan.anh@example.com')

    await user.click(trigger())

    expect(trigger()).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByRole('menu')).toHaveTextContent('lan.anh@example.com')
    expect(screen.getAllByRole('menuitem').map((item) => item.textContent)).toEqual([
      'Cài đặt',
      'Giao diện',
      'Trợ giúp',
      'Đăng xuất',
    ])
    expect(screen.getByRole('menuitem', { name: 'Cài đặt' })).toHaveFocus()
    await user.click(screen.getByRole('menuitem', { name: 'Cài đặt' }))
    expect(onSettings).toHaveBeenCalledOnce()
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })

  it('mỗi mục gọi đúng hành động của nó', async () => {
    const { user, onAppearance, onHelp, onLogout } = renderMenu()

    await user.click(trigger())
    await user.click(screen.getByRole('menuitem', { name: 'Giao diện' }))
    await user.click(trigger())
    await user.click(screen.getByRole('menuitem', { name: 'Trợ giúp' }))
    await user.click(trigger())
    await user.click(screen.getByRole('menuitem', { name: 'Đăng xuất' }))

    expect(onAppearance).toHaveBeenCalledOnce()
    expect(onHelp).toHaveBeenCalledOnce()
    expect(onLogout).toHaveBeenCalledOnce()
  })

  it('chỉ có mục Cài ứng dụng khi trình duyệt cho cài', async () => {
    const { user, onInstall } = renderMenu({ installable: true })

    await user.click(trigger())
    await user.click(screen.getByRole('menuitem', { name: 'Cài ứng dụng' }))

    expect(onInstall).toHaveBeenCalledOnce()
  })

  it('dùng phím mũi tên, Home, End chuyển mục; Esc đóng và trả tiêu điểm về nút', async () => {
    const { user, onLogout } = renderMenu()
    trigger().focus()

    await user.keyboard('{Enter}')
    await user.keyboard('{ArrowDown}')
    expect(screen.getByRole('menuitem', { name: 'Giao diện' })).toHaveFocus()
    await user.keyboard('{End}')
    expect(screen.getByRole('menuitem', { name: 'Đăng xuất' })).toHaveFocus()
    await user.keyboard('{ArrowDown}')
    expect(screen.getByRole('menuitem', { name: 'Cài đặt' })).toHaveFocus()
    await user.keyboard('{ArrowUp}')
    expect(screen.getByRole('menuitem', { name: 'Đăng xuất' })).toHaveFocus()
    await user.keyboard('{Home}')
    expect(screen.getByRole('menuitem', { name: 'Cài đặt' })).toHaveFocus()
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
    expect(trigger()).toHaveFocus()

    await user.keyboard('{Enter}')
    await user.keyboard('{ArrowUp}{Enter}')
    expect(onLogout).toHaveBeenCalledOnce()
  })

  it('Tab hoặc bấm ra ngoài thì đóng trình đơn', async () => {
    const { user } = renderMenu()
    await user.click(trigger())
    await user.click(document.body)
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()

    await user.click(trigger())
    await user.tab()
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })

  it('ở thanh trên của điện thoại chỉ hiện ảnh đại diện', () => {
    renderMenu({ placement: 'topbar' })

    expect(trigger()).not.toHaveTextContent('lan.anh@example.com')
    expect(screen.getByTestId('account-menu-topbar')).toBe(trigger())
  })
})
