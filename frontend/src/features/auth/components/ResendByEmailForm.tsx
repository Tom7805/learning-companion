import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { z } from 'zod'
import { toApiError, type ApiError } from '@/shared/api/errors'
import { deadlineIn } from '@/shared/hooks/useCountdown'
import { Alert, Button, TextField } from '@/shared/ui'
import { useResendVerification } from '../api/queries'
import type { PendingVerification } from '../types'

const schema = z.object({
  email: z.string().trim().min(1, 'validation.emailRequired').pipe(z.email('validation.emailInvalid')),
})

type Values = z.infer<typeof schema>

/** Gửi lại thư xác thực khi không còn biết địa chỉ (mở trang trực tiếp hoặc đường dẫn hỏng). */
export function ResendByEmailForm({ onSent }: { onSent: (pending: PendingVerification) => void }) {
  const { t } = useTranslation()
  const resend = useResendVerification()
  const [error, setError] = useState<ApiError | null>(null)
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<Values>({ resolver: zodResolver(schema), mode: 'onTouched' })

  const onSubmit = handleSubmit(async ({ email }) => {
    setError(null)
    try {
      const result = await resend.mutateAsync({ email })
      onSent({ email, resendAvailableAt: deadlineIn(result.resendAvailableInSeconds) })
    } catch (caught) {
      setError(toApiError(caught))
    }
  })

  return (
    <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4" data-testid="resend-by-email">
      <div className="flex flex-col gap-1">
        <h2 className="text-lg font-medium">{t('checkEmail.manual.title')}</h2>
        <p className="text-sm text-muted">{t('checkEmail.manual.body')}</p>
      </div>
      {error && <Alert tone="danger">{t(`errors.${error.code}`, { defaultValue: t('errors.INTERNAL_ERROR') })}</Alert>}
      <TextField
        label={t('register.email.label')}
        placeholder={t('register.email.placeholder')}
        type="email"
        inputMode="email"
        autoComplete="email"
        autoCapitalize="none"
        spellCheck={false}
        error={errors.email?.message ? t(errors.email.message) : undefined}
        {...register('email')}
      />
      <Button type="submit" variant="dark" size="lg" loading={resend.isPending}>
        {t('checkEmail.manual.submit')}
      </Button>
    </form>
  )
}
