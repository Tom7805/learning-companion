import { BellOff, CheckCheck } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { useSidePanel } from '@/app/providers/sidePanel'
import { routes } from '@/app/routes'
import { toApiError } from '@/shared/api/errors'
import { useMediaQuery } from '@/shared/hooks/useMediaQuery'
import { Button, EmptyState, ErrorState, Skeleton, toast } from '@/shared/ui'
import { useMarkAllRead, useNotifications, useOpenNotification, useUnreadCount } from '../api/queries'
import type { AppNotification } from '../types'
import { NotificationItem } from './NotificationItem'

/**
 * Ngăn thông báo trong khung xem bên (NCL-02-CN-002): mới nhất trước, bấm một thông báo thì đánh dấu đã đọc và mở
 * đúng đối tượng; đối tượng không còn thì mở trang báo nội dung không còn. Màn hình rộng giữ ngăn mở để xem tiếp
 * thông báo khác; điện thoại đóng ngăn để thấy nội dung vừa mở.
 */
export function NotificationPanel() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const panel = useSidePanel()
  const docked = useMediaQuery('(min-width: 1024px)')
  const list = useNotifications()
  const unread = useUnreadCount(true, { poll: false }).data ?? list.data?.pages[0]?.unreadCount ?? 0
  const open = useOpenNotification()
  const markAll = useMarkAllRead()
  const [openingId, setOpeningId] = useState<string | null>(null)

  const items = list.data?.pages.flatMap((page) => page.items) ?? []

  const openNotification = async (notification: AppNotification) => {
    setOpeningId(notification.id)
    try {
      const opened = await open.mutateAsync(notification.id)
      if (!docked || panel.fullscreen) panel.close()
      navigate(opened.available && opened.path ? opened.path : routes.contentGone)
    } catch (caught) {
      if (toApiError(caught).status === 404) {
        // Thông báo vừa bị dọn (quá 90 ngày) trong lúc ngăn đang mở.
        void list.refetch()
        navigate(routes.contentGone)
      } else {
        toast.error(t('notifications.openFailed'))
      }
    } finally {
      setOpeningId(null)
    }
  }

  return (
    <div className="flex flex-col gap-4" data-testid="notification-panel">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p role="status" className="text-sm text-muted" data-testid="notification-summary">
          {unread > 0 ? t('notifications.unreadSummary', { count: unread }) : t('notifications.allRead')}
        </p>
        {items.length > 0 && (
          <Button
            variant="ghost"
            className="h-9 px-3 text-sm"
            disabled={unread === 0}
            loading={markAll.isPending}
            icon={<CheckCheck className="size-4" aria-hidden="true" />}
            onClick={() => markAll.mutate(undefined, { onError: () => toast.error(t('notifications.markFailed')) })}
          >
            {t('notifications.markAllRead')}
          </Button>
        )}
      </div>

      {list.isPending && (
        <div className="flex flex-col gap-2" aria-busy="true">
          {[0, 1, 2].map((key) => (
            <Skeleton key={key} className="h-[4.5rem]" />
          ))}
        </div>
      )}
      {list.isError && (
        <ErrorState
          title={t('notifications.loadFailed')}
          onRetry={() => void list.refetch()}
          retrying={list.isFetching}
          requestId={toApiError(list.error).requestId}
        />
      )}
      {list.isSuccess && items.length === 0 && (
        <EmptyState
          icon={<BellOff />}
          title={t('notifications.empty.title')}
          description={t('notifications.empty.body')}
        />
      )}
      {items.length > 0 && (
        <ul className="-mx-1 flex flex-col gap-1" data-testid="notification-list">
          {items.map((notification) => (
            <li key={notification.id}>
              <NotificationItem
                notification={notification}
                opening={openingId === notification.id}
                onOpen={() => void openNotification(notification)}
              />
            </li>
          ))}
        </ul>
      )}
      {list.hasNextPage && (
        <Button
          variant="outline"
          className="self-center"
          loading={list.isFetchingNextPage}
          onClick={() => void list.fetchNextPage()}
        >
          {t('notifications.loadMore')}
        </Button>
      )}
      {list.isSuccess && <p className="text-xs text-muted">{t('notifications.retention')}</p>}
    </div>
  )
}
