import { screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { routes } from '@/app/routes'
import { recentStore } from '@/app/store/recentStore'
import { renderWithProviders } from '@/test/renderWithProviders'
import { Sidebar } from './Sidebar'

const ACCOUNT = 'account-lan'

function renderSidebar(variant: 'expanded' | 'collapsed' | 'drawer', path: string = routes.library) {
  const handlers = {
    onToggleCollapsed: vi.fn(),
    onClose: vi.fn(),
    onNavigate: vi.fn(),
    onQuickOpen: vi.fn(),
    onSettings: vi.fn(),
    onAppearance: vi.fn(),
    onHelp: vi.fn(),
    onLogout: vi.fn(),
  }
  const ui = <Sidebar variant={variant} accountId={ACCOUNT} name="Lan Anh" email="lan.anh@example.com" {...handlers} />
  const utils = renderWithProviders(ui, {
    path,
    extraRoutes: [routes.home, routes.library, '/library/:id']
      .filter((other) => other !== path)
      .map((other) => ({ path: other, element: ui })),
  })
  return { ...utils, ...handlers }
}

describe('Sidebar', () => {
  it('mở rộng: nút Mở nhanh ở trên cùng, nhóm mục chức năng, mục gần đây và tài khoản ở đáy', async () => {
    const { user, onQuickOpen } = renderSidebar('expanded')

    const nav = screen.getByRole('navigation', { name: 'Chức năng' })
    expect(within(nav).getAllByRole('link').map((link) => link.textContent)).toEqual(['Trang chính', 'Thư viện'])
    expect(within(nav).getByRole('link', { name: 'Thư viện' })).toHaveAttribute('aria-current', 'page')
    expect(within(nav).getByRole('link', { name: 'Trang chính' })).not.toHaveAttribute('aria-current')
    expect(screen.getByTestId('sidebar-recent')).toHaveTextContent('Tài liệu bạn mở sẽ hiện ở đây')
    expect(screen.getByRole('button', { name: 'Trình đơn tài khoản của Lan Anh' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /Mở nhanh/ }))
    expect(onQuickOpen).toHaveBeenCalledOnce()
  })

  it('nút thu gọn báo trạng thái cho trình đọc màn hình và gọi đổi trạng thái', async () => {
    const { user, onToggleCollapsed } = renderSidebar('expanded')

    const toggle = screen.getByRole('button', { name: 'Thu gọn thanh bên' })
    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    await user.click(toggle)

    expect(onToggleCollapsed).toHaveBeenCalledOnce()
  })

  it('TC-01: thu gọn còn cột biểu tượng, mỗi biểu tượng vẫn có tên đọc được và chú thích khi rê chuột', async () => {
    const { user } = renderSidebar('collapsed')

    const toggle = screen.getByRole('button', { name: 'Mở rộng thanh bên' })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    const nav = screen.getByRole('navigation', { name: 'Chức năng' })
    const library = within(nav).getByRole('link', { name: 'Thư viện' })
    expect(library).toHaveTextContent('')
    expect(screen.queryByTestId('sidebar-recent')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Mở nhanh' })).toBeInTheDocument()

    // Chú thích hiện cạnh biểu tượng khi rê chuột hoặc khi biểu tượng có tiêu điểm.
    const tooltip = library.nextElementSibling
    expect(tooltip).toHaveTextContent('Thư viện')
    expect(tooltip).toHaveAttribute('aria-hidden', 'true')
    await user.click(library)
    expect(library).toHaveAttribute('aria-current', 'page')
  })

  it('tài liệu vừa mở hiện ở mục Gần đây và mở lại được', async () => {
    recentStore.record(ACCOUNT, { id: 'doc-1', kind: 'document', title: 'Ghi chú: Định luật Ohm', to: '/library/doc-1' })
    const { user, router, onNavigate } = renderSidebar('drawer')

    const recent = screen.getByTestId('sidebar-recent')
    await user.click(within(recent).getByRole('link', { name: 'Ghi chú: Định luật Ohm' }))

    expect(router.state.location.pathname).toBe('/library/doc-1')
    expect(onNavigate).toHaveBeenCalledOnce()
  })

  it('trong ngăn kéo điện thoại có nút đóng thay cho nút thu gọn, chọn mục thì báo để đóng ngăn kéo', async () => {
    const { user, router, onClose, onNavigate } = renderSidebar('drawer', routes.home)

    expect(screen.queryByRole('button', { name: 'Thu gọn thanh bên' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Đóng thanh bên' }))
    expect(onClose).toHaveBeenCalledOnce()

    await user.click(screen.getByRole('link', { name: 'Thư viện' }))
    expect(router.state.location.pathname).toBe(routes.library)
    expect(onNavigate).toHaveBeenCalledOnce()
  })
})
