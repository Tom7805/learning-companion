import { act, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import { ThemeProvider } from '@/app/providers/ThemeProvider'
import { uiStore } from '@/app/store/uiStore'
import { setMedia } from '@/test/media'
import { apiError, verifiedAccount } from '@/test/mocks/handlers'
import { server } from '@/test/mocks/server'
import { renderWithProviders } from '@/test/renderWithProviders'
import { AppearanceSection } from './AppearanceSection'

let saved: Record<string, unknown>[] = []

function renderSection({ signedIn = true } = {}) {
  server.use(
    http.get('*/api/v1/auth/session', () =>
      HttpResponse.json(signedIn ? { authenticated: true, account: verifiedAccount } : { authenticated: false }),
    ),
    http.patch('*/api/v1/ui-preferences', async ({ request }) => {
      const body = (await request.json()) as Record<string, unknown>
      saved.push(body)
      return HttpResponse.json({ themeMode: 'SYSTEM', fontSize: 'MEDIUM', sidebarCollapsed: false, ...body })
    }),
  )
  return renderWithProviders(
    <ThemeProvider>
      <AppearanceSection />
    </ThemeProvider>,
  )
}

const root = document.documentElement

describe('AppearanceSection', () => {
  beforeEach(() => {
    saved = []
  })

  it('chọn chế độ tối thì cả trang tối ngay và lưu theo tài khoản', async () => {
    const { user } = renderSection()
    await screen.findByTestId('appearance-section')
    expect(screen.getByRole('radio', { name: /Theo hệ thống/ })).toBeChecked()

    await user.click(screen.getByRole('radio', { name: /Tối/ }))

    expect(screen.getByRole('radio', { name: /Tối/ })).toBeChecked()
    expect(root.dataset.theme).toBe('dark')
    await waitFor(() => expect(saved).toEqual([{ themeMode: 'DARK' }]))
  })

  it('TC-03: "theo hệ thống" cho biết đang hiển thị chế độ nào và đổi theo hệ điều hành', async () => {
    renderSection()
    const note = await screen.findByTestId('theme-effective')
    expect(note).toHaveTextContent('Đang dùng: Sáng (theo hệ thống)')

    act(() => setMedia({ dark: true }))

    expect(note).toHaveTextContent('Đang dùng: Tối (theo hệ thống)')
    expect(root.dataset.theme).toBe('dark')
  })

  it('ba cỡ chữ, chọn bằng bàn phím được, áp lên toàn trang', async () => {
    const { user } = renderSection()
    await screen.findByTestId('appearance-section')
    expect(screen.getAllByRole('radio', { name: /^(Nhỏ|Vừa|Lớn)$/ })).toHaveLength(3)
    expect(screen.getByRole('radio', { name: 'Vừa' })).toBeChecked()

    screen.getByRole('radio', { name: 'Vừa' }).focus()
    await user.keyboard('{ArrowRight}')

    expect(screen.getByRole('radio', { name: 'Lớn' })).toBeChecked()
    expect(root.dataset.fontSize).toBe('large')
    await waitFor(() => expect(saved).toEqual([{ fontSize: 'LARGE' }]))
  })

  it('đổi liên tiếp thì mỗi lần áp ngay, không chờ lần lưu trước', async () => {
    let release: () => void = () => undefined
    let stored: Record<string, unknown> = { themeMode: 'SYSTEM', fontSize: 'MEDIUM', sidebarCollapsed: false }
    const { user } = renderSection()
    server.use(
      http.patch('*/api/v1/ui-preferences', async ({ request }) => {
        const body = (await request.json()) as Record<string, unknown>
        saved.push(body)
        await new Promise<void>((resolve) => {
          release = resolve
        })
        stored = { ...stored, ...body }
        return HttpResponse.json(stored)
      }),
    )
    await screen.findByTestId('appearance-section')

    await user.click(screen.getByRole('radio', { name: /Tối/ }))
    await user.click(screen.getByRole('radio', { name: 'Lớn' }))

    expect(root.dataset.theme).toBe('dark')
    expect(root.dataset.fontSize).toBe('large')
    expect(window.localStorage.getItem('lc:ui-pending')).toBe(JSON.stringify(verifiedAccount.id))
    await waitFor(() => expect(saved).toEqual([{ themeMode: 'DARK' }]))
    release()
    await waitFor(() => expect(saved).toEqual([{ themeMode: 'DARK' }, { fontSize: 'LARGE' }]))
    release()
    await waitFor(() => expect(window.localStorage.getItem('lc:ui-pending')).toBeNull())
  })

  it('không lưu được lên tài khoản thì báo, giao diện vẫn áp trên máy', async () => {
    const { user } = renderSection()
    server.use(http.patch('*/api/v1/ui-preferences', () => apiError(500, 'INTERNAL_ERROR')))
    await screen.findByTestId('appearance-section')

    await user.click(screen.getByRole('radio', { name: /Sáng/ }))

    expect(await screen.findByText('Chưa lưu được lên tài khoản, giao diện vẫn áp dụng trên máy này.')).toBeInTheDocument()
    expect(uiStore.get().themeMode).toBe('light')
    expect(root.dataset.theme).toBe('light')
    // Còn đánh dấu chưa lưu để lần mở sau đẩy lên tài khoản.
    expect(window.localStorage.getItem('lc:ui-pending')).toBe(JSON.stringify(verifiedAccount.id))
  })

  it('trình duyệt chưa mời cài thì hướng dẫn cài thủ công', async () => {
    renderSection()

    expect(await screen.findByTestId('appearance-install')).toHaveTextContent('Thêm vào MH chính')
  })
})
