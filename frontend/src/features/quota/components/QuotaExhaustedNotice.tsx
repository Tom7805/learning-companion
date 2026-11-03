import { BookOpen, Clock } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { routes } from '@/app/routes'
import { useDisplayTimeZone } from '@/shared/lib/timezone'
import { formatResetTime } from '../lib/quota'

const FREE_WAYS = ['library', 'review', 'notes', 'explain'] as const

/**
 * Báo hết lượt mà không chặn việc học (NCL-04-CN-003, QTN-13, QTN-14): giờ hồi lại và các cách học không tốn lượt.
 * Chức năng tốn lượt nhận lỗi AI_QUOTA_EXHAUSTED thì hiện khối này với {@code resetsAt} trong chi tiết lỗi.
 */
export function QuotaExhaustedNotice({ resetsAt, requiredUnits, remainingUnits }: {
  resetsAt?: string
  requiredUnits?: number
  remainingUnits?: number
}) {
  const { t, i18n } = useTranslation()
  const timeZone = useDisplayTimeZone()
  const reset = resetsAt ? formatResetTime(resetsAt, i18n.language, timeZone) : undefined
  const partial = remainingUnits !== undefined && remainingUnits > 0 && requiredUnits !== undefined
  return (
    <div
      role="status"
      data-testid="quota-exhausted"
      className="flex flex-col gap-4 rounded-card border-[1.5px] border-ink bg-sun-soft p-5"
    >
      <div className="flex flex-col gap-1">
        <h2 className="text-lg font-semibold">
          {partial ? t('quota.exhausted.partialTitle') : t('quota.exhausted.title')}
        </h2>
        <p className="leading-relaxed text-ink-soft">
          {partial
            ? t('quota.exhausted.partialBody', { required: requiredUnits, remaining: remainingUnits })
            : t('quota.exhausted.body')}
        </p>
        {reset && (
          <p className="flex items-center gap-2 font-medium" data-testid="quota-resets-at">
            <Clock className="size-4" aria-hidden="true" />
            {reset.date
              ? t('quota.exhausted.resetsOn', { time: reset.time, date: reset.date })
              : t('quota.exhausted.resetsAt', { time: reset.time })}
          </p>
        )}
      </div>
      <div className="flex flex-col gap-2">
        <h3 className="text-sm font-semibold uppercase tracking-wide text-muted">{t('quota.free.title')}</h3>
        <ul className="flex flex-col gap-1.5 text-sm leading-relaxed" data-testid="quota-free-ways">
          {FREE_WAYS.map((way) => (
            <li key={way} className="flex gap-2">
              <span aria-hidden="true">•</span>
              {way === 'library' ? (
                <Link to={routes.library} className="font-medium underline underline-offset-2">
                  {t('quota.free.library')}
                </Link>
              ) : (
                <span>{t(`quota.free.${way}`)}</span>
              )}
            </li>
          ))}
        </ul>
      </div>
      <p className="flex items-center gap-2 text-sm text-muted">
        <BookOpen className="size-4 shrink-0" aria-hidden="true" />
        {t('quota.exhausted.neverBlocks')}
      </p>
    </div>
  )
}
