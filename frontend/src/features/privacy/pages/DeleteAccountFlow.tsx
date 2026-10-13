import { Clock3, FileArchive, LogIn, ShieldAlert, Trash2, Zap } from 'lucide-react'
import { useCallback, useId, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { routes } from '@/app/routes'
import { recentStore } from '@/app/store/recentStore'
import { useAuth, useSignOutToReauthenticate } from '@/features/auth'
import { LockoutNotice } from '@/features/auth/components/LockoutNotice'
import { PasswordVisibilityToggle } from '@/features/auth/components/PasswordVisibilityToggle'
import { detailNumber, toApiError } from '@/shared/api/errors'
import { deadlineIn } from '@/shared/hooks/useCountdown'
import { useDateFormat } from '@/shared/hooks/useDateFormat'
import { usePageHeading } from '@/shared/hooks/usePageHeading'
import { reloadTo } from '@/shared/lib/navigation'
import { Alert, Button, Checkbox, TextField, buttonClasses } from '@/shared/ui'
import { useDataExportOverview, useRequestAccountDeletion } from '../api/queries'

const GRACE_DAYS = 30
const ERASED = ['account', 'library', 'learning', 'exports'] as const

/**
 * Xóa tài khoản (NCL-01-CN-009): gợi ý xuất dữ liệu trước, nêu rõ những gì bị xóa và hạn chót đổi ý, rồi xác nhận
 * bằng mật khẩu. Thành công thì mọi phiên bị thu hồi, ứng dụng tải lại ở trang "đã lên lịch xóa".
 */
export function DeleteAccountFlow() {
  const { t } = useTranslation()
  const format = useDateFormat()
  const { account } = useAuth()
  const heading = usePageHeading(t('accountDeletion.page.title'))
  // Hạn dự kiến để người học hình dung; hạn chính xác do máy chủ tính và có trong thư xác nhận.
  const [deadline] = useState(() => new Date(Date.now() + GRACE_DAYS * 86_400_000).toISOString())
  const date = format.dateTime(deadline)

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-6">
      <header className="flex items-start gap-4">
        <span
          aria-hidden="true"
          className="grid size-14 shrink-0 place-items-center rounded-2xl border-[1.5px] border-ink bg-danger-soft"
        >
          <Trash2 className="size-7 text-danger" />
        </span>
        <div className="flex min-w-0 flex-col gap-1">
          <h1 ref={heading} tabIndex={-1} className="text-3xl tracking-tight outline-none">
            {t('accountDeletion.page.title')}
          </h1>
          <p className="leading-relaxed text-muted">{t('accountDeletion.page.subtitle')}</p>
        </div>
      </header>

      <ExportFirst />

      <section
        aria-labelledby="deletion-timeline"
        className="flex flex-col gap-4 rounded-card border-[1.5px] border-ink bg-surface p-5 sm:p-6"
      >
        <h2 id="deletion-timeline" className="text-lg font-semibold">
          {t('accountDeletion.page.timelineTitle')}
        </h2>
        <ol className="flex flex-col gap-4" data-testid="deletion-timeline">
          <Step icon={<Zap className="size-4" />} tone="bg-sun" title={t('accountDeletion.page.now')}>
            {t('accountDeletion.page.nowBody', { email: account?.email ?? '' })}
          </Step>
          <Step icon={<Clock3 className="size-4" />} tone="bg-sky" title={t('accountDeletion.page.grace', { date })}>
            {t('accountDeletion.page.graceBody')}
          </Step>
          <Step icon={<Trash2 className="size-4" />} tone="bg-danger-soft" title={t('accountDeletion.page.after', { date })}>
            <span>{t('accountDeletion.page.afterBody')}</span>
            <ul className="mt-1.5 list-disc pl-5">
              {ERASED.map((item) => (
                <li key={item}>{t(`accountDeletion.page.erased.${item}`)}</li>
              ))}
            </ul>
            <span className="mt-1.5 block text-[0.8125rem] text-muted">{t('accountDeletion.page.auditNote')}</span>
          </Step>
        </ol>
      </section>

      <ConfirmForm date={date} />
    </div>
  )
}

function Step({ icon, tone, title, children }: { icon: ReactNode; tone: string; title: string; children: ReactNode }) {
  return (
    <li className="flex gap-3">
      <span
        aria-hidden="true"
        className={`mt-0.5 grid size-8 shrink-0 place-items-center rounded-full border-[1.5px] border-ink ${tone}`}
      >
        {icon}
      </span>
      <div className="flex flex-col gap-0.5 text-sm leading-relaxed text-ink-soft">
        <p className="font-medium text-ink">{title}</p>
        <div>{children}</div>
      </div>
    </li>
  )
}

/** Gợi ý xuất dữ liệu trước khi xóa; mở mục Xuất dữ liệu trong cài đặt ngay trên trang này. */
function ExportFirst() {
  const { t } = useTranslation()
  const format = useDateFormat()
  const overview = useDataExportOverview()
  const latest = overview.data?.latest
  return (
    <section
      aria-labelledby="deletion-export"
      className="flex flex-col gap-3 rounded-card border-[1.5px] border-ink bg-sky-soft p-5 sm:flex-row sm:items-center sm:p-6"
    >
      <span
        aria-hidden="true"
        className="grid size-12 shrink-0 place-items-center rounded-2xl border-[1.5px] border-ink bg-sun"
      >
        <FileArchive className="size-6" />
      </span>
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <h2 id="deletion-export" className="font-semibold">
          {t('accountDeletion.page.exportTitle')}
        </h2>
        <p className="text-sm leading-relaxed text-ink-soft" data-testid="deletion-export-status">
          {latest?.status === 'READY' && latest.expiresAt
            ? t('accountDeletion.page.exportReady', { until: format.dateTime(latest.expiresAt) })
            : t('accountDeletion.page.exportBody')}
        </p>
      </div>
      <Link to={{ search: '?settings=accounts' }} className={buttonClasses('outline', 'md', 'shrink-0')}>
        {t('accountDeletion.page.exportAction')}
      </Link>
    </section>
  )
}

function ConfirmForm({ date }: { date: string }) {
  const { t } = useTranslation()
  const { account } = useAuth()
  const accountId = account?.id ?? ''
  const hasPassword = account?.hasPassword !== false
  const request = useRequestAccountDeletion(accountId)
  const relogin = useSignOutToReauthenticate()
  const passwordId = useId()
  const passwordInput = useRef<HTMLInputElement>(null)
  const [understood, setUnderstood] = useState(false)
  const [password, setPassword] = useState('')
  const [shown, setShown] = useState(false)
  const [understandError, setUnderstandError] = useState<string | undefined>()
  const [passwordError, setPasswordError] = useState<string | undefined>()
  const [formError, setFormError] = useState<string | null>(null)
  const [needsRelogin, setNeedsRelogin] = useState(false)
  const [lockedUntil, setLockedUntil] = useState<number | null>(null)

  const focusPassword = () => requestAnimationFrame(() => passwordInput.current?.focus())
  const handleUnlocked = useCallback(() => {
    setLockedUntil(null)
    focusPassword()
  }, [])

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setFormError(null)
    const missingUnderstanding = !understood
    const missingPassword = hasPassword && !password
    setUnderstandError(missingUnderstanding ? t('accountDeletion.page.understandRequired') : undefined)
    setPasswordError(missingPassword ? t('validation.passwordRequired') : undefined)
    if (missingUnderstanding || missingPassword) {
      if (missingUnderstanding) document.getElementById(`${passwordId}-understand`)?.focus()
      else focusPassword()
      return
    }
    try {
      const status = await request.mutateAsync(hasPassword ? password : undefined)
      recentStore.clear(accountId)
      const until = status.scheduledFor ? `?until=${encodeURIComponent(status.scheduledFor)}` : ''
      reloadTo(`${routes.deletionScheduled}${until}`)
    } catch (caught) {
      const error = toApiError(caught)
      setPassword('')
      if (error.code === 'CURRENT_PASSWORD_INVALID') {
        const remaining = detailNumber(error, 'remainingAttempts')
        setPasswordError(
          [t('accountDeletion.page.wrongPassword'), remaining !== undefined && remaining <= 2
            ? t('accountDeletion.page.remaining', { count: remaining })
            : null].filter(Boolean).join(' '),
        )
        focusPassword()
      } else if (error.code === 'ACCOUNT_LOCKED') {
        setLockedUntil(deadlineIn(detailNumber(error, 'retryAfterSeconds') ?? 900))
      } else if (error.code === 'REAUTHENTICATION_REQUIRED' && !hasPassword) {
        setNeedsRelogin(true)
      } else {
        setFormError(t(`errors.${error.code}`, { defaultValue: t('errors.INTERNAL_ERROR') }))
      }
    }
  }

  return (
    <form
      onSubmit={(event) => void submit(event)}
      noValidate
      aria-labelledby={`${passwordId}-title`}
      className="flex flex-col gap-4 rounded-card border-[1.5px] border-ink bg-surface p-5 sm:p-6"
    >
      <div className="flex items-start gap-3">
        <span
          aria-hidden="true"
          className="grid size-10 shrink-0 place-items-center rounded-xl border-[1.5px] border-ink bg-danger-soft"
        >
          <ShieldAlert className="size-5 text-danger" />
        </span>
        <div className="flex flex-col gap-1">
          <h2 id={`${passwordId}-title`} className="text-lg font-semibold">
            {t('accountDeletion.page.confirmTitle')}
          </h2>
          <p className="text-sm leading-relaxed text-ink-soft">
            {hasPassword ? t('accountDeletion.page.passwordHint') : t('accountDeletion.page.noPassword')}
          </p>
        </div>
      </div>

      {lockedUntil !== null && (
        <LockoutNotice
          lockedUntil={lockedUntil}
          onUnlocked={handleUnlocked}
          title={t('accountDeletion.page.lockedTitle')}
          body={t('accountDeletion.page.lockedBody')}
        />
      )}
      {formError && (
        <Alert tone="danger" data-testid="deletion-error">
          {formError}
        </Alert>
      )}

      <Checkbox
        id={`${passwordId}-understand`}
        checked={understood}
        onChange={(event) => {
          setUnderstood(event.target.checked)
          if (event.target.checked) setUnderstandError(undefined)
        }}
        label={t('accountDeletion.page.understand', { date })}
        error={understandError}
      />

      {/* Giúp trình quản lý mật khẩu điền đúng tài khoản. */}
      <input type="email" name="username" autoComplete="username" value={account?.email ?? ''} readOnly hidden />
      {hasPassword && (
        <TextField
          ref={passwordInput}
          id={passwordId}
          type={shown ? 'text' : 'password'}
          label={t('accountDeletion.page.password')}
          autoComplete="current-password"
          value={password}
          disabled={lockedUntil !== null}
          onChange={(event) => {
            setPassword(event.target.value)
            setPasswordError(undefined)
          }}
          error={passwordError}
          trailing={<PasswordVisibilityToggle shown={shown} onToggle={() => setShown(!shown)} controls={passwordId} />}
        />
      )}

      <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <Link to={routes.home} className={buttonClasses('outline', 'lg')}>
          {t('accountDeletion.page.keep')}
        </Link>
        {needsRelogin ? (
          <Button
            variant="dark"
            size="lg"
            loading={relogin.isPending}
            onClick={() => relogin.mutate()}
            icon={<LogIn className="size-5" aria-hidden="true" />}
          >
            {t('accountDeletion.page.relogin')}
          </Button>
        ) : (
          <Button
            type="submit"
            variant="danger"
            size="lg"
            loading={request.isPending}
            disabled={lockedUntil !== null}
            icon={<Trash2 className="size-5" aria-hidden="true" />}
          >
            {t('accountDeletion.page.submit')}
          </Button>
        )}
      </div>
    </form>
  )
}
