import { zodResolver } from '@hookform/resolvers/zod'
import { KeyRound } from 'lucide-react'
import { useEffect, useId, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { z } from 'zod'
import { toApiError, type ApiError } from '@/shared/api/errors'
import { Alert, Button, TextField } from '@/shared/ui'
import { useSetPassword } from '../api/queries'
import { newPasswordField } from '../lib/passwordSchema'
import { PasswordStrengthMeter } from './PasswordStrengthMeter'
import { PasswordVisibilityToggle } from './PasswordVisibilityToggle'

const fields = z.object({
  newPassword: newPasswordField,
  confirmPassword: z.string().min(1, 'validation.confirmRequired'),
})
const schema = fields.refine((values) => values.newPassword === values.confirmPassword, {
  path: ['confirmPassword'],
  message: 'validation.confirmMismatch',
  when: (payload) => fields.safeParse(payload.value).success,
})
type Values = z.infer<typeof schema>

/**
 * Đặt mật khẩu lần đầu cho tài khoản tạo qua nhà cung cấp định danh (NCL-01-CN-007): sau đó đăng nhập được bằng
 * thư điện tử và gỡ được liên kết. Mật khẩu theo đúng quy tắc khi đăng ký.
 */
export function SetPasswordForm({ email, onDone }: { email: string; onDone: () => void }) {
  const { t, i18n } = useTranslation()
  const setPassword = useSetPassword()
  const [shown, setShown] = useState(false)
  const [formError, setFormError] = useState<ApiError | null>(null)
  const [focusNew, setFocusNew] = useState(false)
  const newId = useId()
  const confirmId = useId()
  const {
    register,
    handleSubmit,
    watch,
    setError,
    setFocus,
    getValues,
    trigger,
    formState: { errors },
  } = useForm<Values>({ resolver: zodResolver(schema), mode: 'onTouched', defaultValues: { newPassword: '', confirmPassword: '' } })
  const newPassword = watch('newPassword')

  useEffect(() => {
    if (focusNew && !setPassword.isPending) {
      setFocus('newPassword')
      setFocusNew(false)
    }
  }, [focusNew, setPassword.isPending, setFocus])

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      await setPassword.mutateAsync(values.newPassword)
      onDone()
    } catch (caught) {
      const error = toApiError(caught)
      const field = error.fieldErrors.find((item) => item.field === 'newPassword')
      if (field) {
        const key = `errors.${error.code}`
        setError('newPassword', { type: 'server', message: i18n.exists(key) ? key : field.message })
        setFocusNew(true)
      } else {
        setFormError(error)
      }
    }
  })

  const message = (text?: string) => (text && i18n.exists(text) ? t(text) : text)

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-5">
      <input type="email" name="username" autoComplete="username" value={email} readOnly hidden />
      {formError && (
        <Alert tone="danger">{t(`errors.${formError.code}`, { defaultValue: t('errors.INTERNAL_ERROR') })}</Alert>
      )}
      <fieldset disabled={setPassword.isPending} className="flex flex-col gap-5">
        <div className="flex flex-col gap-3">
          <TextField
            id={newId}
            label={t('security.change.newLabel')}
            placeholder={t('security.change.newPlaceholder')}
            type={shown ? 'text' : 'password'}
            autoComplete="new-password"
            autoCapitalize="none"
            spellCheck={false}
            error={message(errors.newPassword?.message)}
            trailing={
              <PasswordVisibilityToggle
                shown={shown}
                onToggle={() => setShown((value) => !value)}
                controls={`${newId} ${confirmId}`}
              />
            }
            {...register('newPassword', {
              onChange: () => getValues('confirmPassword') && void trigger('confirmPassword'),
            })}
          />
          {newPassword && <PasswordStrengthMeter password={newPassword} />}
        </div>
        <TextField
          id={confirmId}
          label={t('security.change.confirmLabel')}
          placeholder={t('security.change.confirmPlaceholder')}
          type={shown ? 'text' : 'password'}
          autoComplete="new-password"
          autoCapitalize="none"
          spellCheck={false}
          error={message(errors.confirmPassword?.message)}
          {...register('confirmPassword')}
        />
      </fieldset>
      <Button
        type="submit"
        variant="dark"
        size="lg"
        className="self-end"
        loading={setPassword.isPending}
        icon={<KeyRound className="size-4" aria-hidden="true" />}
      >
        {t('security.set.submit')}
      </Button>
    </form>
  )
}
