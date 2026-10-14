import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { queryKeys } from '@/shared/api/queryKeys'
import { NOTIFICATION_STREAM_URL } from '../api/notificationsApi'

interface UnreadEvent {
  unreadCount: number
  created: boolean
}

/**
 * Nghe kênh trực tiếp của máy chủ: số chưa đọc đổi (có thông báo mới, đã đọc ở thẻ hay thiết bị khác) thì biểu
 * tượng chuông cập nhật ngay, danh sách đang mở tải lại (NCL-02-CN-002). Trình duyệt tự nối lại khi kênh đứt.
 * Trả về false khi không giữ được kênh, để số chưa đọc chuyển sang hỏi lại định kỳ.
 */
export function useNotificationStream(enabled: boolean) {
  const queryClient = useQueryClient()
  const [connected, setConnected] = useState(false)

  useEffect(() => {
    if (!enabled || typeof EventSource === 'undefined') return undefined
    const source = new EventSource(NOTIFICATION_STREAM_URL, { withCredentials: true })
    const onUnread = (event: MessageEvent<string>) => {
      const data = JSON.parse(event.data) as UnreadEvent
      queryClient.setQueryData(queryKeys.notificationsUnread, data.unreadCount)
      if (data.created || queryClient.getQueryData(queryKeys.notifications)) {
        void queryClient.invalidateQueries({ queryKey: queryKeys.notifications })
      }
    }
    source.addEventListener('unread', onUnread as EventListener)
    source.onopen = () => setConnected(true)
    // Đang nối lại thì vẫn coi là còn kênh; chỉ khi trình duyệt bỏ hẳn (ví dụ phiên đã hết) mới hỏi lại định kỳ.
    source.onerror = () => setConnected(source.readyState !== EventSource.CLOSED)
    return () => {
      source.removeEventListener('unread', onUnread as EventListener)
      source.close()
    }
  }, [enabled, queryClient])

  return enabled && connected
}
