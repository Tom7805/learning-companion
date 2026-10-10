import { ExternalLink, Mail, RotateCw } from 'lucide-react'
import { useState } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { Link, useLocation, useNavigate } from 'react-router'
import { routes } from '@/app/routes'
import { toApiError, type ApiError } from '@/shared/api/errors'
import { deadlineIn, formatMinutesSeconds, useCountdown } from '@/shared/hooks/useCountdown'
import { usePageHeading } from '@/shared/hooks/usePageHeading'
import { Alert, Button, buttonClasses } from '@/shared/ui'
import { useResendVerification } from '../api/queries'
import { ResendByEmailForm } from '../components/ResendByEmailForm'
import { StatusIcon } from '../components/StatusIcon'
import { detectMailProvider } from '../lib/mailProvider'
import { pendingVerification } from '../lib/pendingVerification'
import type { PendingVerification } from '../types'

/**
 * Màn hình sau khi đăng ký. Nội dung giống hệt nhau dù địa chỉ đã có tài khoản hay chưa,
 * nên không ai dò được địa chỉ nào đã đăng ký.
 */
export function CheckEmailPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const location = useLocation()
  const [pending, setPending] = useState<PendingVerification | null>(
    () => (location.state as PendingVerification | null) ?? pendingVerification.load(),
  )
  const [sentCount, setSentCount] = useState(0)
  const [error, setError] = useState<ApiError | null>(null)
  const resend = useResendVerification()
  const secondsLeft = useCountdown(pending?.resendAvailableAt ?? null)
  const heading = usePageHeading(t('checkEmail.title'), pending?.email)

  const remember = (next: PendingVerification) => {
    pendingVerification.save(next)
    setPending(next)
  }

  if (!pending) {
    return (
      <div className="flex flex-col gap-8">
        <StatusIcon tone="sun" dot>
          <Mail className="size-7" strokeWidth={2} />
        </StatusIcon>
        <ResendByEmailForm
          onSent={(next) => {
            remember(next)
            setSentCount((count) => count + 1)
          }}
        />
      </div>
    )
  }

  const provider = detectMailProvider(pending.email)

  const handleResend = async () => {
    setError(null)
    try {
      const result = await resend.mutateAsync({ email: pending.email })
      remember({ ...pending, resendAvailableAt: deadlineIn(result.resendAvailableInSeconds) })
      setSentCount((count) => count + 1)
    } catch (caught) {
      setError(toApiError(caught))
    }
  }

  const changeEmail = () => {
    pendingVerification.clear()
    navigate(routes.register, { state: { prefill: { email: pending.email, displayName: pending.displayName } } })
  }

  return (
    <div className="flex flex-col gap-7">
      <StatusIcon tone="sun" dot>
        <Mail className="size-7" strokeWidth={2} />
      </StatusIcon>

      <header className="flex flex-col gap-3">
        <h1 ref={heading} tabIndex={-1} className="text-[28px] leading-tight tracking-tight outline-none sm:text-[32px]">
          {t('checkEmail.title')}
        </h1>
        <p className="text-[15px] leading-relaxed text-ink-soft" data-testid="check-email-body">
          <Trans
            i18nKey="checkEmail.body"
            values={{ email: pending.email }}
            components={{ email: <strong className="font-semibold break-words text-ink" /> }}
          />
        </p>
        <p className="text-sm text-muted">{t('checkEmail.expires')}</p>
      </header>

      {sentCount > 0 && (
        <Alert tone="success" key={sentCount} data-testid="resent-notice">
          {t('checkEmail.resent')}
        </Alert>
      )}
      {error && (
        <Alert tone="danger">{t(`errors.${error.code}`, { defaultValue: t('errors.INTERNAL_ERROR') })}</Alert>
      )}

      <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap">
        {provider && (
          <a
            href={provider.url}
            target="_blank"
            rel="noopener noreferrer"
            className={buttonClasses('dark', 'lg', 'sm:flex-1')}
          >
            <ExternalLink className="size-4" aria-hidden="true" />
            {t('checkEmail.openMailbox', { provider: provider.name })}
          </a>
        )}
        <Button
          variant="outline"
          size="lg"
          className="whitespace-nowrap tabular-nums sm:min-w-[190px] sm:flex-1"
          onClick={() => void handleResend()}
          disabled={secondsLeft > 0}
          loading={resend.isPending}
          icon={<RotateCw className="size-4" aria-hidden="true" />}
          data-testid="resend-button"
        >
          {resend.isPending
            ? t('checkEmail.resending')
            : secondsLeft > 0
              ? t('checkEmail.resendIn', { time: formatMinutesSeconds(secondsLeft) })
              : t('checkEmail.resend')}
        </Button>
      </div>

      <section className="rounded-card border-[1.5px] border-ink bg-lilac-soft p-5" aria-labelledby="tips-title">
        <h2 id="tips-title" className="font-medium">
          {t('checkEmail.tipsTitle')}
        </h2>
        <ul className="mt-2 flex list-disc flex-col gap-1.5 pl-5 text-sm leading-relaxed text-ink-soft">
          {(t('checkEmail.tips', { returnObjects: true }) as string[]).map((tip) => (
            <li key={tip}>{tip}</li>
          ))}
        </ul>
      </section>

      <div className="flex flex-wrap items-center justify-between gap-3 text-sm">
        <button
          type="button"
          onClick={changeEmail}
          className="font-medium text-accent-ink underline-offset-4 hover:underline"
        >
          {t('checkEmail.changeEmail')}
        </button>
        <Link to={routes.login} className="text-muted underline-offset-4 hover:text-ink hover:underline">
          {t('register.haveAccount')} {t('register.login')}
        </Link>
      </div>
    </div>
  )
}
