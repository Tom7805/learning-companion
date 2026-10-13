import { zodResolver } from '@hookform/resolvers/zod'
import { CircleCheckBig, Clock3, KeyRound, RotateCw, Unlink } from 'lucide-react'
import { useCallback, useEffect, useId, useState, type ReactNode, type RefObject } from 'react'
import { useForm } from 'react-hook-form'
import { Trans, useTranslation } from 'react-i18next'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { routes } from '@/app/routes'
import { toApiError, type ApiError } from '@/shared/api/errors'
import { deadlineIn, formatMinutesSeconds, useCountdown } from '@/shared/hooks/useCountdown'
import { usePageHeading } from '@/shared/hooks/usePageHeading'
import { Alert, Button, CtaButton, Spinner, TextField, buttonClasses } from '@/shared/ui'
import { authApi } from '../api/authApi'
import { useForgotPassword, useResetPassword } from '../api/queries'
import { PasswordStrengthMeter } from '../components/PasswordStrengthMeter'
import { PasswordVisibilityToggle } from '../components/PasswordVisibilityToggle'
import { StatusIcon } from '../components/StatusIcon'
import { resetPasswordSchema, type ResetPasswordValues } from '../lib/passwordSchema'

type State =
  | { kind: 'checking' }
  | { kind: 'form' }
  | { kind: 'expired' }
  | { kind: 'invalid' }
  | { kind: 'failed'; code: string }
  | { kind: 'success'; email: string }

type HeadingRef = RefObject<HTMLHeadingElement | null>

/**
 * Hai lần gắn hiệu ứng liền nhau (StrictMode) chỉ gửi một yêu cầu kiểm tra. Kết quả không được giữ lại sau khi xong,
 * để mở lại đúng đường dẫn đó (ví dụ sau khi đã đặt mật khẩu) luôn được kiểm mới.
 */
const inflight = new Map<string, Promise<void>>()
function checkOnce(token: string) {
  let request = inflight.get(token)
  if (!request) {
    request = authApi.checkResetToken(token)
    inflight.set(token, request)
    request.then(
      () => inflight.delete(token),
      () => inflight.delete(token),
    )
  }
  return request
}

/** Đặt mật khẩu mới từ đường dẫn trong thư (NCL-01-CN-003): dùng một lần, hiệu lực 30 phút. */
export function ResetPasswordPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  // Giữ chuỗi bí mật trong bộ nhớ rồi xóa khỏi thanh địa chỉ để không lọt vào lịch sử trình duyệt.
  const [token] = useState(() => params.get('token')?.trim() || null)
  const [state, setState] = useState<State>(() => (token ? { kind: 'checking' } : { kind: 'invalid' }))
  const heading = usePageHeading(t(titleKey(state)), state.kind)

  useEffect(() => {
    if (params.has('token')) {
      navigate(routes.resetPassword, { replace: true })
    }
  }, [navigate, params])

  // Kiểm tra trước khi cho nhập: người học không phải gõ mật khẩu mới rồi mới biết đường dẫn đã hết hạn.
  const check = useCallback(() => {
    if (!token) return () => undefined
    let active = true
    checkOnce(token)
      .then(() => active && setState({ kind: 'form' }))
      .catch((error: unknown) => active && setState(stateFor(toApiError(error).code)))
    return () => {
      active = false
    }
  }, [token])

  useEffect(() => check(), [check])

  return (
    <div className="flex flex-col gap-7">
      {state.kind === 'checking' && (
        <>
          <StatusIcon tone="lilac">
            <Spinner className="size-7" />
          </StatusIcon>
          <h1 ref={heading} tabIndex={-1} className="text-[1.75rem] leading-tight tracking-tight outline-none">
            {t('reset.checking')}
          </h1>
        </>
      )}
      {state.kind === 'form' && (
        <ResetForm
          token={token!}
          headingRef={heading}
          onDone={(email) => setState({ kind: 'success', email })}
          onTokenProblem={(code) => setState(stateFor(code))}
        />
      )}
      {state.kind === 'success' && <Success email={state.email} headingRef={heading} />}
      {state.kind === 'expired' && <Expired token={token!} headingRef={heading} />}
      {state.kind === 'invalid' && <Invalid headingRef={heading} />}
      {state.kind === 'failed' && (
        <>
          <StatusIcon tone="danger">
            <Unlink className="size-7" />
          </StatusIcon>
          <Heading headingRef={heading} title={t('reset.failed.title')} />
          <Alert tone="danger">{t(`errors.${state.code}`, { defaultValue: t('errors.INTERNAL_ERROR') })}</Alert>
          <Button
            variant="dark"
            size="lg"
            onClick={() => {
              setState({ kind: 'checking' })
              check()
            }}
            icon={<RotateCw className="size-4" aria-hidden="true" />}
          >
            {t('verify.failed.retry')}
          </Button>
        </>
      )}
    </div>
  )
}

function titleKey(state: State) {
  switch (state.kind) {
    case 'checking':
      return 'reset.checking'
    case 'form':
      return 'reset.title'
    case 'success':
      return 'reset.success.title'
    case 'expired':
      return 'reset.expired.title'
    case 'invalid':
      return 'reset.invalid.title'
    default:
      return 'reset.failed.title'
  }
}

function stateFor(code: string): State {
  switch (code) {
    case 'RESET_TOKEN_EXPIRED':
      return { kind: 'expired' }
    case 'RESET_TOKEN_INVALID':
    case 'VALIDATION_FAILED':
      return { kind: 'invalid' }
    default:
      return { kind: 'failed', code }
  }
}

function Heading({ headingRef, title, children }: { headingRef: HeadingRef; title: string; children?: ReactNode }) {
  return (
    <header className="flex flex-col gap-3">
      <h1 ref={headingRef} tabIndex={-1} className="text-[1.75rem] leading-tight tracking-tight outline-none sm:text-[2rem]">
        {title}
      </h1>
      {children && <div className="text-[0.9375rem] leading-relaxed text-ink-soft">{children}</div>}
    </header>
  )
}

interface ResetFormProps {
  token: string
  headingRef: HeadingRef
  onDone: (email: string) => void
  onTokenProblem: (code: string) => void
}

function ResetForm({ token, headingRef, onDone, onTokenProblem }: ResetFormProps) {
  const { t, i18n } = useTranslation()
  const reset = useResetPassword()
  const [showPassword, setShowPassword] = useState(false)
  const [breachedPassword, setBreachedPassword] = useState<string | null>(null)
  const [formError, setFormError] = useState<ApiError | null>(null)
  const [focusRequest, setFocusRequest] = useState(false)
  const passwordId = useId()
  const confirmId = useId()
  const {
    register,
    handleSubmit,
    watch,
    setError,
    setFocus,
    getValues,
    trigger,
    formState: { errors, touchedFields, isSubmitted },
  } = useForm<ResetPasswordValues>({
    resolver: zodResolver(resetPasswordSchema),
    mode: 'onTouched',
    reValidateMode: 'onChange',
    defaultValues: { password: '', confirmPassword: '' },
  })
  const password = watch('password')
  const confirmPassword = watch('confirmPassword')
  const breached = breachedPassword !== null && breachedPassword === password
  const pending = reset.isPending

  useEffect(() => {
    if (focusRequest && !pending) {
      setFocus('password')
      setFocusRequest(false)
    }
  }, [focusRequest, pending, setFocus])

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      const result = await reset.mutateAsync({ token, password: values.password })
      onDone(result.email)
    } catch (caught) {
      const error = toApiError(caught)
      if (error.code === 'RESET_TOKEN_EXPIRED' || error.code === 'RESET_TOKEN_INVALID') {
        onTokenProblem(error.code)
        return
      }
      const passwordError = error.fieldErrors.find((item) => item.field === 'password')
      if (error.code === 'PASSWORD_BREACHED') setBreachedPassword(values.password)
      if (passwordError) {
        const key = `errors.${error.code.startsWith('PASSWORD_') ? error.code : passwordError.code}`
        setError('password', { type: 'server', message: i18n.exists(key) ? key : passwordError.message })
        setFocusRequest(true)
        return
      }
      setFormError(error)
    }
  })

  const message = (text?: string) => (text && i18n.exists(text) ? t(text) : text)
  const valid = (name: keyof ResetPasswordValues, value: string) =>
    Boolean((touchedFields[name] || isSubmitted) && !errors[name] && value)

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-6" aria-describedby="reset-subtitle">
      <StatusIcon tone="lilac">
        <KeyRound className="size-7" strokeWidth={2} />
      </StatusIcon>
      <header className="flex flex-col gap-3">
        <h1 ref={headingRef} tabIndex={-1} className="text-[1.75rem] leading-tight tracking-tight outline-none sm:text-[2rem]">
          {t('reset.title')}
        </h1>
        <p id="reset-subtitle" className="text-[0.9375rem] leading-relaxed text-muted">
          {t('reset.subtitle')}
        </p>
      </header>

      {formError && (
        <Alert tone="danger" data-testid="reset-error">
          <p>{t(`errors.${formError.code}`, { defaultValue: t('errors.INTERNAL_ERROR') })}</p>
          {formError.requestId && (
            <p className="mt-1 text-xs text-muted">{t('errors.requestId', { id: formError.requestId })}</p>
          )}
        </Alert>
      )}

      <fieldset disabled={pending} className="flex flex-col gap-5">
        <div className="flex flex-col gap-3">
          <TextField
            id={passwordId}
            label={t('security.change.newLabel')}
            placeholder={t('security.change.newPlaceholder')}
            type={showPassword ? 'text' : 'password'}
            autoComplete="new-password"
            autoCapitalize="none"
            spellCheck={false}
            error={message(errors.password?.message)}
            valid={valid('password', password) && !breached}
            validLabel={t('register.valid')}
            trailing={
              <PasswordVisibilityToggle
                shown={showPassword}
                onToggle={() => setShowPassword((shown) => !shown)}
                controls={`${passwordId} ${confirmId}`}
              />
            }
            {...register('password', {
              // Sửa mật khẩu sau khi đã nhập ô xác nhận thì báo khớp hay chưa ngay.
              onChange: () => getValues('confirmPassword') && void trigger('confirmPassword'),
            })}
          />
          {password && <PasswordStrengthMeter password={password} breached={breached} />}
        </div>
        <TextField
          id={confirmId}
          label={t('security.change.confirmLabel')}
          placeholder={t('security.change.confirmPlaceholder')}
          type={showPassword ? 'text' : 'password'}
          autoComplete="new-password"
          autoCapitalize="none"
          spellCheck={false}
          error={message(errors.confirmPassword?.message)}
          valid={valid('confirmPassword', confirmPassword)}
          validLabel={t('register.valid')}
          {...register('confirmPassword')}
        />
      </fieldset>

      <CtaButton type="submit" loading={pending} hint={pending ? undefined : t('reset.submitHint')}>
        {pending ? t('reset.submitting') : t('reset.submit')}
      </CtaButton>

      <BackToLogin />
    </form>
  )
}

function Success({ email, headingRef }: { email: string; headingRef: HeadingRef }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  return (
    <>
      <StatusIcon tone="success">
        <CircleCheckBig className="size-8 text-success" strokeWidth={2.25} />
      </StatusIcon>
      <Heading headingRef={headingRef} title={t('reset.success.title')}>
        <p data-testid="reset-success-body">
          <Trans
            i18nKey="reset.success.body"
            values={{ email }}
            components={{ strong: <strong className="font-semibold text-ink" /> }}
          />
        </p>
      </Heading>
      <CtaButton
        onClick={() => navigate(routes.login, { replace: true, state: { email, passwordReset: true } })}
        hint={email}
      >
        {t('reset.success.cta')}
      </CtaButton>
    </>
  )
}

function Expired({ token, headingRef }: { token: string; headingRef: HeadingRef }) {
  const { t } = useTranslation()
  const forgot = useForgotPassword()
  const [availableAt, setAvailableAt] = useState<number | null>(null)
  const [errorCode, setErrorCode] = useState<string | null>(null)
  const seconds = useCountdown(availableAt)

  const send = async () => {
    setErrorCode(null)
    try {
      const result = await forgot.mutateAsync({ token })
      setAvailableAt(deadlineIn(result.resendAvailableInSeconds))
    } catch (error) {
      setErrorCode(toApiError(error).code)
    }
  }

  return (
    <>
      <StatusIcon tone="sun">
        <Clock3 className="size-8" strokeWidth={2} />
      </StatusIcon>
      <Heading headingRef={headingRef} title={t('reset.expired.title')}>
        <p>{t('reset.expired.body')}</p>
      </Heading>
      {availableAt && (
        <Alert tone="success" data-testid="reset-resent">
          {t('reset.expired.sent')}
        </Alert>
      )}
      {errorCode && <Alert tone="danger">{t(`errors.${errorCode}`, { defaultValue: t('errors.INTERNAL_ERROR') })}</Alert>}
      <CtaButton onClick={() => void send()} loading={forgot.isPending} disabled={seconds > 0}>
        {seconds > 0 ? t('checkEmail.resendIn', { time: formatMinutesSeconds(seconds) }) : t('reset.expired.cta')}
      </CtaButton>
      <BackToLogin />
    </>
  )
}

function Invalid({ headingRef }: { headingRef: HeadingRef }) {
  const { t } = useTranslation()
  return (
    <>
      <StatusIcon tone="danger">
        <Unlink className="size-8" strokeWidth={2} />
      </StatusIcon>
      <Heading headingRef={headingRef} title={t('reset.invalid.title')}>
        <p>{t('reset.invalid.body')}</p>
      </Heading>
      <Link to={routes.forgotPassword} className={buttonClasses('dark', 'lg')}>
        {t('reset.invalid.cta')}
      </Link>
      <BackToLogin />
    </>
  )
}

function BackToLogin() {
  const { t } = useTranslation()
  return (
    <Link to={routes.login} className="-my-2 py-2 text-center text-sm text-muted underline-offset-4 hover:text-ink hover:underline">
      {t('forgot.backToLogin')}
    </Link>
  )
}
