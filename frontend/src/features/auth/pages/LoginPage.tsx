import { KeyRound } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link, Navigate } from 'react-router'
import { routes } from '@/app/routes'
import { usePageHeading } from '@/shared/hooks/usePageHeading'
import { Alert, buttonClasses } from '@/shared/ui'
import { useSession } from '../api/queries'
import { StatusIcon } from '../components/StatusIcon'

/** Chỗ giữ cho trang đăng nhập; biểu mẫu đăng nhập thuộc story NCL-01-CN-002. */
export function LoginPage() {
  const { t } = useTranslation()
  const session = useSession()
  const heading = usePageHeading(t('login.title'))

  if (session.data?.authenticated) {
    return <Navigate to={routes.home} replace />
  }

  return (
    <div className="flex flex-col gap-7">
      <StatusIcon tone="lilac">
        <KeyRound className="size-7" />
      </StatusIcon>
      <h1 ref={heading} tabIndex={-1} className="text-[28px] leading-tight tracking-tight outline-none sm:text-[32px]">
        {t('login.title')}
      </h1>
      <Alert tone="info">{t('login.comingSoon')}</Alert>
      <p className="text-sm text-muted">
        {t('login.noAccount')}{' '}
        <Link to={routes.register} className={buttonClasses('ghost', 'md', 'h-auto px-0 text-accent-ink underline-offset-4 hover:bg-transparent hover:underline')}>
          {t('login.register')}
        </Link>
      </p>
    </div>
  )
}
