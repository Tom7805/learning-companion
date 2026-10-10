import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { routes } from '@/app/routes'
import { usePageHeading } from '@/shared/hooks/usePageHeading'
import { buttonClasses } from '@/shared/ui'

export function NotFoundPage() {
  const { t } = useTranslation()
  const heading = usePageHeading(t('notFound.title'))
  return (
    <div className="flex flex-col gap-5">
      <p className="text-6xl font-semibold text-accent" aria-hidden="true">
        404
      </p>
      <h1 ref={heading} tabIndex={-1} className="text-[28px] tracking-tight outline-none">
        {t('notFound.title')}
      </h1>
      <p className="text-muted">{t('notFound.body')}</p>
      <Link to={routes.home} className={buttonClasses('dark', 'lg', 'self-start')}>
        {t('notFound.home')}
      </Link>
    </div>
  )
}
