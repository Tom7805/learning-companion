import { zodResolver } from '@hookform/resolvers/zod'
import { Eye, EyeOff, MailWarning } from 'lucide-react'
import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { useForm } from 'react-hook-form'
import { Trans, useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router'
import { routes } from '@/app/routes'
import { detailNumber, toApiError, type ApiError } from '@/shared/api/errors'
import { deadlineIn } from '@/shared/hooks/useCountdown'
import { Alert, Button, Checkbox, CtaButton, TextField } from '@/shared/ui'
import { useLogin, useResendVerification } from '../api/queries'
import { loginSchema, type LoginFormValues } from '../lib/loginSchema'
import { pendingVerification } from '../lib/pendingVerification'
import type { Account } from '../types'
import { LockoutNotice } from './LockoutNotice'

const WARN_WHEN_ATTEMPTS_LEFT = 2

interface LoginFormProps {
  /** Thông báo đặt trên biểu mẫu, ví dụ phiên vừa hết hạn. */
  notice?: ReactNode
  onLoggedIn: (account: Account) => void
}

export function LoginForm({ notice, onLoggedIn }: LoginFormProps) {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const login = useLogin()
  const resend = useResendVerification()
  const [error, setError] = useState<ApiError | null>(null)
  const [lockedUntil, setLockedUntil] = useState<number | null>(null)
  const [unlocked, setUnlocked] = useState(false)
  const [showPassword, setShowPassword] = useState(false)
  // Mỗi lần cần đưa tiêu điểm về ô mật khẩu (sau lỗi, hết thời gian khóa) thì tăng bộ đếm;
  // effect focus khi biểu mẫu đã mở khóa và ghi nhớ yêu cầu đã xử lý.
  const [focusRequest, setFocusRequest] = useState(0)
  const handledFocusRequest = useRef(0)
  // Thông báo đầu trang (đã đăng xuất, phiên hết hạn) chỉ còn ý nghĩa cho tới lần gửi đầu tiên.
  const [submitted, setSubmitted] = useState(false)

  const {
    register,
    handleSubmit,
    setValue,
    setFocus,
    getValues,
    setError: setFieldError,
    formState: { errors },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    mode: 'onTouched',
    defaultValues: { email: '', password: '', rememberDevice: false },
  })

  const pending = login.isPending
  const locked = lockedUntil !== null

  // Chỉ focus được khi fieldset đã mở khóa sau lần gửi.
  useEffect(() => {
    if (focusRequest > handledFocusRequest.current && !pending && !locked) {
      handledFocusRequest.current = focusRequest
      setFocus('password')
    }
  }, [focusRequest, pending, locked, setFocus])

  const handleUnlocked = useCallback(() => {
    setFocusRequest((count) => count + 1)
    setLockedUntil(null)
    setUnlocked(true)
  }, [])

  const onSubmit = handleSubmit(async (values) => {
    setSubmitted(true)
    setError(null)
    setUnlocked(false)
    try {
      const account = await login.mutateAsync(values)
      onLoggedIn(account)
    } catch (caught) {
      const apiError = toApiError(caught)
      setValue('password', '')
      if (apiError.code === 'ACCOUNT_LOCKED') {
        setLockedUntil(deadlineIn(detailNumber(apiError, 'retryAfterSeconds') ?? 900))
        return
      }
      const fieldErrors = apiError.fieldErrors.filter((item) => item.field === 'email' || item.field === 'password')
      if (apiError.code === 'VALIDATION_FAILED' && fieldErrors.length > 0) {
        fieldErrors.forEach((item) =>
          setFieldError(item.field as 'email' | 'password', { type: 'server', message: item.message }),
        )
        return
      }
      setFocusRequest((count) => count + 1)
      setError(apiError)
    }
  })

  const resendVerification = async () => {
    const email = getValues('email').trim()
    try {
      const result = await resend.mutateAsync({ email })
      const next = { email, resendAvailableAt: deadlineIn(result.resendAvailableInSeconds) }
      pendingVerification.save(next)
      navigate(routes.checkEmail, { state: next })
    } catch (caught) {
      setError(toApiError(caught))
    }
  }

  const message = (text?: string) => (text && i18n.exists(text) ? t(text) : text)
  const attemptsLeft = detailNumber(error, 'remainingAttempts')

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-6" aria-describedby="login-subtitle">
      <header className="flex flex-col gap-3">
        <h1 className="text-[28px] leading-tight font-normal tracking-tight sm:text-[32px]">
          {t('login.titleLine1')}
          <br />
          <Trans i18nKey="login.titleLine2" components={{ strong: <strong className="font-semibold" /> }} />
        </h1>
        <p id="login-subtitle" className="text-[15px] leading-relaxed text-muted">
          {t('login.subtitle')}
        </p>
      </header>

      {!submitted && notice}
      {locked && <LockoutNotice lockedUntil={lockedUntil} onUnlocked={handleUnlocked} />}
      {unlocked && <Alert tone="success">{t('login.locked.unlocked')}</Alert>}
      {error?.code === 'INVALID_CREDENTIALS' && (
        <Alert tone="danger" data-testid="login-error">
          <p>{t('login.invalid')}</p>
          {attemptsLeft !== undefined && attemptsLeft <= WARN_WHEN_ATTEMPTS_LEFT && (
            <p className="mt-1 font-medium" data-testid="attempts-left">
              {t('login.attemptsLeft', { count: attemptsLeft })}
            </p>
          )}
        </Alert>
      )}
      {error?.code === 'EMAIL_NOT_VERIFIED' && (
        <Alert tone="warning" data-testid="login-error">
          <p>{t('login.notVerified.body')}</p>
          <Button
            variant="dark"
            className="mt-3"
            loading={resend.isPending}
            onClick={() => void resendVerification()}
            icon={<MailWarning className="size-4" aria-hidden="true" />}
          >
            {t('login.notVerified.resend')}
          </Button>
        </Alert>
      )}
      {error && !['INVALID_CREDENTIALS', 'EMAIL_NOT_VERIFIED'].includes(error.code) && (
        <Alert tone="danger" data-testid="login-error">
          <p>
            {error.code === 'ACCOUNT_UNAVAILABLE'
              ? t('login.unavailable')
              : t(`errors.${error.code}`, { defaultValue: t('errors.INTERNAL_ERROR') })}
          </p>
          {error.requestId && <p className="mt-1 text-xs text-muted">{t('errors.requestId', { id: error.requestId })}</p>}
        </Alert>
      )}

      <fieldset disabled={pending || locked} className="flex flex-col gap-5">
        <TextField
          label={t('register.email.label')}
          placeholder={t('register.email.placeholder')}
          type="email"
          inputMode="email"
          autoComplete="username"
          autoCapitalize="none"
          spellCheck={false}
          error={message(errors.email?.message)}
          {...register('email')}
        />
        <TextField
          label={t('register.password.label')}
          placeholder={t('login.passwordPlaceholder')}
          type={showPassword ? 'text' : 'password'}
          autoComplete="current-password"
          autoCapitalize="none"
          spellCheck={false}
          error={message(errors.password?.message)}
          trailing={
            <button
              type="button"
              onClick={() => setShowPassword((shown) => !shown)}
              aria-pressed={showPassword}
              aria-label={showPassword ? t('register.password.hide') : t('register.password.show')}
              className="grid size-9 place-items-center rounded-lg text-muted transition-colors hover:bg-ink/5 hover:text-ink"
            >
              {showPassword ? <EyeOff className="size-5" aria-hidden="true" /> : <Eye className="size-5" aria-hidden="true" />}
            </button>
          }
          {...register('password')}
        />
        <div className="flex flex-col gap-1">
          <Checkbox label={t('login.remember')} aria-describedby="remember-hint" {...register('rememberDevice')} />
          <p id="remember-hint" className="pl-9 text-[13px] leading-snug text-muted">
            {t('login.rememberHint')}
          </p>
        </div>
      </fieldset>

      <CtaButton type="submit" loading={pending} disabled={locked} hint={pending ? undefined : t('login.submitHint')}>
        {pending ? t('login.submitting') : t('login.submit')}
      </CtaButton>

      <p className="text-center text-sm text-muted">
        {t('login.noAccount')}{' '}
        <Link to={routes.register} className="font-medium text-accent-ink underline-offset-4 hover:underline">
          {t('login.register')}
        </Link>
      </p>
    </form>
  )
}
