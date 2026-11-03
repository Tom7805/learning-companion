import { Sparkles } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { routes } from '@/app/routes'
import { cn } from '@/shared/lib/cn'
import { useDisplayTimeZone } from '@/shared/lib/timezone'
import { formatResetTime, quotaLevel, remainingPercent } from '../lib/quota'
import type { QuotaStatus } from '../types'

interface QuotaMeterProps {
  ai: QuotaStatus['ai']
  /** "sidebar": thanh bên mở rộng hoặc ngăn kéo, có chữ và thanh tiến độ; "compact": chỉ vòng tròn kèm số. */
  variant: 'sidebar' | 'compact'
  onNavigate?: () => void
}

const FILL = { plenty: 'bg-sun', low: 'bg-accent', empty: 'bg-on-night/30' } as const
const STROKE = { plenty: 'stroke-sun', low: 'stroke-accent', empty: 'stroke-on-night/30' } as const

/**
 * Đồng hồ lượt trí tuệ nhân tạo trên khung ứng dụng (NCL-04-CN-003): còn bao nhiêu lượt hôm nay và lúc nào hồi lại.
 * Bấm vào mở trang chi tiết: phần nào tốn lượt, dung lượng lưu trữ và cách học không tốn lượt.
 */
export function QuotaMeter({ ai, variant, onNavigate }: QuotaMeterProps) {
  const { t, i18n } = useTranslation()
  const timeZone = useDisplayTimeZone()
  const level = quotaLevel(ai)
  const percent = remainingPercent(ai)
  const reset = formatResetTime(ai.resetsAt, i18n.language, timeZone)
  const resetText = reset.date
    ? t('quota.meter.resetsOn', { time: reset.time, date: reset.date })
    : t('quota.meter.resetsAt', { time: reset.time })
  const label = t('quota.meter.label', { remaining: ai.remainingUnits, daily: ai.dailyUnits, reset: resetText })
  const common = {
    to: routes.usage,
    onClick: onNavigate,
    'aria-label': label,
    'data-testid': 'quota-meter',
    'data-remaining': ai.remainingUnits,
    'data-level': level,
  }

  if (variant === 'compact') {
    const radius = 15
    const length = 2 * Math.PI * radius
    return (
      <Link
        {...common}
        className={cn(
          'relative grid size-11 shrink-0 place-items-center rounded-xl text-on-night outline-none transition-colors',
          'hover:bg-on-night/10 focus-visible:ring-4 focus-visible:ring-sun/60',
        )}
      >
        <svg viewBox="0 0 36 36" className="absolute size-9 -rotate-90" aria-hidden="true">
          <circle cx="18" cy="18" r={radius} className="fill-none stroke-on-night/15" strokeWidth="3" />
          <circle
            cx="18"
            cy="18"
            r={radius}
            className={cn('fill-none transition-[stroke-dashoffset] duration-500', STROKE[level])}
            strokeWidth="3"
            strokeLinecap="round"
            strokeDasharray={length}
            strokeDashoffset={length * (1 - percent / 100)}
          />
        </svg>
        <span aria-hidden="true" className="relative text-[0.75rem] font-bold tabular-nums">
          {ai.remainingUnits > 99 ? '99+' : ai.remainingUnits}
        </span>
      </Link>
    )
  }

  return (
    <Link
      {...common}
      className={cn(
        'flex w-full flex-col gap-2 rounded-2xl bg-on-night/10 px-3.5 py-3 text-on-night outline-none',
        'transition-colors hover:bg-on-night/15 focus-visible:ring-4 focus-visible:ring-sun/60',
      )}
    >
      <span className="flex items-center justify-between gap-2 text-sm" aria-hidden="true">
        <span className="flex items-center gap-2 font-medium">
          <Sparkles className="size-4 text-sun" />
          {t('quota.meter.title')}
        </span>
        <span className="tabular-nums">
          <strong className="text-base">{ai.remainingUnits}</strong>
          <span className="text-on-night/70">/{ai.dailyUnits}</span>
        </span>
      </span>
      <span className="h-1.5 overflow-hidden rounded-full bg-on-night/15" aria-hidden="true">
        <span
          className={cn('block h-full rounded-full transition-[width] duration-500', FILL[level])}
          style={{ width: `${percent}%` }}
        />
      </span>
      <span className="text-xs text-on-night/75" aria-hidden="true">
        {level === 'empty' ? t('quota.meter.emptyHint', { reset: resetText }) : resetText}
      </span>
    </Link>
  )
}
