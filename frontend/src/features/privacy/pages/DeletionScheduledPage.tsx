import { Hourglass } from 'lucide-react'
import { useState } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { Link, useSearchParams } from 'react-router'
import { routes } from '@/app/routes'
import { StatusIcon } from '@/features/auth/components/StatusIcon'
import { useDateFormat } from '@/shared/hooks/useDateFormat'
import { usePageHeading } from '@/shared/hooks/usePageHeading'
import { Alert, buttonClasses } from '@/shared/ui'

/**
 * Trang công khai sau khi yêu cầu xóa tài khoản (NCL-01-CN-009): mọi phiên đã bị thu hồi nên trang không cần đăng
 * nhập. Hạn xóa đến từ tham số {@code until}; tham số hỏng thì chỉ nói "sau 30 ngày".
 */
export function DeletionScheduledPage() {
  const { t } = useTranslation()
  const format = useDateFormat()
  const [params] = useSearchParams()
  const [until] = useState(() => {
    const value = params.get('until')
    return value && !Number.isNaN(Date.parse(value)) ? value : null
  })
  const heading = usePageHeading(t('accountDeletion.scheduled.title'))

  return (
    <div className="flex flex-col gap-7" data-testid="deletion-scheduled">
      <StatusIcon tone="sun">
        <Hourglass className="size-8" />
      </StatusIcon>
      <header className="flex flex-col gap-3">
        <h1 ref={heading} tabIndex={-1} className="text-[28px] leading-tight tracking-tight outline-none sm:text-[32px]">
          {t('accountDeletion.scheduled.title')}
        </h1>
        <p className="text-[15px] leading-relaxed text-ink-soft">
          {until ? (
            <Trans
              i18nKey="accountDeletion.scheduled.body"
              values={{ date: format.dateTime(until) }}
              components={{ strong: <strong className="font-semibold text-ink" /> }}
            />
          ) : (
            t('accountDeletion.scheduled.bodyNoDate')
          )}
        </p>
      </header>
      <Alert tone="info">{t('accountDeletion.scheduled.changeMind')}</Alert>
      <p className="text-sm text-muted">{t('accountDeletion.scheduled.goodbye')}</p>
      <Link to={routes.login} className={buttonClasses('dark', 'lg')}>
        {t('accountDeletion.scheduled.toLogin')}
      </Link>
    </div>
  )
}
