import { useTranslation } from 'react-i18next'
import { Navigate, Outlet, useLocation } from 'react-router'
import { useSession } from '@/features/auth'
import { PendingDeletionNotice } from '@/features/privacy'
import { Button, Spinner } from '@/shared/ui'
import { routes } from './routes'

/**
 * Chỉ cho vào khi có phiên hợp lệ. Mất phiên thì về màn hình đăng nhập kèm lý do (hết hạn, bị đăng xuất
 * từ thiết bị khác) và trang đang xem để quay lại sau khi đăng nhập. Tài khoản đang chờ xóa chỉ thấy câu hỏi có hủy
 * yêu cầu xóa không (NCL-01-CN-009).
 */
export function ProtectedRoute() {
  const { t } = useTranslation()
  const location = useLocation()
  const session = useSession()

  if (session.isPending) {
    return (
      <div className="grid min-h-dvh place-items-center">
        <Spinner className="size-8" label={t('common.loading')} />
      </div>
    )
  }
  if (session.isError) {
    return (
      <div className="grid min-h-dvh place-items-center gap-4 p-6 text-center">
        <p>{t('errors.NETWORK_ERROR')}</p>
        <Button variant="dark" onClick={() => void session.refetch()}>
          {t('common.retry')}
        </Button>
      </div>
    )
  }
  if (!session.data.authenticated) {
    return (
      <Navigate
        to={routes.login}
        replace
        state={{ from: location.pathname + location.search, reason: session.data.reason }}
      />
    )
  }
  if (session.data.account?.status === 'PENDING_DELETION') {
    return <PendingDeletionNotice account={session.data.account} />
  }
  return <Outlet />
}
