import { act, render } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { setMedia } from '@/test/media'
import { uiStore } from '../store/uiStore'
import { resolveTheme } from './appearance'
import { ThemeProvider } from './ThemeProvider'

const root = document.documentElement
const themeColor = () => document.querySelector('meta[name="theme-color"]')?.getAttribute('content')

describe('ThemeProvider', () => {
  beforeEach(() => {
    const meta = document.createElement('meta')
    meta.name = 'theme-color'
    meta.content = '#2a2a2c'
    document.head.append(meta)
  })
  afterEach(() => {
    document.querySelector('meta[name="theme-color"]')?.remove()
  })

  it('tính chế độ đang hiển thị: "theo hệ thống" đi theo hệ điều hành', () => {
    expect(resolveTheme('light', true)).toBe('light')
    expect(resolveTheme('dark', false)).toBe('dark')
    expect(resolveTheme('system', true)).toBe('dark')
    expect(resolveTheme('system', false)).toBe('light')
  })

  it('TC-03: hệ điều hành chuyển tối khi đang chọn "theo hệ thống" thì giao diện chuyển tối ngay', () => {
    render(<ThemeProvider>nội dung</ThemeProvider>)
    expect(root.dataset.theme).toBe('light')
    expect(themeColor()).toBe('#2a2a2c')

    act(() => setMedia({ dark: true }))

    expect(root.dataset.theme).toBe('dark')
    expect(themeColor()).toBe('#09090a')
  })

  it('chọn sáng hoặc tối thì không theo hệ điều hành nữa; cỡ chữ áp lên toàn trang', () => {
    setMedia({ dark: true })
    render(<ThemeProvider>nội dung</ThemeProvider>)
    expect(root.dataset.theme).toBe('dark')
    expect(root.dataset.fontSize).toBe('medium')

    act(() => uiStore.set({ themeMode: 'light', fontSize: 'large' }))
    expect(root.dataset.theme).toBe('light')
    expect(root.dataset.fontSize).toBe('large')

    act(() => setMedia({ dark: false }))
    act(() => uiStore.set({ themeMode: 'dark', fontSize: 'small' }))
    expect(root.dataset.theme).toBe('dark')
    expect(root.dataset.fontSize).toBe('small')
  })
})
