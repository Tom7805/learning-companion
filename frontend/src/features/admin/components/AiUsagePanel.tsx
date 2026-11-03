import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { formatDateTime } from '@/shared/lib/date'
import { useDisplayTimeZone } from '@/shared/lib/timezone'
import { cn } from '@/shared/lib/cn'
import { Alert, Button, Skeleton } from '@/shared/ui'
import { useAiUsage } from '../api/queries'

const PERIODS = [1, 7, 30] as const

/** Chi phí lưu bằng phần triệu đô la; hiện bằng đô la với tối đa bốn chữ số lẻ. */
function formatCost(microUsd: number, language: string) {
  return new Intl.NumberFormat(language, {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 2,
    maximumFractionDigits: 4,
  }).format(microUsd / 1_000_000)
}

/**
 * Mức sử dụng lớp kết nối: tổng lời gọi, đơn vị, chi phí theo khả năng và nhà cung cấp, cùng các lời gọi gần nhất.
 * Chỉ có số liệu: lớp kết nối không ghi nội dung lời gọi.
 */
export function AiUsagePanel() {
  const { t, i18n } = useTranslation()
  const timeZone = useDisplayTimeZone()
  const [days, setDays] = useState<number>(7)
  const usage = useAiUsage(days)
  const number = (value: number) => new Intl.NumberFormat(i18n.language).format(value)

  return (
    <section aria-labelledby="ai-usage-title" className="flex flex-col gap-4" data-testid="ai-usage">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="flex flex-col gap-1">
          <h2 id="ai-usage-title" className="text-xl font-semibold">
            {t('aiGateway.usage.title')}
          </h2>
          <p className="text-sm text-muted">{t('aiGateway.usage.subtitle')}</p>
        </div>
        <div role="group" aria-label={t('aiGateway.usage.period')} className="flex gap-1">
          {PERIODS.map((period) => (
            <button
              key={period}
              type="button"
              aria-pressed={days === period}
              onClick={() => setDays(period)}
              className={cn(
                'rounded-full border-[1.5px] px-3.5 py-1.5 text-sm font-medium outline-none focus-visible:ring-4 focus-visible:ring-focus/25',
                days === period ? 'border-ink bg-sun' : 'border-line hover:border-ink',
              )}
            >
              {t('aiGateway.usage.days', { count: period })}
            </button>
          ))}
        </div>
      </div>

      {usage.isPending && <Skeleton className="h-40" />}
      {usage.isError && (
        <Alert tone="danger">
          <p>{t('aiGateway.usage.loadFailed')}</p>
          <Button variant="outline" className="mt-3" onClick={() => void usage.refetch()}>
            {t('common.retry')}
          </Button>
        </Alert>
      )}
      {usage.data && (
        <>
          <dl className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            {[
              ['calls', number(usage.data.totals.calls)],
              ['failures', number(usage.data.totals.failures)],
              ['units', `${number(usage.data.totals.inputUnits)} / ${number(usage.data.totals.outputUnits)}`],
              ['cost', formatCost(usage.data.totals.costMicroUsd, i18n.language)],
            ].map(([key, value]) => (
              <div
                key={key}
                className="flex flex-col gap-1 rounded-card border-[1.5px] border-ink bg-surface px-4 py-3"
              >
                <dt className="text-sm text-muted">{t(`aiGateway.usage.totals.${key}`)}</dt>
                <dd className="text-2xl font-semibold tracking-tight" data-testid={`ai-usage-${key}`}>
                  {value}
                </dd>
              </div>
            ))}
          </dl>

          <div
            // Bảng cuộn ngang trên màn hình hẹp: nhận tiêu điểm để cuộn được bằng bàn phím.
            tabIndex={0}
            role="region"
            aria-label={t('aiGateway.usage.byProvider')}
            className="overflow-x-auto rounded-card border-[1.5px] border-ink bg-surface outline-none focus-visible:ring-4 focus-visible:ring-focus/25"
          >
            <table className="w-full min-w-[640px] text-left text-sm">
              <caption className="sr-only">{t('aiGateway.usage.byProvider')}</caption>
              <thead className="border-b border-line text-xs text-muted">
                <tr>
                  <th scope="col" className="px-4 py-2.5 font-medium">
                    {t('aiGateway.usage.columns.capability')}
                  </th>
                  <th scope="col" className="px-4 py-2.5 font-medium">
                    {t('aiGateway.usage.columns.provider')}
                  </th>
                  <th scope="col" className="px-4 py-2.5 text-right font-medium">
                    {t('aiGateway.usage.columns.calls')}
                  </th>
                  <th scope="col" className="px-4 py-2.5 text-right font-medium">
                    {t('aiGateway.usage.columns.units')}
                  </th>
                  <th scope="col" className="px-4 py-2.5 text-right font-medium">
                    {t('aiGateway.usage.columns.latency')}
                  </th>
                  <th scope="col" className="px-4 py-2.5 text-right font-medium">
                    {t('aiGateway.usage.columns.cost')}
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {usage.data.rows.length === 0 && (
                  <tr>
                    <td colSpan={6} className="px-4 py-6 text-center text-muted">
                      {t('aiGateway.usage.empty')}
                    </td>
                  </tr>
                )}
                {usage.data.rows.map((row) => (
                  <tr key={`${row.capability}-${row.providerCode ?? 'none'}`}>
                    <td className="px-4 py-2.5">{t(`aiGateway.capabilities.${row.capability}`)}</td>
                    <td className="px-4 py-2.5">{row.providerCode ?? t('aiGateway.usage.noProvider')}</td>
                    <td className="px-4 py-2.5 text-right tabular-nums">
                      {number(row.calls)}
                      {row.failures > 0 && (
                        <span className="text-danger">
                          {' '}
                          · {t('aiGateway.usage.failed', { count: row.failures })}
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-2.5 text-right tabular-nums">
                      {number(row.inputUnits)} / {number(row.outputUnits)}
                    </td>
                    <td className="px-4 py-2.5 text-right tabular-nums">{number(row.averageLatencyMs)} ms</td>
                    <td className="px-4 py-2.5 text-right tabular-nums">
                      {formatCost(row.costMicroUsd, i18n.language)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="flex flex-col gap-2">
            <h3 className="font-semibold">{t('aiGateway.usage.recent')}</h3>
            <p className="text-sm text-muted">{t('aiGateway.usage.noContent')}</p>
            {usage.data.recentCalls.length === 0 ? (
              <p className="text-sm text-muted">{t('aiGateway.usage.empty')}</p>
            ) : (
              <ul
                className="divide-y divide-line rounded-card border-[1.5px] border-ink bg-surface px-4"
                data-testid="ai-recent-calls"
              >
                {usage.data.recentCalls.map((call) => (
                  <li key={call.id} className="flex flex-wrap items-center gap-x-4 gap-y-1 py-2.5 text-sm">
                    <span className="w-40 shrink-0 text-muted">
                      {formatDateTime(call.occurredAt, i18n.language, timeZone)}
                    </span>
                    <span className="min-w-0 flex-1">
                      {t(`aiGateway.capabilities.${call.capability}`)} ·{' '}
                      <code className="text-xs">{call.feature}</code>
                    </span>
                    <span>{call.providerCode ?? '—'}</span>
                    <span
                      className={cn(
                        'rounded-full border px-2 py-0.5 text-xs font-medium',
                        call.outcome === 'SUCCESS'
                          ? 'border-success/50 bg-success-soft'
                          : 'border-danger/50 bg-danger-soft',
                      )}
                    >
                      {t(`aiGateway.outcomes.${call.outcome}`)}
                      {call.usedFallback ? ` · ${t('aiGateway.usage.viaFallback')}` : ''}
                    </span>
                    <span className="tabular-nums text-muted">
                      {t('aiGateway.usage.callFacts', {
                        input: call.inputUnits,
                        output: call.outputUnits,
                        latency: call.latencyMs,
                        attempts: call.attempts,
                      })}
                    </span>
                    <span className="tabular-nums">{formatCost(call.costMicroUsd, i18n.language)}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </>
      )}
    </section>
  )
}
