import { zodResolver } from '@hookform/resolvers/zod'
import { KeyRound } from 'lucide-react'
import { useCallback, useEffect, useId, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { routes } from '@/app/routes'
import { detailNumber, toApiError, type ApiError } from '@/shared/api/errors'
import { deadlineIn } from '@/shared/hooks/useCountdown'
import { Alert, Button, TextField } from '@/shared/ui'
import { useChangePassword } from '../api/queries'
import { changePasswordSchema, type ChangePasswordValues } from '../lib/passwordSchema'
import { LockoutNotice } from './LockoutNotice'
import { PasswordStrengthMeter } from './PasswordStrengthMeter'
import { PasswordVisibilityToggle } from './PasswordVisibilityToggle'

type FieldName = keyof ChangePasswordValues
const WARN_WHEN_ATTEMPTS_LEFT = 2
const EMPTY: ChangePasswordValues = { currentPassword: '', newPassword: '', confirmPassword: '' }

/**
 * Đổi mật khẩu khi đang đăng nhập: phải nhập đúng mật khẩu hiện tại (sai cũng tính vào số lần nhập sai
 * như khi đăng nhập), mật khẩu mới theo quy tắc lúc đăng ký và khác mật khẩu cũ.
 */
export function ChangePasswordForm({ email }: { email: string }) {
  const { t, i18n } = useTranslation()
  const change = useChangePassword()
  const [showPassword, setShowPassword] = useState(false)
  const [breachedPassword, setBreachedPassword] = useState<string | null>(null)
  const [formError, setFormError] = useState<ApiError | null>(null)
  const [attemptsLeft, setAttemptsLeft] = useState<number | null>(null)
  const [lockedUntil, setLockedUntil] = useState<number | null>(null)
  const [unlocked, setUnlocked] = useState(false)
  const [revoked, setRevoked] = useState<number | null>(null)
  const [focusTarget, setFocusTarget] = useState<FieldName | null>(null)
  const currentId = useId()
  const newId = useId()
  const confirmId = useId()
  const {
    register,
    handleSubmit,
    watch,
    reset,
    setError,
    setValue,
    setFocus,
    getValues,
    trigger,
    formState: { errors, touchedFields, isSubmitted },
  } = useForm<ChangePasswordValues>({
    resolver: zodResolver(changePasswordSchema),
    mode: 'onTouched',
    reValidateMode: 'onChange',
    defaultValues: EMPTY,
  })
  const values = watch()
  const breached = breachedPassword !== null && breachedPassword === values.newPassword
  const pending = change.isPending
  const locked = lockedUntil !== null

  useEffect(() => {
    if (focusTarget && !pending && !locked) {
      setFocus(focusTarget)
      setFocusTarget(null)
    }
  }, [focusTarget, pending, locked, setFocus])

  const handleUnlocked = useCallback(() => {
    setLockedUntil(null)
    setUnlocked(true)
    setFocusTarget('currentPassword')
  }, [])

  const onSubmit = handleSubmit(async (form) => {
    setFormError(null)
    setRevoked(null)
    setUnlocked(false)
    setAttemptsLeft(null)
    try {
      const result = await change.mutateAsync({ currentPassword: form.currentPassword, newPassword: form.newPassword })
      reset(EMPTY)
      setShowPassword(false)
      setBreachedPassword(null)
      setRevoked(result.revokedSessions)
    } catch (caught) {
      applyServerError(toApiError(caught), form.newPassword)
    }
  })

  function applyServerError(error: ApiError, newPassword: string) {
    if (error.code === 'ACCOUNT_LOCKED') {
      setValue('currentPassword', '')
      setLockedUntil(deadlineIn(detailNumber(error, 'retryAfterSeconds') ?? 900))
      return
    }
    if (error.code === 'CURRENT_PASSWORD_INVALID') {
      setValue('currentPassword', '')
      setAttemptsLeft(detailNumber(error, 'remainingAttempts') ?? null)
      setError('currentPassword', { type: 'server', message: 'errors.CURRENT_PASSWORD_INVALID' })
      setFocusTarget('currentPassword')
      return
    }
    if (error.code === 'PASSWORD_BREACHED') setBreachedPassword(newPassword)
    const fieldError = error.fieldErrors.find((item) => item.field === 'newPassword' || item.field === 'currentPassword')
    if (fieldError) {
      const key = `errors.${error.code.startsWith('PASSWORD_') ? error.code : fieldError.code}`
      const field = fieldError.field as FieldName
      setError(field, { type: 'server', message: i18n.exists(key) ? key : fieldError.message })
      setFocusTarget(field)
      return
    }
    setFormError(error)
  }

  // Một ô đổi giá trị có thể làm ô phía sau hết (hoặc bắt đầu) sai: kiểm lại ô đó nếu đã được nhập.
  const revalidate = (field: FieldName) => () => getValues(field) && void trigger(field)
  const message = (text?: string) => (text && i18n.exists(text) ? t(text) : text)
  const isValid = (name: FieldName) => Boolean((touchedFields[name] || isSubmitted) && !errors[name] && values[name])

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-5" aria-labelledby="change-password-title">
      {/* Giúp trình quản lý mật khẩu biết đang đổi mật khẩu cho tài khoản nào. */}
      <input type="email" name="username" autoComplete="username" value={email} readOnly hidden />

      {revoked !== null && (
        <Alert tone="success" data-testid="change-password-success">
          {t('security.change.success', { count: revoked })}
        </Alert>
      )}
      {locked && (
        <LockoutNotice
          lockedUntil={lockedUntil}
          onUnlocked={handleUnlocked}
          title={t('security.change.lockedTitle')}
          body={t('security.change.lockedBody')}
        />
      )}
      {unlocked && <Alert tone="success">{t('security.change.unlocked')}</Alert>}
      {formError && (
        <Alert tone="danger" data-testid="change-password-error">
          <p>{t(`errors.${formError.code}`, { defaultValue: t('errors.INTERNAL_ERROR') })}</p>
          {formError.requestId && (
            <p className="mt-1 text-xs text-muted">{t('errors.requestId', { id: formError.requestId })}</p>
          )}
        </Alert>
      )}

      <fieldset disabled={pending || locked} className="flex flex-col gap-5">
        <TextField
          id={currentId}
          label={t('security.change.currentLabel')}
          placeholder={t('security.change.currentPlaceholder')}
          type={showPassword ? 'text' : 'password'}
          autoComplete="current-password"
          autoCapitalize="none"
          spellCheck={false}
          error={message(errors.currentPassword?.message)}
          hint={
            attemptsLeft !== null && attemptsLeft <= WARN_WHEN_ATTEMPTS_LEFT ? (
              <span className="font-medium text-danger" data-testid="change-attempts-left">
                {t('security.change.attemptsLeft', { count: attemptsLeft })}
              </span>
            ) : undefined
          }
          trailing={
            <PasswordVisibilityToggle
              shown={showPassword}
              onToggle={() => setShowPassword((shown) => !shown)}
              controls={`${currentId} ${newId} ${confirmId}`}
            />
          }
          {...register('currentPassword', { onChange: revalidate('newPassword') })}
        />
        <div className="flex flex-col gap-3">
          <TextField
            id={newId}
            label={t('security.change.newLabel')}
            placeholder={t('security.change.newPlaceholder')}
            type={showPassword ? 'text' : 'password'}
            autoComplete="new-password"
            autoCapitalize="none"
            spellCheck={false}
            error={message(errors.newPassword?.message)}
            valid={isValid('newPassword') && !breached}
            validLabel={t('register.valid')}
            {...register('newPassword', { onChange: revalidate('confirmPassword') })}
          />
          {values.newPassword && <PasswordStrengthMeter password={values.newPassword} breached={breached} />}
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
          valid={isValid('confirmPassword')}
          validLabel={t('register.valid')}
          {...register('confirmPassword')}
        />
      </fieldset>

      <div className="flex flex-col-reverse items-stretch gap-3 sm:flex-row sm:items-center sm:justify-between">
        <Link
          to={routes.forgotPassword}
          state={{ email }}
          className="text-center text-sm font-medium text-accent-ink underline-offset-4 hover:underline sm:text-left"
        >
          {t('security.change.forgot')}
        </Link>
        <Button
          type="submit"
          variant="dark"
          size="lg"
          loading={pending}
          disabled={locked}
          icon={<KeyRound className="size-4" aria-hidden="true" />}
        >
          {pending ? t('security.change.submitting') : t('security.change.submit')}
        </Button>
      </div>
    </form>
  )
}
