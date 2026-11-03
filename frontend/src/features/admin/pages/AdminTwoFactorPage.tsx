import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, KeyRound, LifeBuoy, ShieldCheck, TimerOff } from 'lucide-react'
import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation, useNavigate } from 'react-router'
import { routes } from '@/app/routes'
import { LockoutNotice, type SessionStatus } from '@/features/auth'
import { detailNumber, toApiError, type ApiError } from '@/shared/api/errors'
import { queryKeys } from '@/shared/api/queryKeys'
import { deadlineIn } from '@/shared/hooks/useCountdown'
import { usePageHeading } from '@/shared/hooks/usePageHeading'
import { Alert, Badge, buttonClasses, CtaButton, Skeleton, TextField } from '@/shared/ui'
import { adminApi } from '../api/adminApi'
import { RecoveryCodesSheet } from '../components/RecoveryCodesSheet'
import { TotpEnrollment } from '../components/TotpEnrollment'
import type { TwoFactorResult, TwoFactorStage } from '../types'

const WARN_WHEN_ATTEMPTS_LEFT = 2
const CHALLENGE_KEY = ['admin', 'two-factor'] as const

interface LocationState {
  from?: string
}

/**
 * Bước thứ hai khi quản trị viên đăng nhập (NCL-03-CN-002): mật khẩu đã đúng, còn phải nhập mã sáu chữ số của ứng dụng
 * sinh mã. Lần đầu (hoặc sau khi vào bằng mã khôi phục) là thiết lập ứng dụng sinh mã rồi lưu mười mã khôi phục. Sai
 * mã năm lần liên tiếp thì đăng nhập quản trị bị khóa mười lăm phút.
 */
export function AdminTwoFactorPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const from = (location.state as LocationState | null)?.from
  const challenge = useQuery({ queryKey: CHALLENGE_KEY, queryFn: adminApi.twoFactorChallenge, retry: false })
  // Bước hiện tại có thể đổi ngay trên trang: dùng mã khôi phục thì chuyển sang thiết lập lại.
  const [stage, setStage] = useState<TwoFactorStage | null>(null)
  const [recovered, setRecovered] = useState(false)
  const [codes, setCodes] = useState<string[] | null>(null)
  const current = stage ?? challenge.data?.stage

  const enter = useCallback(
    (result: TwoFactorResult) => {
      if (result.account) {
        queryClient.setQueryData<SessionStatus>(queryKeys.session, { authenticated: true, account: result.account })
        queryClient.removeQueries({ queryKey: queryKeys.adminMe })
      }
      navigate(from ?? routes.adminAudit, { replace: true })
    },
    [from, navigate, queryClient],
  )

  if (challenge.isPending) {
    return (
      <div className="flex flex-col gap-4" aria-busy="true">
        <p role="status" className="sr-only">
          {t('common.loading')}
        </p>
        <Skeleton className="h-10 w-3/4" />
        <Skeleton className="h-14" />
        <Skeleton className="h-14" />
      </div>
    )
  }
  if (challenge.isError || !challenge.data) {
    return <ChallengeExpired />
  }
  if (codes) {
    return <RecoveryCodesSheet codes={codes} email={challenge.data.email} onDone={() => enter({ signedIn: true, recoveryCodes: [] })} />
  }
  if (current === 'ENROLL') {
    return (
      <EnrollStep
        email={challenge.data.email}
        recovered={recovered}
        onEnrolled={(result) => {
          if (result.account) {
            queryClient.setQueryData<SessionStatus>(queryKeys.session, {
              authenticated: true,
              account: result.account,
            })
            queryClient.removeQueries({ queryKey: queryKeys.adminMe })
          }
          setCodes(result.recoveryCodes)
        }}
      />
    )
  }
  return (
    <VerifyStep
      email={challenge.data.email}
      onSignedIn={enter}
      onEnrollmentRequired={(viaRecovery) => {
        setRecovered(viaRecovery)
        setStage('ENROLL')
      }}
    />
  )
}

function Header({ title, body }: { title: string; body: string }) {
  const { t } = useTranslation()
  const heading = usePageHeading(title)
  return (
    <header className="flex flex-col gap-3">
      <Badge tone="sun" className="self-start">
        <ShieldCheck className="size-3.5" aria-hidden="true" />
        {t('adminLogin.badge')}
      </Badge>
      <h1 ref={heading} tabIndex={-1} className="text-[1.75rem] leading-tight font-normal tracking-tight outline-none sm:text-[2rem]">
        {title}
      </h1>
      <p className="text-[0.9375rem] leading-relaxed text-muted">{body}</p>
    </header>
  )
}

/** Lỗi chung của các bước nhập mã: sai mã (kèm số lần còn lại), khóa, hoặc bước đã hết hạn. */
function useCodeErrors() {
  const [error, setError] = useState<ApiError | null>(null)
  const [lockedUntil, setLockedUntil] = useState<number | null>(null)
  const [expired, setExpired] = useState(false)
  const handle = (caught: unknown) => {
    const apiError = toApiError(caught)
    if (apiError.code === 'ADMIN_LOGIN_LOCKED') {
      setError(null)
      setLockedUntil(deadlineIn(detailNumber(apiError, 'retryAfterSeconds') ?? 900))
      return
    }
    if (apiError.code === 'TWO_FACTOR_CHALLENGE_INVALID') {
      setExpired(true)
      return
    }
    setError(apiError)
  }
  return { error, setError, lockedUntil, setLockedUntil, expired, handle }
}

function VerifyStep({
  email,
  onSignedIn,
  onEnrollmentRequired,
}: {
  email: string
  onSignedIn: (result: TwoFactorResult) => void
  onEnrollmentRequired: (viaRecovery: boolean) => void
}) {
  const { t } = useTranslation()
  const [mode, setMode] = useState<'code' | 'recovery'>('code')
  const [value, setValue] = useState('')
  const [fieldError, setFieldError] = useState<string>()
  const inputRef = useRef<HTMLInputElement>(null)
  const errors = useCodeErrors()
  const locked = errors.lockedUntil !== null
  const submit = useMutation({
    mutationFn: (input: string) => (mode === 'code' ? adminApi.verifyTotp(input) : adminApi.useRecoveryCode(input)),
  })

  useEffect(() => {
    inputRef.current?.focus()
  }, [mode])

  if (errors.expired) return <ChallengeExpired />

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault()
    const input = value.trim()
    if (mode === 'code' && !/^\d{3}\s?\d{3}$/.test(input)) {
      setFieldError(t('adminLogin.verify.codeFormat'))
      inputRef.current?.focus()
      return
    }
    if (mode === 'recovery' && input.replace(/[^a-z0-9]/gi, '').length !== 10) {
      setFieldError(t('adminLogin.recovery.format'))
      inputRef.current?.focus()
      return
    }
    setFieldError(undefined)
    errors.setError(null)
    try {
      const result = await submit.mutateAsync(input)
      if (result.signedIn) onSignedIn(result)
      else onEnrollmentRequired(mode === 'recovery')
    } catch (caught) {
      setValue('')
      errors.handle(caught)
      inputRef.current?.focus()
    }
  }

  const attemptsLeft = detailNumber(errors.error, 'remainingAttempts')
  const switchMode = () => {
    setMode((current) => (current === 'code' ? 'recovery' : 'code'))
    setValue('')
    setFieldError(undefined)
    errors.setError(null)
  }

  return (
    <form onSubmit={(event) => void onSubmit(event)} noValidate className="flex flex-col gap-6">
      {mode === 'code' ? (
        <Header title={t('adminLogin.verify.title')} body={t('adminLogin.verify.body', { email })} />
      ) : (
        <Header title={t('adminLogin.recovery.title')} body={t('adminLogin.recovery.body')} />
      )}

      {locked && (
        <LockoutNotice
          lockedUntil={errors.lockedUntil!}
          onUnlocked={() => errors.setLockedUntil(null)}
          title={t('adminLogin.locked.title')}
          body={t('adminLogin.locked.body')}
        />
      )}
      {errors.error && (
        <Alert tone="danger" data-testid="two-factor-error">
          <p>
            {errors.error.code === 'TWO_FACTOR_CODE_INVALID'
              ? t('adminLogin.verify.invalid')
              : errors.error.code === 'RECOVERY_CODE_INVALID'
                ? t('adminLogin.recovery.invalid')
                : t(`errors.${errors.error.code}`, { defaultValue: t('errors.INTERNAL_ERROR') })}
          </p>
          {attemptsLeft !== undefined && attemptsLeft <= WARN_WHEN_ATTEMPTS_LEFT && (
            <p className="mt-1 font-medium" data-testid="attempts-left">
              {t('adminLogin.attemptsLeft', { count: attemptsLeft })}
            </p>
          )}
        </Alert>
      )}

      <fieldset disabled={submit.isPending || locked} className="flex flex-col gap-5">
        {mode === 'code' ? (
          <TextField
            ref={inputRef}
            key="code"
            label={t('adminLogin.verify.codeLabel')}
            placeholder="123 456"
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength={7}
            value={value}
            onChange={(event) => setValue(event.currentTarget.value)}
            error={fieldError}
            className="[&_input]:font-mono [&_input]:text-lg [&_input]:tracking-[0.3em]"
          />
        ) : (
          <TextField
            ref={inputRef}
            key="recovery"
            label={t('adminLogin.recovery.label')}
            placeholder="abcde-fghjk"
            autoComplete="off"
            autoCapitalize="none"
            spellCheck={false}
            maxLength={32}
            value={value}
            onChange={(event) => setValue(event.currentTarget.value)}
            error={fieldError}
            hint={t('adminLogin.recovery.hint')}
            className="[&_input]:font-mono [&_input]:tracking-wider"
          />
        )}
      </fieldset>

      <CtaButton type="submit" loading={submit.isPending} disabled={locked}>
        {submit.isPending ? t('adminLogin.verify.submitting') : t('adminLogin.verify.submit')}
      </CtaButton>

      <div className="flex flex-wrap items-center justify-between gap-3 text-sm">
        <Link to={routes.login} className="inline-flex items-center gap-1.5 font-medium text-ink-soft underline-offset-4 hover:underline">
          <ArrowLeft className="size-4" aria-hidden="true" />
          {t('adminLogin.backToLogin')}
        </Link>
        <button
          type="button"
          onClick={switchMode}
          className="inline-flex items-center gap-1.5 font-medium text-accent-ink underline-offset-4 hover:underline"
        >
          {mode === 'code' ? (
            <>
              <LifeBuoy className="size-4" aria-hidden="true" />
              {t('adminLogin.recovery.use')}
            </>
          ) : (
            <>
              <KeyRound className="size-4" aria-hidden="true" />
              {t('adminLogin.recovery.back')}
            </>
          )}
        </button>
      </div>
    </form>
  )
}

function EnrollStep({
  email,
  recovered,
  onEnrolled,
}: {
  email: string
  recovered: boolean
  onEnrolled: (result: TwoFactorResult) => void
}) {
  const { t } = useTranslation()
  const [value, setValue] = useState('')
  const [fieldError, setFieldError] = useState<string>()
  const inputRef = useRef<HTMLInputElement>(null)
  const errors = useCodeErrors()
  const locked = errors.lockedUntil !== null
  // Gọi lại vẫn nhận đúng khóa đang chờ, nên tải lại trang không làm hỏng mã đã quét.
  const setup = useQuery({ queryKey: ['admin', 'two-factor', 'setup'], queryFn: adminApi.startTotpEnrollment, retry: false, staleTime: Infinity })
  const confirm = useMutation({ mutationFn: adminApi.confirmTotpEnrollment })

  if (errors.expired || (setup.isError && toApiError(setup.error).code === 'TWO_FACTOR_CHALLENGE_INVALID')) {
    return <ChallengeExpired />
  }

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault()
    if (!/^\d{3}\s?\d{3}$/.test(value.trim())) {
      setFieldError(t('adminLogin.verify.codeFormat'))
      inputRef.current?.focus()
      return
    }
    setFieldError(undefined)
    errors.setError(null)
    try {
      onEnrolled(await confirm.mutateAsync(value.trim()))
    } catch (caught) {
      setValue('')
      errors.handle(caught)
      inputRef.current?.focus()
    }
  }

  return (
    <form onSubmit={(event) => void onSubmit(event)} noValidate className="flex flex-col gap-6">
      <Header
        title={recovered ? t('adminLogin.enroll.resetTitle') : t('adminLogin.enroll.title')}
        body={recovered ? t('adminLogin.enroll.resetBody') : t('adminLogin.enroll.body')}
      />
      {recovered && (
        <Alert tone="success" data-testid="recovery-used">
          {t('adminLogin.recovery.used')}
        </Alert>
      )}
      {locked && (
        <LockoutNotice
          lockedUntil={errors.lockedUntil!}
          onUnlocked={() => errors.setLockedUntil(null)}
          title={t('adminLogin.locked.title')}
          body={t('adminLogin.locked.body')}
        />
      )}
      {errors.error && (
        <Alert tone="danger" data-testid="two-factor-error">
          {errors.error.code === 'TWO_FACTOR_CODE_INVALID'
            ? t('adminLogin.enroll.invalid')
            : t(`errors.${errors.error.code}`, { defaultValue: t('errors.INTERNAL_ERROR') })}
        </Alert>
      )}
      {setup.isError && toApiError(setup.error).code !== 'TWO_FACTOR_CHALLENGE_INVALID' && (
        <Alert tone="danger">{t('errors.INTERNAL_ERROR')}</Alert>
      )}

      <TotpEnrollment setup={setup.data} email={email} />

      <fieldset disabled={confirm.isPending || locked || !setup.data} className="flex flex-col gap-5">
        <TextField
          ref={inputRef}
          label={t('adminLogin.enroll.codeLabel')}
          placeholder="123 456"
          inputMode="numeric"
          autoComplete="one-time-code"
          maxLength={7}
          value={value}
          onChange={(event) => setValue(event.currentTarget.value)}
          error={fieldError}
          className="[&_input]:font-mono [&_input]:text-lg [&_input]:tracking-[0.3em]"
        />
      </fieldset>
      <CtaButton type="submit" loading={confirm.isPending} disabled={locked || !setup.data}>
        {confirm.isPending ? t('adminLogin.enroll.submitting') : t('adminLogin.enroll.submit')}
      </CtaButton>
      <Link to={routes.login} className="inline-flex items-center gap-1.5 self-start text-sm font-medium text-ink-soft underline-offset-4 hover:underline">
        <ArrowLeft className="size-4" aria-hidden="true" />
        {t('adminLogin.backToLogin')}
      </Link>
    </form>
  )
}

function ChallengeExpired() {
  const { t } = useTranslation()
  const heading = usePageHeading(t('adminLogin.expired.title'))
  return (
    <div className="flex flex-col items-start gap-4" data-testid="two-factor-expired">
      <span aria-hidden="true" className="grid size-14 place-items-center rounded-2xl border-[1.5px] border-ink bg-sky">
        <TimerOff className="size-6" />
      </span>
      <h1 ref={heading} tabIndex={-1} className="text-[1.75rem] leading-tight font-normal tracking-tight outline-none">
        {t('adminLogin.expired.title')}
      </h1>
      <p className="text-[0.9375rem] leading-relaxed text-muted">{t('adminLogin.expired.body')}</p>
      <Link to={routes.login} className={buttonClasses('dark', 'md', 'mt-2')}>
        {t('adminLogin.backToLogin')}
      </Link>
    </div>
  )
}
