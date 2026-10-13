import { WifiOff } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useUiPreferences } from '@/app/store/uiStore'
import { cn } from '@/shared/lib/cn'
import { AppMark, Skeleton } from '@/shared/ui'

/**
 * Phần khung hiện ngay từ bộ lưu đệm khi mạng chập chờn (NCL-02-CN-001, TC-05): thanh bên và vùng làm việc dạng
 * khung xương kèm thông báo đang kết nối lại. Không có dữ liệu cá nhân nào ở đây. Thanh bên đã thu gọn thì khung
 * xương cũng thu gọn, để lúc vào hẳn ứng dụng bố cục không nhảy.
 */
export function AppShellSkeleton({ reconnecting = false }: { reconnecting?: boolean }) {
  const { t } = useTranslation()
  const { sidebarCollapsed: collapsed } = useUiPreferences()
  return (
    <div className="flex min-h-dvh bg-night md:h-dvh" aria-busy="true" data-testid="app-shell-skeleton">
      <div
        className={cn(
          'hidden shrink-0 flex-col gap-5 md:flex',
          collapsed ? 'w-[4.75rem] items-center px-2 py-3' : 'w-[18rem] p-3',
        )}
      >
        <div className="flex items-center gap-2.5 pl-1">
          <AppMark />
          {!collapsed && <span className="text-[0.9375rem] font-semibold tracking-tight text-on-night">{t('app.name')}</span>}
        </div>
        <Skeleton className={cn('rounded-2xl bg-on-night/10', collapsed ? 'size-11' : 'h-14 w-full')} />
        <div className={cn('flex flex-col gap-2', !collapsed && 'w-full')}>
          <Skeleton className={cn('bg-on-night/10', collapsed ? 'size-11' : 'h-10')} />
          <Skeleton className={cn('bg-on-night/10', collapsed ? 'size-11' : 'h-10')} />
        </div>
        <Skeleton className={cn('mt-auto rounded-2xl bg-on-night/10', collapsed ? 'size-10 rounded-full' : 'h-12 w-full')} />
      </div>
      <div className="flex min-w-0 flex-1 flex-col md:py-2 md:pr-2">
        <div className="flex items-center gap-3 px-4 py-3 md:hidden">
          <AppMark className="size-8" />
          <span className="font-semibold text-on-night">{t('app.name')}</span>
        </div>
        <div className="flex flex-1 flex-col gap-5 rounded-t-[1.25rem] bg-surface px-5 py-6 sm:px-8 md:rounded-[1.5rem] lg:px-10">
          {reconnecting && (
            <p
              role="status"
              data-testid="reconnecting"
              className="flex items-center gap-2 self-start rounded-full border-[1.5px] border-ink bg-sun-soft px-3.5 py-1.5 text-sm font-medium"
            >
              <WifiOff className="size-4" aria-hidden="true" />
              {t('shell.reconnecting')}
            </p>
          )}
          <Skeleton className="h-9 w-2/3 max-w-md" />
          <Skeleton className="h-36" />
          <div className="grid gap-4 sm:grid-cols-3">
            <Skeleton className="h-28" />
            <Skeleton className="h-28" />
            <Skeleton className="h-28" />
          </div>
        </div>
      </div>
    </div>
  )
}
