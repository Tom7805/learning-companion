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

  const notice = state.reason ? (
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

  return <LoginForm notice={notice} onLoggedIn={handleLoggedIn} />
}
