import { Check, Circle, X } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/cn'
import { estimatePasswordStrength, passwordLength, PASSWORD_MIN_LENGTH } from '../lib/passwordStrength'

const segmentColors = ['bg-danger', 'bg-danger', 'bg-sun', 'bg-success', 'bg-success']

interface PasswordStrengthMeterProps {
  password: string
  /** Máy chủ vừa báo mật khẩu này nằm trong danh sách đã lộ. */
  breached?: boolean
  id?: string
}

export function PasswordStrengthMeter({ password, breached = false, id }: PasswordStrengthMeterProps) {
  const { t } = useTranslation()
  const level = breached ? 0 : estimatePasswordStrength(password)
  const levels = t('register.strength.levels', { returnObjects: true }) as string[]
  const longEnough = passwordLength(password) >= PASSWORD_MIN_LENGTH

  return (
    <div id={id} className="flex flex-col gap-2.5 rounded-field bg-paper/70 px-3.5 py-3">
      <div className="flex items-center justify-between gap-3 text-[13px]">
        <span className="text-muted">{t('register.strength.label')}</span>
        <span aria-live="polite" className="font-medium" data-testid="strength-label">
          {password ? levels[level] : '—'}
        </span>
      </div>
      <div className="grid grid-cols-4 gap-1.5" aria-hidden="true">
        {[1, 2, 3, 4].map((segment) => (
          <span
            key={segment}
            data-active={password && level >= segment ? 'true' : 'false'}
            className={cn(
              'h-1.5 rounded-full transition-colors duration-200',
              password && level >= segment ? segmentColors[level] : 'bg-line',
            )}
          />
        ))}
      </div>
      <ul className="flex flex-col gap-1 text-[13px]">
        <Rule state={longEnough ? 'pass' : 'pending'}>{t('register.password.ruleLength')}</Rule>
        <Rule state={breached ? 'fail' : 'pending'}>{t('register.password.ruleCommon')}</Rule>
      </ul>
      <p className="text-[13px] leading-snug text-muted">{t('register.password.tip')}</p>
    </div>
  )
}

function Rule({ state, children }: { state: 'pass' | 'fail' | 'pending'; children: string }) {
  const { t } = useTranslation()
  return (
    <li
      data-state={state}
      className={cn(
        'flex items-center gap-2',
        state === 'pass' && 'text-success',
        state === 'fail' && 'text-danger',
        state === 'pending' && 'text-muted',
      )}
    >
      {state === 'pass' && <Check className="size-4" strokeWidth={3} aria-hidden="true" />}
      {state === 'fail' && <X className="size-4" strokeWidth={3} aria-hidden="true" />}
      {state === 'pending' && <Circle className="size-3.5" aria-hidden="true" />}
      {children}
      <span className="sr-only">({t(`register.password.ruleState.${state}`)})</span>
    </li>
  )
}
