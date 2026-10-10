import { useTranslation } from 'react-i18next'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { routes } from '@/app/routes'
import { deadlineIn } from '@/shared/hooks/useCountdown'
import { usePageHeading } from '@/shared/hooks/usePageHeading'
import { useSession } from '../api/queries'
import { RegisterForm } from '../components/RegisterForm'
import { pendingVerification } from '../lib/pendingVerification'
import type { PendingVerification, RegistrationAccepted } from '../types'

interface RegisterLocationState {
  prefill?: { displayName?: string; email?: string }
}

export function RegisterPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const location = useLocation()
  const session = useSession()
  usePageHeading(t('register.submit'))

  if (session.data?.authenticated) {
    return <Navigate to={routes.home} replace />
  }

  const prefill = (location.state as RegisterLocationState | null)?.prefill

  const handleRegistered = (result: RegistrationAccepted, displayName: string) => {
    const pending: PendingVerification = {
      email: result.email,
      displayName,
      resendAvailableAt: deadlineIn(result.resendAvailableInSeconds),
    }
    pendingVerification.save(pending)
    navigate(routes.checkEmail, { state: pending })
  }

  return <RegisterForm prefill={prefill} onRegistered={handleRegistered} />
}
