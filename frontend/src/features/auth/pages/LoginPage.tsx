import { useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Navigate, useLocation, useNavigate, useSearchParams } from 'react-router'
import { routes } from '@/app/routes'
import { queryKeys } from '@/shared/api/queryKeys'
import { usePageHeading } from '@/shared/hooks/usePageHeading'
import { Alert } from '@/shared/ui'
import { useSession } from '../api/queries'
import { LoginForm } from '../components/LoginForm'
import { pendingVerification } from '../lib/pendingVerification'
import type { Account, SessionLostReason, SessionStatus } from '../types'

interface LoginLocationState {
  from?: string
  reason?: SessionLostReason
  /** Địa chỉ điền sẵn, ví dụ khi quay lại từ màn hình quên mật khẩu. */
  email?: string
  /** Vừa đặt lại mật khẩu qua thư. */
  passwordReset?: boolean
}

export function LoginPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const location = useLocation()
  const [params] = useSearchParams()
  const queryClient = useQueryClient()
  const session = useSession()
  usePageHeading(t('login.title'))

  const state = (location.state as LoginLocationState | null) ?? {}
  const from = state.from && state.from.startsWith('/') && !state.from.startsWith('//') ? state.from : routes.home

  if (session.data?.authenticated) {
    return <Navigate to={from} replace />
  }

  const notice = state.passwordReset ? (
    <Alert tone="success" data-testid="session-notice">
      {t('login.reason.passwordReset')}
    </Alert>
  ) : state.reason ? (
    <Alert tone="warning" data-testid="session-notice">
      {t(`login.reason.${state.reason}`)}
    </Alert>
  ) : params.has('loggedOut') ? (
    <Alert tone="success" data-testid="session-notice">
      {t('login.reason.loggedOut')}
    </Alert>
  ) : undefined

  const handleLoggedIn = (account: Account) => {
    pendingVerification.clear()
    queryClient.setQueryData<SessionStatus>(queryKeys.session, { authenticated: true, account })
    navigate(from, { replace: true })
  }

  return <LoginForm notice={notice} defaultEmail={state.email} onLoggedIn={handleLoggedIn} />
}
