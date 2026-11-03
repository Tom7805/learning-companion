import { HardDrive, RotateCcw, Sparkles } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { routes } from '@/app/routes'
import { usePageHeading } from '@/shared/hooks/usePageHeading'
import { formatBytes } from '@/shared/lib/format'
import { cn } from '@/shared/lib/cn'
import { useDisplayTimeZone } from '@/shared/lib/timezone'
import { Alert, Badge, Button, Card, Skeleton } from '@/shared/ui'
import { useQuotaStatus } from '../api/queries'
import { QuotaExhaustedNotice } from '../components/QuotaExhaustedNotice'
import { formatResetTime, quotaLevel, remainingPercent } from '../lib/quota'

const FREE_ACTIONS = ['read', 'review', 'quiz', 'notes', 'history'] as const

/**
 * Lượt trí tuệ nhân tạo và dung lượng lưu trữ của người học (NCL-04-CN-003): lượt còn lại hôm nay, giờ hồi lại theo
 * múi giờ của người học, phần nào tốn lượt và phần nào không, dung lượng đã dùng. Trang chỉ đọc nên luôn mở được,
 * kể cả khi đã hết lượt.
 */
export function UsagePage() {
  const { t, i18n } = useTranslation()
  const heading = usePageHeading(t('quota.page.title'))
  const timeZone = useDisplayTimeZone()
  const status = useQuotaStatus()
  const data = status.data

  return (
    <div className="flex flex-col gap-8">
      <header className="flex max-w-3xl flex-col gap-2">
        <h1 ref={heading} tabIndex={-1} className="text-3xl tracking-tight outline-none">
          {t('quota.page.title')}
        </h1>
        <p className="leading-relaxed text-muted">{t('quota.page.subtitle')}</p>
      </header>

      {status.isPending && (
        <div className="grid gap-4 lg:grid-cols-2" data-testid="quota-loading">
          <Skeleton className="h-48" />
          <Skeleton className="h-48" />
        </div>
      )}
      {status.isError && (
        <Alert tone="danger">
          <p>{t('quota.page.loadFailed')}</p>
          <Button variant="outline" className="mt-3" onClick={() => void status.refetch()}>
            {t('common.retry')}
          </Button>
        </Alert>
      )}

      {data && (
        <>
          <div className="grid gap-4 lg:grid-cols-2">
            <AiCard ai={data.ai} language={i18n.language} timeZone={timeZone} />
            <Card className="flex flex-col gap-4 p-5" data-testid="quota-storage">
              <h2 className="flex items-center gap-2 text-lg font-semibold">
                <HardDrive className="size-5" aria-hidden="true" />
                {t('quota.storage.title')}
              </h2>
              <p className="text-3xl font-semibold tabular-nums tracking-tight">
                {formatBytes(data.storage.usedBytes, i18n.language)}
                <span className="text-base font-normal text-muted">
                  {' '}
                  {t('quota.storage.of', { limit: formatBytes(data.storage.limitBytes, i18n.language) })}
                </span>
              </p>
              <Bar
                percent={data.storage.limitBytes > 0 ? (data.storage.usedBytes / data.storage.limitBytes) * 100 : 0}
                label={t('quota.storage.title')}
                tone={data.storage.remainingBytes * 10 <= data.storage.limitBytes ? 'accent' : 'ink'}
              />
              <p className="text-sm text-muted">
                {t('quota.storage.remaining', { size: formatBytes(data.storage.remainingBytes, i18n.language) })}
              </p>
              <Link to={routes.library} className="self-start text-sm font-medium underline underline-offset-2">
                {t('quota.storage.manage')}
              </Link>
            </Card>
          </div>

          {data.ai.remainingUnits <= 0 && <QuotaExhaustedNotice resetsAt={data.ai.resetsAt} />}

          <div className="grid gap-6 lg:grid-cols-[3fr_2fr]">
            <section aria-labelledby="quota-costs-title" className="flex flex-col gap-3">
              <div className="flex flex-col gap-1">
                <h2 id="quota-costs-title" className="text-xl font-semibold">
                  {t('quota.costs.title')}
                </h2>
                <p className="text-sm text-muted">{t('quota.costs.subtitle')}</p>
              </div>
              <ul
                className="divide-y divide-line rounded-card border-[1.5px] border-ink bg-surface px-4 sm:px-5"
                data-testid="quota-costs"
              >
                {data.costs.map((cost) => (
                  <li key={cost.task} className="flex items-center justify-between gap-3 py-3" data-task={cost.task}>
                    <span className="flex min-w-0 flex-col gap-1">
                      <span className="font-medium">{t(`quota.tasks.${cost.task}`, { defaultValue: cost.task })}</span>
                      <span>
                        <Badge tone={cost.weight === 'LONG' ? 'lilac' : 'sky'}>{t(`quota.weights.${cost.weight}`)}</Badge>
                      </span>
                    </span>
                    <span className="shrink-0 font-semibold tabular-nums" data-testid="quota-cost-units">
                      {t('quota.costs.units', { count: cost.units })}
                    </span>
                  </li>
                ))}
              </ul>
              <p className="flex items-start gap-2 text-sm text-muted">
                <RotateCcw className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
                {t('quota.costs.refund')}
              </p>
            </section>

            <section aria-labelledby="quota-free-title" className="flex flex-col gap-3">
              <div className="flex flex-col gap-1">
                <h2 id="quota-free-title" className="text-xl font-semibold">
                  {t('quota.freeActions.title')}
                </h2>
                <p className="text-sm text-muted">{t('quota.freeActions.subtitle')}</p>
              </div>
              <Card tone="lilac" className="p-5">
                <ul className="flex flex-col gap-2 text-sm leading-relaxed" data-testid="quota-free-actions">
                  {FREE_ACTIONS.map((action) => (
                    <li key={action} className="flex gap-2">
                      <span aria-hidden="true">✓</span>
                      {t(`quota.freeActions.${action}`)}
                    </li>
                  ))}
                </ul>
              </Card>
            </section>
          </div>
        </>
      )}
    </div>
  )
}

function AiCard({ ai, language, timeZone }: { ai: NonNullable<ReturnType<typeof useQuotaStatus>['data']>['ai'];
  language: string; timeZone?: string }) {
  const { t } = useTranslation()
  const reset = formatResetTime(ai.resetsAt, language, timeZone)
  const level = quotaLevel(ai)
  return (
    <Card tone="night" className="flex flex-col gap-4 p-5" data-testid="quota-ai">
      <h2 className="flex items-center gap-2 text-lg font-semibold">
        <Sparkles className="size-5 text-sun" aria-hidden="true" />
        {t('quota.ai.title')}
      </h2>
      <p className="text-3xl font-semibold tabular-nums tracking-tight" data-testid="quota-ai-remaining">
        {t('quota.ai.remaining', { remaining: ai.remainingUnits, daily: ai.dailyUnits })}
      </p>
      <Bar
        percent={remainingPercent(ai)}
        label={t('quota.ai.title')}
        tone={level === 'plenty' ? 'sun' : 'accent'}
        night
      />
      <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
        <dt className="text-on-night/75">{t('quota.ai.used')}</dt>
        <dd className="text-right font-medium tabular-nums" data-testid="quota-ai-used">
          {ai.usedUnits}
        </dd>
        <dt className="text-on-night/75">{t('quota.ai.reserved')}</dt>
        <dd className="text-right font-medium tabular-nums">{ai.reservedUnits}</dd>
        <dt className="text-on-night/75">{t('quota.ai.resets')}</dt>
        <dd className="text-right font-medium" data-testid="quota-ai-resets">
          {reset.date
            ? t('quota.ai.resetsOn', { time: reset.time, date: reset.date })
            : t('quota.ai.resetsAt', { time: reset.time })}
        </dd>
      </dl>
      <p className="text-xs text-on-night/75">
        {t('quota.ai.timeZone', { zone: ai.timeZone })}{' '}
        <Link to={{ search: '?settings=profile' }} className="font-medium text-on-night underline underline-offset-2">
          {t('quota.ai.changeTimeZone')}
        </Link>
      </p>
    </Card>
  )
}

function Bar({ percent, label, tone, night }: { percent: number; label: string; tone: 'sun' | 'accent' | 'ink';
  night?: boolean }) {
  const value = Math.max(0, Math.min(100, Math.round(percent)))
  return (
    <div
      role="progressbar"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={value}
      className={cn('h-2.5 overflow-hidden rounded-full', night ? 'bg-on-night/15' : 'bg-field')}
    >
      <div
        className={cn('h-full rounded-full transition-[width] duration-500',
          tone === 'sun' ? 'bg-sun' : tone === 'accent' ? 'bg-accent' : 'bg-ink')}
        style={{ width: `${value}%` }}
      />
    </div>
  )
}
