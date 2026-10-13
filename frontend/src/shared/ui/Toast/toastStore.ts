export type ToastTone = 'success' | 'info' | 'danger'

export interface ToastItem {
  id: number
  tone: ToastTone
  message: string
}

let items: ToastItem[] = []
let nextId = 1
const listeners = new Set<() => void>()

function emit() {
  listeners.forEach((listener) => listener())
}

function push(tone: ToastTone, message: string) {
  // Cùng một lời nhắn đang hiện thì không chồng thêm bản nữa.
  if (items.some((item) => item.message === message)) return
  items = [...items, { id: nextId++, tone, message }].slice(-3)
  emit()
}

export function dismissToast(id: number) {
  items = items.filter((item) => item.id !== id)
  emit()
}

/** Thông báo nổi ngắn ở góc màn hình, tự ẩn sau vài giây; trình đọc màn hình đọc ngay khi hiện. */
export const toast = {
  success: (message: string) => push('success', message),
  info: (message: string) => push('info', message),
  error: (message: string) => push('danger', message),
  /** Chỉ dùng trong kiểm thử. */
  clear() {
    items = []
    emit()
  },
}

export function subscribeToasts(listener: () => void) {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

export const currentToasts = () => items
