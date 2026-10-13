import { describe, expect, it, vi } from 'vitest'
import { DEFAULT_UI, UI_STORAGE_KEY, uiStore } from './uiStore'

const stored = () => JSON.parse(window.localStorage.getItem(UI_STORAGE_KEY) ?? 'null')

describe('uiStore', () => {
  it('mặc định theo hệ thống, chữ cỡ vừa và thanh bên mở rộng', () => {
    expect(uiStore.get()).toEqual({ themeMode: 'system', fontSize: 'medium', sidebarCollapsed: false })
    expect(DEFAULT_UI).toEqual(uiStore.get())
  })

  it('đổi tùy chọn thì lưu trên máy và báo cho nơi đang nghe', () => {
    const listener = vi.fn()
    const unsubscribe = uiStore.subscribe(listener)

    uiStore.set({ sidebarCollapsed: true })
    uiStore.set({ themeMode: 'dark', fontSize: 'large' })
    uiStore.set({ themeMode: 'dark' })

    expect(uiStore.get()).toEqual({ themeMode: 'dark', fontSize: 'large', sidebarCollapsed: true })
    expect(stored()).toEqual(uiStore.get())
    // Lần cuối không đổi gì nên không báo thừa.
    expect(listener).toHaveBeenCalledTimes(2)
    unsubscribe()
  })

  it('đọc lại tùy chọn đã lưu khi mở lại, giá trị hỏng thì về mặc định', () => {
    window.localStorage.setItem(UI_STORAGE_KEY, JSON.stringify({ themeMode: 'light', fontSize: 'small', sidebarCollapsed: true }))
    uiStore.reset()
    expect(uiStore.get()).toEqual({ themeMode: 'light', fontSize: 'small', sidebarCollapsed: true })

    window.localStorage.setItem(UI_STORAGE_KEY, JSON.stringify({ themeMode: 'neon', fontSize: 'huge', sidebarCollapsed: 'yes' }))
    uiStore.reset()
    expect(uiStore.get()).toEqual(DEFAULT_UI)

    window.localStorage.setItem(UI_STORAGE_KEY, '{không phải JSON')
    uiStore.reset()
    expect(uiStore.get()).toEqual(DEFAULT_UI)
  })

  it('bản lưu theo tài khoản chỉ áp khi người học chưa đổi gì trên máy trong phiên này', () => {
    uiStore.sync({ themeMode: 'dark', fontSize: 'large', sidebarCollapsed: true })
    expect(uiStore.get()).toEqual({ themeMode: 'dark', fontSize: 'large', sidebarCollapsed: true })

    uiStore.set({ themeMode: 'light' })
    uiStore.sync({ themeMode: 'dark', fontSize: 'small', sidebarCollapsed: false })

    expect(uiStore.get()).toEqual({ themeMode: 'light', fontSize: 'large', sidebarCollapsed: true })
  })

  it('trình duyệt chặn bộ nhớ thì tùy chọn vẫn áp trong phiên', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('blocked', 'SecurityError')
    })

    uiStore.set({ fontSize: 'large' })

    expect(uiStore.get().fontSize).toBe('large')
  })
})
