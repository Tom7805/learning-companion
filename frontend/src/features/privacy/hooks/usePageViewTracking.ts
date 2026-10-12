import { useEffect, useRef } from 'react'
import { useLocation } from 'react-router'
import { privacyApi } from '../api/privacyApi'

/**
 * Thống kê sử dụng ẩn danh (NCL-01-CN-006, TC-01): chỉ gửi khi người học đang bật mục này, và bắt đầu từ
 * lần chuyển trang kế tiếp sau khi bật. Máy chủ kiểm lại đồng ý và bỏ mọi mã bản ghi trong đường dẫn.
 */
export function usePageViewTracking(enabled: boolean) {
  const location = useLocation()
  const enabledRef = useRef(enabled)

  useEffect(() => {
    enabledRef.current = enabled
  }, [enabled])

  useEffect(() => {
    if (!enabledRef.current) return
    // Lỗi thống kê không bao giờ được làm phiền người học.
    privacyApi.trackPageView(location.pathname).catch(() => undefined)
  }, [location.pathname])
}
