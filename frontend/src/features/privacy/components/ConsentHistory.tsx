import { CircleCheck, CircleSlash } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useDateFormat } from '@/shared/hooks/useDateFormat'
import { cn } from '@/shared/lib/cn'
import { Alert, Button, Skeleton } from '@/shared/ui'
import { useConsentHistory } from '../api/queries'

/** Lịch sử đồng ý và rút lại, mới nhất ở trên, kèm phiên bản văn bản và thời điểm. */
export function ConsentHistory({ accountId }: { accountId: string }) {
  const { t } = useTranslation()
  const format = useDateFormat()
  const history = useConsentHistory(accountId)

  return (
    <section
      aria-labelledby="consent-history-heading"
      className="flex flex-col gap-3 rounded-card border-[1.5px] border-ink bg-surface p-5 sm:p-6"
    >
      <h2 id="consent-history-heading" className="text-xl font-medium">
        {t('privacy.historyTitle')}
      </h2>
      {history.isPending && (
        <div className="flex flex-col gap-2" aria-busy="true">
          <Skeleton className="h-12" />
          <Skeleton className="h-12" />
        </div>
      )}
      {history.isError && (
        <Alert tone="danger">
          <p>{t('privacy.historyFailed')}</p>
          <Button variant="outline" className="mt-3" onClick={() => void history.refetch()}>
            {t('common.retry')}
          </Button>
        </Alert>
      )}
      {history.data && (
        <ol className="flex flex-col" data-testid="consent-history">
          {history.data.map((entry) => (
            <li key={entry.id} className="flex items-start gap-3 border-l-2 border-line py-2.5 pl-4" data-testid="history-entry">
              {entry.granted ? (
                <CircleCheck className="-ml-[1.6rem] mt-0.5 size-5 shrink-0 rounded-full bg-surface text-success" aria-hidden="true" />
              ) : (
                <CircleSlash className="-ml-[1.6rem] mt-0.5 size-5 shrink-0 rounded-full bg-surface text-muted" aria-hidden="true" />
              )}
              <div className="flex min-w-0 flex-col">
                <p className={cn('text-sm font-medium', !entry.granted && 'text-ink-soft')}>
                  {t(entry.granted ? 'privacy.history.granted' : 'privacy.history.withdrawn', {
                    purpose: t(`privacy.purposeName.${entry.purpose}`),
                  })}
                </p>
                <p className="text-[0.8125rem] text-muted">
                  <time dateTime={entry.recordedAt}>{format.dateTime(entry.recordedAt)}</time> ·{' '}
                  {t('privacy.history.version', { version: entry.documentVersion })}
                </p>
              </div>
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}
