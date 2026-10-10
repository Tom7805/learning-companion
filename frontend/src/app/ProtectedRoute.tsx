import { useTranslation } from 'react-i18next'
import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuth } from '@/features/auth'
import { Button, Spinner } from '@/shared/ui'
import { routes } from './routes'

/** Chỉ cho vào khi có phiên hợp lệ; chưa đăng nhập thì về trang đăng ký (đăng nhập thuộc story sau). */
export function ProtectedRoute() {
  const { t } = useTranslation()
  const location = useLocation()
  const { isAuthenticated, isLoading, isError, refetch } = useAuth()

  if (isLoading) {
    return (
      <div className="grid min-h-dvh place-items-center">
        <Spinner className="size-8" label={t('common.loading')} />
      </div>
    )
  }
  if (isError) {
    return (
      <div className="grid min-h-dvh place-items-center gap-4 p-6 text-center">
        <p>{t('errors.NETWORK_ERROR')}</p>
        <Button variant="dark" onClick={() => void refetch()}>
          {t('verify.failed.retry')}
        </Button>
      </div>
    )
  }
  if (!isAuthenticated) {
    return <Navigate to={routes.register} replace state={{ from: location.pathname }} />
  }
  return <Outlet />
}
