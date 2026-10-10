import { LockKeyhole } from 'lucide-react'
import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { formatMinutesSeconds, useCountdown } from '@/shared/hooks/useCountdown'

interface LockoutNoticeProps {
  lockedUntil: number
  onUnlocked: () => void
}

/** Khóa tạm sau 5 lần nhập sai: hiện thời gian chờ còn lại và tự mở lại khi hết giờ. */
export function LockoutNotice({ lockedUntil, onUnlocked }: LockoutNoticeProps) {
  const { t } = useTranslation()
  const seconds = useCountdown(lockedUntil)
  const notified = useRef(false)

  useEffect(() => {
    if (seconds === 0 && !notified.current) {
      notified.current = true
      onUnlocked()
    }
  }, [seconds, onUnlocked])

  return (
    <div
      role="alert"
      data-testid="lockout-notice"
      className="flex items-start gap-4 rounded-card border-[1.5px] border-ink bg-sun-soft p-5 animate-fade-up"
    >
      <span
        aria-hidden="true"
        className="grid size-12 shrink-0 place-items-center rounded-2xl border-[1.5px] border-ink bg-sun"
      >
        <LockKeyhole className="size-6" />
      </span>
      <div className="flex flex-col gap-1">
        <p className="font-semibold">{t('login.locked.title')}</p>
        <p className="text-sm leading-relaxed text-ink-soft">{t('login.locked.body')}</p>
        <p className="text-3xl font-semibold tabular-nums tracking-tight" data-testid="lockout-countdown">
          {formatMinutesSeconds(seconds)}
        </p>
      </div>
    </div>
  )
}
