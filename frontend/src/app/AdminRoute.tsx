import { ShieldX } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link, Outlet } from 'react-router'
import { useAdminIdentity } from '@/features/admin'
import { toApiError } from '@/shared/api/errors'
import { usePageHeading } from '@/shared/hooks/usePageHeading'
import { buttonClasses, ErrorState, Skeleton } from '@/shared/ui'
import { routes } from './routes'

/**
 * Chỉ quản trị viên vào được khu quản trị. Quyền do máy chủ quyết định, kể cả khi mở thẳng đường dẫn: người không
 * phải quản trị viên nhận màn hình từ chối, còn máy chủ ghi lại lần từ chối (NCL-03-CN-001 TC-05).
 */
export function AdminRoute() {
  const { t } = useTranslation()
  const admin = useAdminIdentity()

  if (admin.isPending) {
    return (
      <div className="flex flex-col gap-5" aria-busy="true" data-testid="admin-checking">
        <p role="status" className="sr-only">
          {t('common.loading')}
        </p>
        <Skeleton className="h-9 w-2/3 max-w-md" />
        <Skeleton className="h-40" />
      </div>
    )
  }
  if (admin.isError) {
    const error = toApiError(admin.error)
    if (error.status === 403) return <AdminForbidden />
    return (
      <ErrorState
        title={t('errors.INTERNAL_ERROR')}
        onRetry={() => void admin.refetch()}
        retrying={admin.isFetching}
        requestId={error.requestId}
      />
    )
  }
  return <Outlet />
}

function AdminForbidden() {
  const { t } = useTranslation()
  const heading = usePageHeading(t('admin.forbidden.title'))
  return (
    <div className="mx-auto flex max-w-md flex-col items-center gap-4 py-12 text-center" data-testid="admin-forbidden">
      <span
        aria-hidden="true"
        className="grid size-16 place-items-center rounded-2xl border-[1.5px] border-ink bg-danger-soft text-danger"
      >
        <ShieldX className="size-7" />
      </span>
      <h1 ref={heading} tabIndex={-1} className="text-2xl tracking-tight outline-none">
        {t('admin.forbidden.title')}
      </h1>
      <p className="leading-relaxed text-muted">{t('admin.forbidden.body')}</p>
      <Link to={routes.home} className={buttonClasses('dark', 'md', 'mt-2')}>
        {t('admin.forbidden.home')}
      </Link>
    </div>
  )
}
