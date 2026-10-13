import { act, fireEvent, screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { routes } from '@/app/routes'
import { recentStore } from '@/app/store/recentStore'
import { UI_STORAGE_KEY, uiStore } from '@/app/store/uiStore'
import { reloadTo } from '@/shared/lib/navigation'
import { setMedia } from '@/test/media'
import { serverUiPreferences, verifiedAccount } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import { AppShell } from './AppShell'

vi.mock('@/shared/lib/navigation', () => ({ reloadTo: vi.fn(), reloadPage: vi.fn() }))

/** Mọi lần lưu tùy chọn giao diện lên tài khoản. */
let saved: Record<string, unknown>[] = []

function renderShell({ path = routes.home, preferences = serverUiPreferences } = {}) {
  server.use(
    http.get('*/api/v1/auth/session', () => HttpResponse.json({ authenticated: true, account: verifiedAccount })),
    http.get('*/api/v1/ui-preferences', () => HttpResponse.json(preferences)),
    http.patch('*/api/v1/ui-preferences', async ({ request }) => {
      const body = (await request.json()) as Record<string, unknown>
      saved.push(body)
      return HttpResponse.json({ ...preferences, ...body })
    }),
  )
  return renderWithProviders(null, {
    initialEntries: [path],
    routes: [
      {
        element: <AppShell />,
        children: [
          { path: routes.home, element: <h1>Trang chính của Lan Anh</h1> },
          { path: routes.library, element: <h1>Thư viện của Lan Anh</h1> },
        ],
      },
    ],
  })
}

const shell = () => screen.findByTestId('app-shell')

describe('AppShell · màn hình rộng', () => {
  beforeEach(() => {
    saved = []
  })

  it('có thanh bên với Mở nhanh, mục chức năng, mục gần đây, tài khoản và vùng làm việc chính', async () => {
    renderShell()
    await shell()

    const sidebar = screen.getByTestId('sidebar')
    expect(within(sidebar).getByRole('button', { name: /Mở nhanh/ })).toBeInTheDocument()
    expect(within(sidebar).getByRole('navigation', { name: 'Chức năng' })).toBeInTheDocument()
    expect(within(sidebar).getByTestId('sidebar-recent')).toBeInTheDocument()
    expect(within(sidebar).getByRole('button', { name: 'Trình đơn tài khoản của Lan Anh' })).toBeInTheDocument()
    expect(screen.getByRole('main')).toHaveTextContent('Trang chính của Lan Anh')
    expect(screen.queryByTestId('open-sidebar')).not.toBeInTheDocument()
  })

  it('TC-01: thu gọn thanh bên thì còn cột biểu tượng, nhớ trên máy và lưu theo tài khoản', async () => {
    const { user } = renderShell()
    await shell()

    await user.click(screen.getByRole('button', { name: 'Thu gọn thanh bên' }))

    const sidebar = screen.getByTestId('sidebar')
    expect(sidebar).toHaveAttribute('data-collapsed', 'true')
    expect(within(sidebar).getByRole('link', { name: 'Thư viện' })).toHaveTextContent('')
    expect(JSON.parse(window.localStorage.getItem(UI_STORAGE_KEY)!)).toMatchObject({ sidebarCollapsed: true })
    await waitFor(() => expect(saved).toEqual([{ sidebarCollapsed: true }]))

    await user.click(within(sidebar).getByRole('button', { name: 'Mở rộng thanh bên' }))
    expect(sidebar).not.toHaveAttribute('data-collapsed')
    await waitFor(() => expect(saved).toEqual([{ sidebarCollapsed: true }, { sidebarCollapsed: false }]))
  })

  it('mở trên máy khác: áp tùy chọn đã lưu theo tài khoản (thanh bên thu gọn, chế độ tối)', async () => {
    renderShell({ preferences: { themeMode: 'DARK', fontSize: 'LARGE', sidebarCollapsed: true } })
    await shell()

    await waitFor(() => expect(screen.getByTestId('sidebar')).toHaveAttribute('data-collapsed', 'true'))
    expect(uiStore.get()).toEqual({ themeMode: 'dark', fontSize: 'large', sidebarCollapsed: true })
    expect(saved).toEqual([])
  })

  it('TC-01: thu gọn rồi tải lại ngay khi chưa kịp lưu lên tài khoản thì bản trên máy được giữ và đẩy lên', async () => {
    // Lần trước: đã thu gọn trên máy nhưng trang tải lại trước khi máy chủ nhận được.
    window.localStorage.setItem(UI_STORAGE_KEY, JSON.stringify({ themeMode: 'system', fontSize: 'medium', sidebarCollapsed: true }))
    window.localStorage.setItem('lc:ui-pending', JSON.stringify(verifiedAccount.id))
    uiStore.reset()
    renderShell()
    await shell()

    await waitFor(() => expect(saved).toEqual([{ themeMode: 'SYSTEM', fontSize: 'MEDIUM', sidebarCollapsed: true }]))
    expect(screen.getByTestId('sidebar')).toHaveAttribute('data-collapsed', 'true')
    await waitFor(() => expect(window.localStorage.getItem('lc:ui-pending')).toBeNull())
  })

  it('cờ chưa lưu của tài khoản khác trên cùng máy không đè lên tùy chọn của tài khoản này', async () => {
    window.localStorage.setItem(UI_STORAGE_KEY, JSON.stringify({ themeMode: 'light', fontSize: 'small', sidebarCollapsed: true }))
    window.localStorage.setItem('lc:ui-pending', JSON.stringify('tai-khoan-khac'))
    uiStore.reset()
    renderShell({ preferences: { themeMode: 'DARK', fontSize: 'LARGE', sidebarCollapsed: false } })
    await shell()

    await waitFor(() => expect(uiStore.get()).toEqual({ themeMode: 'dark', fontSize: 'large', sidebarCollapsed: false }))
    expect(saved).toEqual([])
  })

  it('Trợ giúp trong trình đơn tài khoản mở ở khung xem bên; Giao diện mở cài đặt đúng mục', async () => {
    const { user, router } = renderShell()
    await shell()

    await user.click(screen.getByRole('button', { name: 'Trình đơn tài khoản của Lan Anh' }))
    await user.click(screen.getByRole('menuitem', { name: 'Trợ giúp' }))
    const panel = screen.getByTestId('side-panel')
    expect(panel).toHaveAccessibleName('Trợ giúp')
    expect(within(panel).getByTestId('help-panel')).toHaveTextContent('Làm quen với khung ứng dụng')

    await user.click(screen.getByRole('button', { name: 'Trình đơn tài khoản của Lan Anh' }))
    await user.click(screen.getByRole('menuitem', { name: 'Giao diện' }))
    expect(router.state.location.search).toBe('?settings=appearance')
    expect(await screen.findByTestId('appearance-section')).toBeInTheDocument()
  })

  it('nút Mở nhanh mở hộp tìm trang và tài liệu', async () => {
    const { user } = renderShell()
    await shell()

    await user.click(within(screen.getByTestId('sidebar')).getByRole('button', { name: /Mở nhanh/ }))

    expect(screen.getByRole('combobox', { name: 'Tìm trang hoặc tài liệu' })).toHaveFocus()
  })

  it('Ctrl K mở nhanh từ bất cứ đâu, nút Mở nhanh báo phím tắt cho trình đọc màn hình', async () => {
    const { user } = renderShell()
    await shell()
    expect(within(screen.getByTestId('sidebar')).getByRole('button', { name: /Mở nhanh/ })).toHaveAttribute(
      'aria-keyshortcuts',
      'Control+K Meta+K',
    )

    await user.keyboard('{Control>}k{/Control}')

    expect(screen.getByRole('combobox', { name: 'Tìm trang hoặc tài liệu' })).toHaveFocus()
  })

  it('đăng xuất thì xóa mục gần đây của tài khoản khỏi máy rồi về trang đăng nhập', async () => {
    server.use(http.post('*/api/v1/auth/logout', () => new HttpResponse(null, { status: 204 })))
    recentStore.record(verifiedAccount.id, { id: 'doc-1', kind: 'document', title: 'Ghi chú', to: '/library/doc-1' })
    const { user } = renderShell()
    await shell()
    expect(screen.getByTestId('sidebar-recent')).toHaveTextContent('Ghi chú')

    await user.click(screen.getByRole('button', { name: 'Trình đơn tài khoản của Lan Anh' }))
    await user.click(screen.getByRole('menuitem', { name: 'Đăng xuất' }))

    await waitFor(() => expect(reloadTo).toHaveBeenCalledWith(`${routes.login}?loggedOut=1`))
    expect(window.localStorage.getItem(`lc:recent:${verifiedAccount.id}`)).toBeNull()
  })
})

describe('AppShell · điện thoại', () => {
  beforeEach(() => {
    setMedia({ width: 360 })
  })

  it('TC-02: nút mở thanh bên trượt ngăn kéo từ cạnh trái; chọn mục thì sang trang và tự đóng', async () => {
    const { user, router } = renderShell()
    await shell()
    expect(screen.queryByTestId('sidebar')).not.toBeInTheDocument()

    const opener = screen.getByRole('button', { name: 'Mở thanh bên' })
    expect(opener).toHaveAttribute('aria-expanded', 'false')
    await user.click(opener)

    expect(opener).toHaveAttribute('aria-expanded', 'true')
    const drawer = screen.getByRole('dialog', { name: 'Thanh bên' })
    expect(drawer).toHaveAttribute('id', opener.getAttribute('aria-controls'))
    await user.click(within(drawer).getByRole('link', { name: 'Thư viện' }))

    expect(router.state.location.pathname).toBe(routes.library)
    expect(screen.queryByRole('dialog', { name: 'Thanh bên' })).not.toBeInTheDocument()
    expect(opener).toHaveAttribute('aria-expanded', 'false')
  })

  it('TC-02: chạm ra ngoài ngăn kéo hoặc Esc thì đóng', async () => {
    const { user } = renderShell()
    await shell()
    const opener = screen.getByRole('button', { name: 'Mở thanh bên' })

    await user.click(opener)
    fireEvent.click(screen.getByRole('dialog', { name: 'Thanh bên' }))
    expect(opener).toHaveAttribute('aria-expanded', 'false')

    await user.click(opener)
    fireEvent(screen.getByRole('dialog', { name: 'Thanh bên' }), new Event('cancel', { cancelable: true }))
    expect(opener).toHaveAttribute('aria-expanded', 'false')
  })

  it('thanh trên cùng có Mở nhanh và trình đơn tài khoản', async () => {
    const { user } = renderShell()
    await shell()

    expect(screen.getByTestId('account-menu-topbar')).toHaveAccessibleName('Trình đơn tài khoản của Lan Anh')
    await user.click(screen.getByRole('button', { name: 'Mở nhanh' }))
    expect(screen.getByRole('combobox', { name: 'Tìm trang hoặc tài liệu' })).toBeInTheDocument()
  })
})

describe('AppShell · mất mạng', () => {
  let online = true
  beforeEach(() => {
    online = true
    vi.spyOn(Navigator.prototype, 'onLine', 'get').mockImplementation(() => online)
  })
  afterEach(() => {
    online = true
  })

  it('TC-05: mất mạng thì báo đang kết nối lại, có mạng trở lại thì báo và tải lại dữ liệu', async () => {
    renderShell()
    await shell()
    expect(screen.queryByTestId('offline-banner')).not.toBeInTheDocument()

    online = false
    act(() => {
      window.dispatchEvent(new Event('offline'))
    })
    expect(screen.getByTestId('offline-banner')).toHaveTextContent('Mất kết nối mạng')

    online = true
    act(() => {
      window.dispatchEvent(new Event('online'))
    })
    expect(screen.queryByTestId('offline-banner')).not.toBeInTheDocument()
    expect(await screen.findByText('Đã kết nối lại.')).toBeInTheDocument()
  })
})
