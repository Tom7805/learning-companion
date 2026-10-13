import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { Navigate, Outlet, useLocation } from 'react-router'
import { useSession } from '@/features/auth'
import { PendingDeletionNotice } from '@/features/privacy'
import { AppShellSkeleton } from '@/layouts/AppShellSkeleton'
import { toApiError } from '@/shared/api/errors'
import { ErrorState } from '@/shared/ui'
import { routes } from './routes'

const RECONNECT_EVERY_MS = 5_000

/**
 * Chỉ cho vào khi có phiên hợp lệ. Mất phiên thì về màn hình đăng nhập kèm lý do (hết hạn, bị đăng xuất
 * từ thiết bị khác) và trang đang xem để quay lại sau khi đăng nhập. Tài khoản đang chờ xóa chỉ thấy câu hỏi có hủy
 * yêu cầu xóa không (NCL-01-CN-009).
 *
 * Trong lúc hỏi phiên, phần khung ứng dụng hiện ngay dạng khung xương; mạng chập chờn thì khung vẫn ở đó kèm thông
 * báo đang kết nối lại và tự thử lại, có mạng là vào thẳng (NCL-02-CN-001, TC-05).
 */
export function ProtectedRoute() {
  const { t } = useTranslation()
  const location = useLocation()
  const session = useSession()
  const offline = session.isError && toApiError(session.error).code === 'NETWORK_ERROR'
  const { refetch } = session

  useEffect(() => {
    if (!offline) return undefined
    const retry = () => void refetch()
    const timer = window.setInterval(retry, RECONNECT_EVERY_MS)
    window.addEventListener('online', retry)
    return () => {
      window.clearInterval(timer)
      window.removeEventListener('online', retry)
    }
  }, [offline, refetch])

  if (session.isPending || offline) {
    return (
      <>
        {/* Khi mất mạng, khung xương tự hiện thông báo "đang kết nối lại" nên không đọc trùng ở đây. */}
        {!offline && (
          <p role="status" className="sr-only">
            {t('common.loading')}
          </p>
        )}
        <AppShellSkeleton reconnecting={offline} />
      </>
    )
  }
  if (session.isError) {
    return (
      <div className="grid min-h-dvh place-items-center bg-paper p-6">
        <ErrorState
          title={t('errors.INTERNAL_ERROR')}
          onRetry={() => void refetch()}
          retrying={session.isFetching}
          requestId={toApiError(session.error).requestId}
          className="w-full max-w-md"
        />
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
