import { zodResolver } from '@hookform/resolvers/zod'
import { Eye, EyeOff } from 'lucide-react'
import { useEffect, useId, useState, type ReactNode } from 'react'
import { useForm } from 'react-hook-form'
import { Trans, useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { routes } from '@/app/routes'
import { toApiError, type ApiError } from '@/shared/api/errors'
import { Alert, Button, Checkbox, CtaButton, TextField } from '@/shared/ui'
import { useRegister } from '../api/queries'
import { registerSchema, type RegisterFormInput, type RegisterFormValues } from '../lib/registerSchema'
import type { RegistrationAccepted } from '../types'
import { useLegalCurrent } from '@/features/privacy/api/queries'
import { PasswordStrengthMeter } from './PasswordStrengthMeter'

type FieldName = keyof RegisterFormInput
const SERVER_FIELDS: FieldName[] = ['displayName', 'email', 'password', 'acceptTerms']

interface RegisterFormProps {
  prefill?: { displayName?: string; email?: string }
  onRegistered: (result: RegistrationAccepted, displayName: string) => void
}

export function RegisterForm({ prefill, onRegistered }: RegisterFormProps) {
  const { t, i18n } = useTranslation()
  const legal = useLegalCurrent()
  const registerMutation = useRegister()
  const [showPassword, setShowPassword] = useState(false)
  const [breachedPassword, setBreachedPassword] = useState<string | null>(null)
  const [formError, setFormError] = useState<ApiError | null>(null)
  // Ô cần nhận tiêu điểm sau lỗi máy chủ; chỉ focus được khi fieldset hết bị khóa.
  const [focusTarget, setFocusTarget] = useState<FieldName | null>(null)
  const passwordId = useId()
  const confirmId = useId()

  const {
    register,
    handleSubmit,
    watch,
    setError,
    setValue,
    setFocus,
    formState: { errors, touchedFields, isSubmitted, submitCount },
  } = useForm<RegisterFormInput, unknown, RegisterFormValues>({
    resolver: zodResolver(registerSchema),
    mode: 'onTouched',
    reValidateMode: 'onChange',
    defaultValues: {
      displayName: prefill?.displayName ?? '',
      email: prefill?.email ?? '',
      password: '',
      confirmPassword: '',
      acceptTerms: false,
    },
  })

  const values = watch()
  const breached = breachedPassword !== null && breachedPassword === values.password
  const pending = registerMutation.isPending

  useEffect(() => {
    if (focusTarget && !pending) {
      setFocus(focusTarget)
      setFocusTarget(null)
    }
  }, [focusTarget, pending, setFocus])

  /** Lời nhắn là khóa dịch (phía máy khách) hoặc câu tiếng Việt từ máy chủ. */
  const message = (text?: string) => (text && i18n.exists(text) ? t(text) : text)
  const isValid = (name: FieldName) =>
    Boolean((touchedFields[name] || isSubmitted) && !errors[name] && values[name])

  const onSubmit = handleSubmit(async (form) => {
    setFormError(null)
    const terms = legal.data ?? (await legal.refetch()).data
    if (!terms) {
      setFormError({ code: 'NETWORK_ERROR', message: '', status: 0, fieldErrors: [] })
      return
    }
    try {
      const result = await registerMutation.mutateAsync({
        displayName: form.displayName,
        email: form.email,
        password: form.password,
        acceptTerms: form.acceptTerms,
        termsVersion: terms.terms.version,
        privacyVersion: terms.privacy.version,
      })
      onRegistered(result, form.displayName)
    } catch (error) {
      applyServerError(toApiError(error), form.password)
    }
  })

  function applyServerError(error: ApiError, password: string) {
    if (error.code === 'PASSWORD_BREACHED') {
      setBreachedPassword(password)
      setError('password', { type: 'server', message: 'errors.PASSWORD_BREACHED' })
      setFocusTarget('password')
      return
    }
    if (error.code === 'TERMS_VERSION_OUTDATED') {
      setValue('acceptTerms', false)
      void legal.refetch()
      setError('acceptTerms', { type: 'server', message: 'errors.TERMS_VERSION_OUTDATED' })
      setFocusTarget('acceptTerms')
      return
    }
    const fieldErrors = error.fieldErrors.filter((item) => SERVER_FIELDS.includes(item.field as FieldName))
    if (fieldErrors.length > 0) {
      fieldErrors.forEach((item) => {
        const key = `errors.${item.code}`
        setError(item.field as FieldName, { type: 'server', message: i18n.exists(key) ? key : item.message })
      })
      setFocusTarget(fieldErrors[0].field as FieldName)
      return
    }
    setFormError(error)
  }

  const errorCount = Object.keys(errors).length
  const passwordToggle = (
    <button
      type="button"
      onClick={() => setShowPassword((shown) => !shown)}
      aria-pressed={showPassword}
      aria-controls={`${passwordId} ${confirmId}`}
      aria-label={showPassword ? t('register.password.hide') : t('register.password.show')}
      className="grid size-9 place-items-center rounded-lg text-muted transition-colors hover:bg-ink/5 hover:text-ink"
    >
      {showPassword ? <EyeOff className="size-5" aria-hidden="true" /> : <Eye className="size-5" aria-hidden="true" />}
    </button>
  )

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-6" aria-describedby="register-subtitle">
      <header className="flex flex-col gap-3">
        <h1 className="text-[28px] leading-tight font-normal tracking-tight text-ink sm:text-[32px]" tabIndex={-1}>
          {t('register.titleLine1')}
          <br />
          <Trans i18nKey="register.titleLine2" components={{ strong: <strong className="font-semibold" /> }} />
        </h1>
        <p id="register-subtitle" className="text-[15px] leading-relaxed text-muted">
          {t('register.subtitle')}
        </p>
      </header>

      {submitCount > 0 && errorCount > 0 && (
        <Alert tone="danger" data-testid="error-summary">
          {t('register.errorSummary', { count: errorCount })}
        </Alert>
      )}
      {formError && (
        <Alert tone="danger" data-testid="form-error">
          <p>{t(`errors.${formError.code}`, { defaultValue: t('errors.INTERNAL_ERROR') })}</p>
          {formError.requestId && (
            <p className="mt-1 text-xs text-muted">{t('errors.requestId', { id: formError.requestId })}</p>
          )}
        </Alert>
      )}

      <fieldset disabled={pending} className="flex flex-col gap-5">
        <TextField
          label={t('register.displayName.label')}
          placeholder={t('register.displayName.placeholder')}
          autoComplete="nickname"
          maxLength={60}
          error={message(errors.displayName?.message)}
          valid={isValid('displayName')}
          validLabel={t('register.valid')}
          hint={t('register.displayName.hint')}
          {...register('displayName')}
        />
        <TextField
          label={t('register.email.label')}
          placeholder={t('register.email.placeholder')}
          type="email"
          inputMode="email"
          autoComplete="email"
          autoCapitalize="none"
          spellCheck={false}
          error={message(errors.email?.message)}
          valid={isValid('email')}
          validLabel={t('register.valid')}
          {...register('email')}
        />
        <div className="flex flex-col gap-3">
          <TextField
            id={passwordId}
            label={t('register.password.label')}
            placeholder={t('register.password.placeholder')}
            type={showPassword ? 'text' : 'password'}
            autoComplete="new-password"
            autoCapitalize="none"
            spellCheck={false}
            error={message(errors.password?.message)}
            valid={isValid('password') && !breached}
            validLabel={t('register.valid')}
            trailing={passwordToggle}
            {...register('password')}
          />
          {values.password && <PasswordStrengthMeter password={values.password} breached={breached} />}
        </div>
        <TextField
          id={confirmId}
          label={t('register.confirmPassword.label')}
          placeholder={t('register.confirmPassword.placeholder')}
          type={showPassword ? 'text' : 'password'}
          autoComplete="new-password"
          autoCapitalize="none"
          spellCheck={false}
          error={message(errors.confirmPassword?.message)}
          valid={isValid('confirmPassword')}
          validLabel={t('register.valid')}
          {...register('confirmPassword')}
        />
        <div className="flex flex-col gap-1.5">
          <Checkbox
            label={
              <Trans
                i18nKey="register.terms"
                components={{
                  terms: <ExternalLink href={routes.terms} />,
                  privacy: <ExternalLink href={routes.privacy} />,
                }}
              />
            }
            error={message(errors.acceptTerms?.message)}
            {...register('acceptTerms')}
          />
          {legal.data && (
            <p className="pl-9 text-xs text-subtle" data-testid="terms-version">
              {t('register.termsVersion', { version: legal.data.terms.version })}
            </p>
          )}
          {legal.isError && (
            <p className="flex items-center gap-2 pl-9 text-xs text-danger">
              {t('register.termsLoadFailed')}
              <Button variant="ghost" className="h-7 px-2 text-xs" onClick={() => void legal.refetch()}>
                {t('verify.failed.retry')}
              </Button>
            </p>
          )}
        </div>
      </fieldset>

      <CtaButton type="submit" loading={pending} hint={pending ? undefined : t('register.submitHint')}>
        {pending ? t('register.submitting') : t('register.submit')}
      </CtaButton>

      <p className="text-center text-sm text-muted">
        {t('register.haveAccount')}{' '}
        <Link to={routes.login} className="font-medium text-accent-ink underline-offset-4 hover:underline">
          {t('register.login')}
        </Link>
      </p>
    </form>
  )
}

function ExternalLink({ href, children }: { href: string; children?: ReactNode }) {
  const { t } = useTranslation()
  return (
    <a
      href={href}
      target="_blank"
      rel="noopener noreferrer"
      className="font-medium text-ink underline decoration-accent decoration-2 underline-offset-4 hover:text-accent-ink"
    >
      {children}
      <span className="sr-only"> {t('register.opensInNewTab')}</span>
    </a>
  )
}
