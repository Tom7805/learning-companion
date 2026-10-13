import { useSyncExternalStore } from 'react'
import { reloadPage } from '@/shared/lib/navigation'
import { sessionStore } from '@/shared/lib/storage'

/** Sự kiện trình duyệt phát ra khi ứng dụng cài được (Chrome, Edge, Android). */
interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>
}

const RELOAD_KEY = 'lc:chunk-reload'
const RELOAD_GUARD_MS = 30_000

let deferred: BeforeInstallPromptEvent | null = null
let installed = false
const listeners = new Set<() => void>()
const emit = () => listeners.forEach((listener) => listener())

function standalone() {
  return (
    (typeof window.matchMedia === 'function' && window.matchMedia('(display-mode: standalone)').matches) ||
    (navigator as Navigator & { standalone?: boolean }).standalone === true
  )
}

/** Chỉ tự tải lại một lần trong một khoảng ngắn, tránh vòng lặp nếu gói mã hỏng hẳn. */
function reloadedRecently() {
  const last = sessionStore.get<number>(RELOAD_KEY)
  const now = Date.now()
  if (last !== null && now - last < RELOAD_GUARD_MS) return true
  sessionStore.set(RELOAD_KEY, now)
  return false
}

/**
 * Cài ứng dụng lên máy (NCL-02-CN-001): giữ lại lời mời cài của trình duyệt để hiện nút "Cài ứng dụng" đúng lúc,
 * và đăng ký service worker lưu đệm phần khung để lần sau mở nhanh, kể cả khi mạng chập chờn. Trả về hàm gỡ các
 * trình nghe (dùng trong kiểm thử).
 */
export function initPwa() {
  const controller = new AbortController()
  const { signal } = controller
  installed = standalone()
  deferred = null
  window.addEventListener(
    'beforeinstallprompt',
    (event) => {
      event.preventDefault()
      deferred = event as BeforeInstallPromptEvent
      emit()
    },
    { signal },
  )
  window.addEventListener(
    'appinstalled',
    () => {
      deferred = null
      installed = true
      emit()
    },
    { signal },
  )
  // Bản mới vừa thay bản đang mở thì gói mã cũ của trang không còn: tải lại một lần để lấy bản mới. Mất mạng thì để
  // trang báo lỗi kèm nút thử lại, vì tải lại lúc đó chỉ ra trang lỗi của trình duyệt.
  window.addEventListener(
    'vite:preloadError',
    (event) => {
      if (!navigator.onLine || reloadedRecently()) return
      event.preventDefault()
      reloadPage()
    },
    { signal },
  )
  // Máy phát triển không dùng service worker để không lẫn với bản tải lại nóng của Vite.
  if (import.meta.env.PROD && 'serviceWorker' in navigator) {
    window.addEventListener(
      'load',
      () => {
        void navigator.serviceWorker.register('/sw.js').catch(() => undefined)
      },
      { signal },
    )
  }
  return () => controller.abort()
}

export const installPrompt = {
  subscribe(listener: () => void) {
    listeners.add(listener)
    return () => {
      listeners.delete(listener)
    }
  },
  state: () => (installed ? 'installed' : deferred ? 'available' : 'unavailable') as InstallState,
  /** Mở hộp thoại cài của trình duyệt; trả về lựa chọn của người học. */
  async install() {
    const event = deferred
    if (!event) return 'dismissed' as const
    await event.prompt()
    const { outcome } = await event.userChoice
    deferred = null
    if (outcome === 'accepted') installed = true
    emit()
    return outcome
  },
}

export type InstallState = 'available' | 'installed' | 'unavailable'

export function useInstallState() {
  return useSyncExternalStore(installPrompt.subscribe, installPrompt.state, () => 'unavailable' as InstallState)
}
