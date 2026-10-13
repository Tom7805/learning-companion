import { useCallback, useSyncExternalStore } from 'react'

/** Một truy vấn phương tiện (ví dụ chế độ tối của hệ điều hành, màn hình rộng) và tự cập nhật khi nó đổi. */
export function useMediaQuery(query: string) {
  const subscribe = useCallback(
    (notify: () => void) => {
      if (typeof window.matchMedia !== 'function') return () => undefined
      const list = window.matchMedia(query)
      list.addEventListener('change', notify)
      return () => list.removeEventListener('change', notify)
    },
    [query],
  )
  return useSyncExternalStore(
    subscribe,
    () => (typeof window.matchMedia === 'function' ? window.matchMedia(query).matches : false),
    () => false,
  )
}
