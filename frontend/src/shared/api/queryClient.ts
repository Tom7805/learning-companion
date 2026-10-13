import { QueryClient } from '@tanstack/react-query'
import { toApiError } from './errors'

/**
 * Chỉ thử lại một lần khi lỗi mạng hoặc máy chủ (5xx). Lỗi 4xx là câu trả lời dứt khoát (không tìm thấy, không có
 * quyền, chưa đăng nhập): thử lại chỉ làm màn hình báo lỗi hiện chậm hơn và gửi thêm một yêu cầu vô ích.
 */
export function shouldRetry(failureCount: number, error: unknown) {
  if (failureCount >= 1) return false
  const status = toApiError(error).status
  return status === 0 || status >= 500
}

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: shouldRetry, refetchOnWindowFocus: false, staleTime: 30_000 },
      mutations: { retry: false },
    },
  })
}
