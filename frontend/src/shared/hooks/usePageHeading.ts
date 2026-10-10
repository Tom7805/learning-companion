import { useEffect, useRef } from 'react'

/**
 * Đặt tiêu đề thẻ trình duyệt và, khi chuyển trang trong ứng dụng, đưa tiêu điểm về tiêu đề chính
 * để người dùng trình đọc màn hình biết đã sang màn hình mới.
 */
export function usePageHeading<T extends HTMLElement = HTMLHeadingElement>(title: string, focusKey?: unknown) {
  const ref = useRef<T>(null)

  useEffect(() => {
    document.title = `${title} · Learning Companion`
  }, [title])

  useEffect(() => {
    const navigatedInApp = (window.history.state?.idx ?? 0) > 0
    if (navigatedInApp || focusKey !== undefined) {
      ref.current?.focus({ preventScroll: false })
    }
  }, [focusKey])

  return ref
}
