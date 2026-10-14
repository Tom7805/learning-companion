import { http } from '@/shared/api/httpClient'
import type { NotificationPage, OpenedNotification } from '../types'

export const NOTIFICATION_STREAM_URL = '/api/v1/notifications/stream'

export const notificationsApi = {
  async page(before?: string) {
    const { data } = await http.get<NotificationPage>('/notifications', { params: before ? { before } : undefined })
    return data
  },
  async unreadCount() {
    const { data } = await http.get<{ unreadCount: number }>('/notifications/unread-count')
    return data.unreadCount
  },
  async open(id: string) {
    const { data } = await http.post<OpenedNotification>(`/notifications/${encodeURIComponent(id)}/open`)
    return data
  },
  async readAll() {
    const { data } = await http.post<{ marked: number; unreadCount: number }>('/notifications/read-all')
    return data
  },
}
