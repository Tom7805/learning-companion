import { Bell } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/cn'

interface NotificationBellProps {
  unreadCount: number
  /** Ngăn thông báo đang mở. */
  expanded: boolean
  onToggle: () => void
  /** "sidebar": nằm cạnh nút Mở nhanh trên thanh bên; "compact": thanh bên thu gọn hoặc thanh trên của điện thoại. */
  variant?: 'sidebar' | 'compact'
  className?: string
}

/** Hiện đủ số tới 99, nhiều hơn thì "99+". */
function badgeText(count: number) {
  return count > 99 ? '99+' : String(count)
}

/**
 * Biểu tượng chuông ở đầu thanh bên (NCL-02-CN-002): huy hiệu cho biết số thông báo chưa đọc, bấm thì mở hoặc đóng
 * ngăn thông báo. Tên đọc được kèm số chưa đọc cho trình đọc màn hình.
 */
export function NotificationBell({
  unreadCount,
  expanded,
  onToggle,
  variant = 'compact',
  className,
}: NotificationBellProps) {
  const { t } = useTranslation()
  const label = unreadCount > 0 ? t('notifications.bellUnread', { count: unreadCount }) : t('notifications.bell')
  return (
    <button
      type="button"
      aria-label={label}
      aria-expanded={expanded}
      data-testid="notification-bell"
      data-unread={unreadCount}
      onClick={onToggle}
      className={cn(
        'relative grid shrink-0 place-items-center rounded-xl outline-none transition-colors',
        'focus-visible:ring-4 focus-visible:ring-sun/60',
        variant === 'sidebar'
          ? 'size-[3.25rem] rounded-2xl bg-on-night/10 text-on-night hover:bg-on-night/15'
          : 'size-11 text-on-night/80 hover:bg-on-night/10 hover:text-on-night',
        expanded && 'bg-on-night/15 text-on-night',
        className,
      )}
    >
      <Bell className="size-5" aria-hidden="true" />
      {unreadCount > 0 && (
        <span
          aria-hidden="true"
          data-testid="notification-badge"
          className={cn(
            'absolute right-1 top-1 grid h-5 min-w-5 place-items-center rounded-full border-2 border-night bg-accent',
            'px-1 text-[0.6875rem] font-bold leading-none text-on-accent',
          )}
        >
          {badgeText(unreadCount)}
        </span>
      )}
    </button>
  )
}
