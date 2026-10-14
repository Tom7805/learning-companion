import { useInfiniteQuery, useMutation, useQuery, useQueryClient, type InfiniteData } from '@tanstack/react-query'
import { queryKeys } from '@/shared/api/queryKeys'
import type { NotificationPage } from '../types'
import { notificationsApi } from './notificationsApi'

/**
 * Số thông báo chưa đọc cho biểu tượng chuông. Kênh trực tiếp ({@link useNotificationStream}) cập nhật số này ngay
 * khi có thay đổi; trình duyệt không mở được kênh thì hỏi lại mỗi phút.
 */
export function useUnreadCount(enabled: boolean, { poll }: { poll: boolean }) {
  return useQuery({
    queryKey: queryKeys.notificationsUnread,
    queryFn: notificationsApi.unreadCount,
    enabled,
    staleTime: poll ? 30_000 : Infinity,
    refetchInterval: poll ? 60_000 : false,
  })
}

/** Danh sách thông báo, mới nhất trước, tải thêm trang cũ hơn khi cần. */
export function useNotifications() {
  const queryClient = useQueryClient()
  return useInfiniteQuery({
    queryKey: queryKeys.notifications,
    queryFn: async ({ pageParam }) => {
      const page = await notificationsApi.page(pageParam)
      queryClient.setQueryData(queryKeys.notificationsUnread, page.unreadCount)
      return page
    },
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor,
  })
}

type NotificationPages = InfiniteData<NotificationPage, string | undefined>

/** Đánh dấu đã đọc ngay trong danh sách đang hiện, không phải tải lại. */
function markRead(pages: NotificationPages | undefined, read: (id: string) => boolean) {
  if (!pages) return pages
  const now = new Date().toISOString()
  return {
    ...pages,
    pages: pages.pages.map((page) => ({
      ...page,
      items: page.items.map((item) => (!item.readAt && read(item.id) ? { ...item, readAt: now } : item)),
    })),
  }
}

/** Mở một thông báo: máy chủ đánh dấu đã đọc và cho biết đối tượng còn mở được không. */
export function useOpenNotification() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: notificationsApi.open,
    onSuccess: (opened, id) => {
      queryClient.setQueryData(queryKeys.notificationsUnread, opened.unreadCount)
      queryClient.setQueryData<NotificationPages>(queryKeys.notifications, (pages) =>
        markRead(pages, (itemId) => itemId === id),
      )
    },
  })
}

export function useMarkAllRead() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: notificationsApi.readAll,
    onSuccess: (result) => {
      queryClient.setQueryData(queryKeys.notificationsUnread, result.unreadCount)
      queryClient.setQueryData<NotificationPages>(queryKeys.notifications, (pages) => markRead(pages, () => true))
    },
  })
}
