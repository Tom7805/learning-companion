import { useSyncExternalStore } from 'react'

function subscribe(notify: () => void) {
  window.addEventListener('online', notify)
  window.addEventListener('offline', notify)
  return () => {
    window.removeEventListener('online', notify)
    window.removeEventListener('offline', notify)
  }
}

/** Trình duyệt có đang báo là có mạng không; mất mạng thì giao diện báo "đang kết nối lại". */
export function useOnlineStatus() {
  return useSyncExternalStore(subscribe, () => navigator.onLine, () => true)
}
