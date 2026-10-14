import { Bell, FileArchive, ShieldAlert } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useDateFormat } from '@/shared/hooks/useDateFormat'
import { cn } from '@/shared/lib/cn'
import { formatBytes } from '@/shared/lib/format'
import { Spinner } from '@/shared/ui'
import { describeNotification, notificationCategory, type NotificationCategory } from '../lib/describe'
import type { AppNotification } from '../types'

const ICONS: Record<NotificationCategory, { icon: typeof Bell; tone: string }> = {
  security: { icon: ShieldAlert, tone: 'bg-lilac' },
  data: { icon: FileArchive, tone: 'bg-sky' },
  general: { icon: Bell, tone: 'bg-sun' },
}

/**
 * Một dòng trong ngăn thông báo: loại, tiêu đề, mô tả ngắn và thời điểm. Chưa đọc thì chữ đậm kèm chấm cam; bấm để
 * mở đúng đối tượng (và đánh dấu đã đọc).
 */
export function NotificationItem({ notification, opening, onOpen }: {
  notification: AppNotification
  opening: boolean
  onOpen: () => void
}) {
  const { t, i18n } = useTranslation()
  const format = useDateFormat()
  const category = notificationCategory(notification.type)
  const { title, body } = describeNotification(notification, t, {
    dateTime: format.dateTime,
    bytes: (value) => formatBytes(value, i18n.language),
  })
  const unread = !notification.readAt
  const { icon: Icon, tone } = ICONS[category]

  return (
    <button
      type="button"
      onClick={onOpen}
      disabled={opening}
      aria-busy={opening || undefined}
      data-testid="notification-item"
      data-unread={unread || undefined}
      className={cn(
        'flex w-full items-start gap-3 rounded-field px-3 py-3 text-left outline-none transition-colors',
        'hover:bg-ink/5 focus-visible:ring-4 focus-visible:ring-focus/30 disabled:cursor-wait',
        unread && 'bg-sun-soft/60',
      )}
    >
      <span
        aria-hidden="true"
        className={cn('mt-0.5 grid size-9 shrink-0 place-items-center rounded-xl border-[1.5px] border-ink', tone)}
      >
        {opening ? <Spinner className="size-4" /> : <Icon className="size-4" />}
      </span>
      <span className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span className="flex items-center gap-2 text-xs font-medium text-muted">
          {t(`notifications.category.${category}`)}
          <span aria-hidden="true">·</span>
          <time dateTime={notification.createdAt} title={format.dateTime(notification.createdAt)}>
            {format.relative(notification.createdAt)}
          </time>
        </span>
        <span className={cn('text-[0.9375rem] leading-snug', unread ? 'font-semibold' : 'font-medium')}>
          {title}
        </span>
        {body && <span className="text-sm leading-snug text-ink-soft">{body}</span>}
      </span>
      {unread && (
        <span className="mt-2 flex shrink-0 items-center">
          <span aria-hidden="true" className="size-2.5 rounded-full bg-accent" />
          <span className="sr-only">{t('notifications.unread')}</span>
        </span>
      )}
    </button>
  )
}
