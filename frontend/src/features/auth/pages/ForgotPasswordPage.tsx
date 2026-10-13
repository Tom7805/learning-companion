import { zodResolver } from '@hookform/resolvers/zod'
import { ArrowLeft, ExternalLink, KeyRound, Mail, RotateCw } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Trans, useTranslation } from 'react-i18next'
import { Link, useLocation, useNavigate } from 'react-router'
import { z } from 'zod'
import { routes } from '@/app/routes'
import { toApiError, type ApiError } from '@/shared/api/errors'
import { deadlineIn, formatMinutesSeconds, useCountdown } from '@/shared/hooks/useCountdown'
import { usePageHeading } from '@/shared/hooks/usePageHeading'
import { Alert, Button, CtaButton, TextField, buttonClasses } from '@/shared/ui'
import { useForgotPassword } from '../api/queries'
import { StatusIcon } from '../components/StatusIcon'
import { detectMailProvider } from '../lib/mailProvider'
import { emailField } from '../lib/passwordSchema'
import type { ForgotPasswordSent } from '../types'

const schema = z.object({ email: emailField })
type Values = z.infer<typeof schema>

interface ForgotLocationState {
  /** Địa chỉ đã gõ ở màn hình đăng nhập, điền sẵn để khỏi gõ lại. */
  email?: string
  sent?: ForgotPasswordSent
}

/**
 * Quên mật khẩu (NCL-01-CN-003). Phản hồi luôn giống hệt nhau dù địa chỉ có tài khoản hay không,
 * nên trang này không giúp ai dò ra địa chỉ nào đã đăng ký.
 */
export function ForgotPasswordPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const state = (location.state as ForgotLocationState | null) ?? {}

  // Lưu trạng thái "đã gửi" vào lịch sử trình duyệt: tải lại trang vẫn giữ, không cần bộ nhớ riêng.
  const showSent = (sent: ForgotPasswordSent) => navigate(routes.forgotPassword, { replace: true, state: { sent } })
  const showForm = (email: string) => navigate(routes.forgotPassword, { replace: true, state: { email } })

  return state.sent ? (
    <SentView sent={state.sent} onResent={showSent} onChangeEmail={() => showForm(state.sent!.email)} />
  ) : (
    <RequestForm defaultEmail={state.email ?? ''} onSent={showSent} />
  )
}

function RequestForm({ defaultEmail, onSent }: { defaultEmail: string; onSent: (sent: ForgotPasswordSent) => void }) {
  const { t, i18n } = useTranslation()
  const forgot = useForgotPassword()
  const [error, setError] = useState<ApiError | null>(null)
  usePageHeading(t('forgot.title'))
  const {
    register,
    handleSubmit,
    setFocus,
    formState: { errors },
  } = useForm<Values>({ resolver: zodResolver(schema), mode: 'onTouched', defaultValues: { email: defaultEmail } })

  const onSubmit = handleSubmit(async ({ email }) => {
    setError(null)
    try {
      const result = await forgot.mutateAsync({ email })
      onSent({ email, resendAvailableAt: deadlineIn(result.resendAvailableInSeconds) })
    } catch (caught) {
      setError(toApiError(caught))
    }
  })

  // Sau lỗi máy chủ, đưa tiêu điểm về ô thư khi biểu mẫu đã mở khóa.
  useEffect(() => {
    if (error && !forgot.isPending) setFocus('email')
  }, [error, forgot.isPending, setFocus])

  const message = (text?: string) => (text && i18n.exists(text) ? t(text) : text)

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-6" aria-describedby="forgot-subtitle">
      <StatusIcon tone="lilac">
        <KeyRound className="size-7" strokeWidth={2} />
      </StatusIcon>
      <header className="flex flex-col gap-3">
        <h1 className="text-[28px] leading-tight font-normal tracking-tight sm:text-[32px]">
          {t('forgot.titleLine1')}
          <br />
          <Trans i18nKey="forgot.titleLine2" components={{ strong: <strong className="font-semibold" /> }} />
        </h1>
        <p id="forgot-subtitle" className="text-[15px] leading-relaxed text-muted">
          {t('forgot.subtitle')}
        </p>
      </header>

      {error && (
        <Alert tone="danger" data-testid="forgot-error">
          <p>{t(`errors.${error.code}`, { defaultValue: t('errors.INTERNAL_ERROR') })}</p>
          {error.requestId && <p className="mt-1 text-xs text-muted">{t('errors.requestId', { id: error.requestId })}</p>}
        </Alert>
      )}

      <fieldset disabled={forgot.isPending} className="flex flex-col gap-5">
        <TextField
          label={t('register.email.label')}
          placeholder={t('register.email.placeholder')}
          type="email"
          inputMode="email"
          autoComplete="username"
          autoCapitalize="none"
          spellCheck={false}
          autoFocus
          error={message(errors.email?.message)}
          {...register('email')}
        />
      </fieldset>

      <CtaButton type="submit" loading={forgot.isPending} hint={forgot.isPending ? undefined : t('forgot.submitHint')}>
        {forgot.isPending ? t('forgot.submitting') : t('forgot.submit')}
      </CtaButton>

      <p className="text-center text-sm text-muted">
        {t('forgot.rememberedIt')}{' '}
        <Link to={routes.login} className="font-medium text-accent-ink underline-offset-4 hover:underline">
          {t('forgot.backToLogin')}
        </Link>
      </p>
    </form>
  )
}

interface SentViewProps {
  sent: ForgotPasswordSent
  onResent: (sent: ForgotPasswordSent) => void
  onChangeEmail: () => void
}

function SentView({ sent, onResent, onChangeEmail }: SentViewProps) {
  const { t } = useTranslation()
  const forgot = useForgotPassword()
  const [resentCount, setResentCount] = useState(0)
  const [error, setError] = useState<ApiError | null>(null)
  const secondsLeft = useCountdown(sent.resendAvailableAt)
  const heading = usePageHeading(t('forgot.sent.title'), sent.email)
  const provider = detectMailProvider(sent.email)

  const resend = async () => {
    setError(null)
    try {
      const result = await forgot.mutateAsync({ email: sent.email })
      onResent({ email: sent.email, resendAvailableAt: deadlineIn(result.resendAvailableInSeconds) })
      setResentCount((count) => count + 1)
    } catch (caught) {
      setError(toApiError(caught))
    }
  }

  return (
    <div className="flex flex-col gap-7">
      <StatusIcon tone="sun" dot>
        <Mail className="size-7" strokeWidth={2} />
      </StatusIcon>

      <header className="flex flex-col gap-3">
        <h1 ref={heading} tabIndex={-1} className="text-[28px] leading-tight tracking-tight outline-none sm:text-[32px]">
          {t('forgot.sent.title')}
        </h1>
        <p className="text-[15px] leading-relaxed text-ink-soft" data-testid="forgot-sent-body">
          <Trans
            i18nKey="forgot.sent.body"
            values={{ email: sent.email }}
            components={{ email: <strong className="font-semibold text-ink" /> }}
          />
        </p>
        <p className="text-sm text-muted">{t('forgot.sent.expires')}</p>
      </header>

      {resentCount > 0 && (
        <Alert tone="success" key={resentCount} data-testid="forgot-resent">
          {t('forgot.sent.resent')}
        </Alert>
      )}
      {error && <Alert tone="danger">{t(`errors.${error.code}`, { defaultValue: t('errors.INTERNAL_ERROR') })}</Alert>}

      <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap">
        {provider && (
          <a href={provider.url} target="_blank" rel="noopener noreferrer" className={buttonClasses('dark', 'lg', 'sm:flex-1')}>
            <ExternalLink className="size-4" aria-hidden="true" />
            {t('checkEmail.openMailbox', { provider: provider.name })}
          </a>
        )}
        <Button
          variant="outline"
          size="lg"
          className="whitespace-nowrap tabular-nums sm:min-w-[190px] sm:flex-1"
          onClick={() => void resend()}
          disabled={secondsLeft > 0}
          loading={forgot.isPending}
          icon={<RotateCw className="size-4" aria-hidden="true" />}
          data-testid="forgot-resend"
        >
          {forgot.isPending
            ? t('forgot.sent.resending')
            : secondsLeft > 0
              ? t('checkEmail.resendIn', { time: formatMinutesSeconds(secondsLeft) })
              : t('forgot.sent.resend')}
        </Button>
      </div>

      <section className="rounded-card border-[1.5px] border-ink bg-lilac-soft p-5" aria-labelledby="forgot-tips-title">
        <h2 id="forgot-tips-title" className="font-medium">
          {t('forgot.sent.tipsTitle')}
        </h2>
        <ul className="mt-2 flex list-disc flex-col gap-1.5 pl-5 text-sm leading-relaxed text-ink-soft">
          {(t('forgot.sent.tips', { returnObjects: true }) as string[]).map((tip) => (
            <li key={tip}>{tip}</li>
          ))}
        </ul>
      </section>

      <div className="flex flex-wrap items-center justify-between gap-3 text-sm">
        <button type="button" onClick={onChangeEmail} className="font-medium text-accent-ink underline-offset-4 hover:underline">
          {t('forgot.sent.changeEmail')}
        </button>
        <Link
          to={routes.login}
          state={{ email: sent.email }}
          className="-my-2 inline-flex items-center gap-1.5 py-2 text-muted underline-offset-4 hover:text-ink hover:underline"
        >
          <ArrowLeft className="size-4" aria-hidden="true" />
          {t('forgot.backToLogin')}
        </Link>
      </div>
    </div>
  )
}
