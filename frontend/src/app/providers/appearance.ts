import { useMediaQuery } from '@/shared/hooks/useMediaQuery'
import { useUiPreferences, type FontSize, type ThemeMode } from '../store/uiStore'

/** Màu thanh trạng thái của trình duyệt và ứng dụng đã cài: trùng màu khung ứng dụng ở mỗi chế độ. */
const THEME_COLOR = { light: '#2a2a2c', dark: '#09090a' } as const

export type EffectiveTheme = 'light' | 'dark'

export function resolveTheme(mode: ThemeMode, systemDark: boolean): EffectiveTheme {
  return mode === 'system' ? (systemDark ? 'dark' : 'light') : mode
}

/** Chế độ màu đang hiển thị thật, sau khi đã tính "theo hệ thống". */
export function useEffectiveTheme(): EffectiveTheme {
  const { themeMode } = useUiPreferences()
  return resolveTheme(themeMode, useMediaQuery('(prefers-color-scheme: dark)'))
}

export function applyAppearance(theme: EffectiveTheme, fontSize: FontSize) {
  const root = document.documentElement
  root.dataset.theme = theme
  root.dataset.fontSize = fontSize
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', THEME_COLOR[theme])
}
