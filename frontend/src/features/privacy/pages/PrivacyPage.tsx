import { useTranslation } from 'react-i18next'
import { useAuth } from '@/features/auth'
import { usePageHeading } from '@/shared/hooks/usePageHeading'
import { Alert, Button, Skeleton } from '@/shared/ui'
import { usePrivacyStatus } from '../api/queries'
import { ConsentHistory } from '../components/ConsentHistory'
import { ConsentToggles } from '../components/ConsentToggles'
import { TermsSummary } from '../components/TermsSummary'

/** Quyền riêng tư (NCL-01-CN-006): văn bản đang áp dụng, bật tắt từng mục đích và lịch sử đồng ý. */
export function PrivacyPage() {
  const { t } = useTranslation()
  const heading = usePageHeading(t('privacy.title'))
  const { account } = useAuth()
  const status = usePrivacyStatus(account?.id)

  return (
    <div className="flex flex-col gap-6">
      <header className="flex max-w-2xl flex-col gap-2">
        <h1 ref={heading} tabIndex={-1} className="text-3xl tracking-tight outline-none">
          {t('privacy.title')}
        </h1>
        <p className="leading-relaxed text-muted">{t('privacy.subtitle')}</p>
      </header>

      {status.isPending && (
        <div className="grid gap-6 lg:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)]" aria-busy="true">
          <Skeleton className="h-72" />
          <Skeleton className="h-72" />
        </div>
      )}
      {status.isError && (
        <Alert tone="danger">
          <p>{t('privacy.loadFailed')}</p>
          <Button variant="outline" className="mt-3" onClick={() => void status.refetch()}>
            {t('common.retry')}
          </Button>
        </Alert>
      )}
      {status.data && (
        <>
          <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)]">
            <ConsentToggles status={status.data} />
            <TermsSummary terms={status.data.terms} privacy={status.data.privacy} />
          </div>
          <ConsentHistory accountId={status.data.accountId} />
        </>
      )}
    </div>
  )
}
