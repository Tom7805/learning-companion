import { useSyncExternalStore } from 'react'
import { localStore } from '@/shared/lib/storage'

export type ThemeMode = 'light' | 'dark' | 'system'
export type FontSize = 'small' | 'medium' | 'large'

/** Tùy chọn giao diện (NCL-02-CN-001). Lưu trên máy để áp ngay khi mở, đồng bộ với tài khoản khi đã đăng nhập. */
export interface UiPreferences {
  themeMode: ThemeMode
  fontSize: FontSize
  sidebarCollapsed: boolean
}

/** Khóa lưu trên máy; đoạn mã đầu index.html cũng đọc khóa này để áp giao diện trước khi ứng dụng tải xong. */
export const UI_STORAGE_KEY = 'lc:ui'
/** Tài khoản có tùy chọn đã đổi trên máy nhưng chưa lưu được lên máy chủ (mất mạng, tải lại trang giữa chừng). */
const PENDING_KEY = 'lc:ui-pending'

export const DEFAULT_UI: UiPreferences = { themeMode: 'system', fontSize: 'medium', sidebarCollapsed: false }

const THEMES: ThemeMode[] = ['light', 'dark', 'system']
const FONT_SIZES: FontSize[] = ['small', 'medium', 'large']

function sanitize(value: Partial<UiPreferences> | null): UiPreferences {
  return {
    themeMode: THEMES.includes(value?.themeMode as ThemeMode) ? (value!.themeMode as ThemeMode) : DEFAULT_UI.themeMode,
    fontSize: FONT_SIZES.includes(value?.fontSize as FontSize) ? (value!.fontSize as FontSize) : DEFAULT_UI.fontSize,
    sidebarCollapsed: value?.sidebarCollapsed === true,
  }
}

let state: UiPreferences = sanitize(localStore.get<Partial<UiPreferences>>(UI_STORAGE_KEY))
/** Người học đã đổi tùy chọn trên máy trong phiên này: bản tải về từ máy chủ sau đó không được ghi đè lên. */
let changedLocally = false
const listeners = new Set<() => void>()

function apply(patch: Partial<UiPreferences>) {
  const next = sanitize({ ...state, ...patch })
  if (samePreferences(next, state)) return
  state = next
  localStore.set(UI_STORAGE_KEY, state)
  listeners.forEach((listener) => listener())
}

export const uiStore = {
  get: () => state,
  subscribe(listener: () => void) {
    listeners.add(listener)
    return () => {
      listeners.delete(listener)
    }
  },
  /** Người học đổi tùy chọn trên máy này. */
  set(patch: Partial<UiPreferences>) {
    changedLocally = true
    apply(patch)
  },
  /** Bản lưu theo tài khoản từ máy chủ: chỉ áp khi người học chưa đổi gì trên máy trong phiên này. */
  sync(saved: UiPreferences) {
    if (!changedLocally) apply(saved)
  },
  /** Tài khoản đang có thay đổi chưa lưu lên máy chủ, nếu có. */
  pendingFor: () => localStore.get<string>(PENDING_KEY),
  markPending(accountId: string) {
    localStore.set(PENDING_KEY, accountId)
  },
  markSynced() {
    localStore.remove(PENDING_KEY)
  },
  /** Chỉ dùng trong kiểm thử: quên các lần đổi và đọc lại từ bộ nhớ của máy. */
  reset() {
    changedLocally = false
    state = sanitize(localStore.get<Partial<UiPreferences>>(UI_STORAGE_KEY))
    listeners.forEach((listener) => listener())
  },
}

export function samePreferences(left: UiPreferences, right: UiPreferences) {
  return (
    left.themeMode === right.themeMode &&
    left.fontSize === right.fontSize &&
    left.sidebarCollapsed === right.sidebarCollapsed
  )
}

export function useUiPreferences() {
  return useSyncExternalStore(uiStore.subscribe, uiStore.get, uiStore.get)
}
