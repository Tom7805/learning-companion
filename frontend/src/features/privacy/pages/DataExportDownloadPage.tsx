import { Download, FileArchive, FileQuestion, Hourglass, LogIn, ShieldCheck } from 'lucide-react'
import { useCallback, useId, useRef, useState, type FormEvent, type ReactNode, type Ref } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router'
import { routes } from '@/app/routes'
import { useAuth, useSignOutToReauthenticate } from '@/features/auth'
import { LockoutNotice } from '@/features/auth/components/LockoutNotice'
import { PasswordVisibilityToggle } from '@/features/auth/components/PasswordVisibilityToggle'
import { detailNumber, toApiError } from '@/shared/api/errors'
import { deadlineIn } from '@/shared/hooks/useCountdown'
import { useDateFormat } from '@/shared/hooks/useDateFormat'
import { downloadFromUrl } from '@/shared/lib/download'
import { usePageHeading } from '@/shared/hooks/usePageHeading'
import { formatBytes } from '@/shared/lib/format'
import { Alert, Button, CtaButton, Skeleton, Spinner, TextField, buttonClasses } from '@/shared/ui'
import { useDataExport, useDownloadDataExport, useRequestDataExport } from '../api/queries'
import type { DataExport, DataExportLink } from '../types'

const ACCOUNT_SETTINGS = { search: '?settings=accounts' }

/**
 * Trang tải gói dữ liệu, mở từ thư báo hoặc từ cài đặt (NCL-01-CN-008). Mỗi lần tải phải xác nhận lại danh tính;
 * gói quá bảy ngày thì báo hết hạn và cho yêu cầu xuất lại; gói của người khác hiện như không tồn tại (TC-04).
 */
export function DataExportDownloadPage() {
  const { t } = useTranslation()
  const { id = '' } = useParams()
  const query = useDataExport(id)
  const notFound = query.isError && toApiError(query.error).status === 404
  const heading = usePageHeading(
    notFound ? t('dataExport.page.notFoundTitle') : titleOf(query.data, t),
    query.data?.status ?? query.status,
  )

  if (query.isPending) {
    return (
      <div className="mx-auto flex w-full max-w-2xl flex-col gap-5" aria-busy="true">
        <Skeleton className="h-12 w-2/3" />
        <Skeleton className="h-40" />
        <Skeleton className="h-48" />
      </div>
    )
  }

  if (notFound) {
    return (
      <StatusScreen
        icon={<FileQuestion className="size-7" />}
        title={t('dataExport.page.notFoundTitle')}
        body={t('dataExport.page.notFoundBody')}
        heading={heading}
        testId="export-not-found"
      >
        <Link to={routes.home} className={buttonClasses('dark', 'lg')}>
          {t('dataExport.page.home')}
        </Link>
      </StatusScreen>
    )
  }

  if (query.isError) {
    return (
      <Alert tone="danger" className="mx-auto max-w-2xl">
        <p>{t('dataExport.loadFailed')}</p>
        <Button variant="outline" className="mt-3" onClick={() => void query.refetch()}>
          {t('common.retry')}
        </Button>
      </Alert>
    )
  }

  const data = query.data
  if (data.status === 'READY') {
    return <ReadyExport data={data} heading={heading} />
  }
  if (data.status === 'QUEUED' || data.status === 'RUNNING') {
    return (
      <StatusScreen
        icon={<Spinner className="size-7" />}
        title={t('dataExport.page.preparingTitle')}
        body={t('dataExport.preparingHint')}
        heading={heading}
        testId="export-preparing"
      >
        <Link to={ACCOUNT_SETTINGS} className={buttonClasses('outline', 'lg')}>
          {t('dataExport.page.openSettings')}
        </Link>
      </StatusScreen>
    )
  }
  return <RequestAgain status={data.status} heading={heading} />
}

function titleOf(data: DataExport | undefined, t: (key: string) => string) {
  if (data?.status === 'EXPIRED') return t('dataExport.page.expiredTitle')
  if (data?.status === 'FAILED') return t('dataExport.page.failedTitle')
  if (data?.status === 'QUEUED' || data?.status === 'RUNNING') return t('dataExport.page.preparingTitle')
  return t('dataExport.page.title')
}

function StatusScreen({
  icon,
  title,
  body,
  heading,
  testId,
  children,
}: {
  icon: ReactNode
  title: string
  body: string
  heading: Ref<HTMLHeadingElement>
  testId: string
  children?: ReactNode
}) {
  return (
    <div className="mx-auto flex max-w-lg flex-col items-center gap-4 py-10 text-center" data-testid={testId}>
      <span aria-hidden="true" className="grid size-16 place-items-center rounded-2xl border-[1.5px] border-ink bg-sun">
        {icon}
      </span>
      <h1 ref={heading} tabIndex={-1} className="text-2xl tracking-tight outline-none sm:text-3xl">
        {title}
      </h1>
      <p className="leading-relaxed text-muted">{body}</p>
      {children && <div className="flex flex-wrap justify-center gap-3">{children}</div>}
    </div>
  )
}

/** Gói hết hạn hoặc tạo lỗi: yêu cầu xuất lại ngay tại đây (vẫn theo giới hạn mỗi ngày một lần). */
function RequestAgain({ status, heading }: { status: 'EXPIRED' | 'FAILED'; heading: Ref<HTMLHeadingElement> }) {
  const { t } = useTranslation()
  const format = useDateFormat()
  const { account } = useAuth()
  const request = useRequestDataExport()
  const [error, setError] = useState<string | null>(null)

  const requestAgain = async () => {
    setError(null)
    try {
      await request.mutateAsync()
    } catch (caught) {
      const apiError = toApiError(caught)
      const next = apiError.details?.nextAvailableAt
      const text = t(`errors.${apiError.code}`, { defaultValue: t('errors.INTERNAL_ERROR') })
      setError(typeof next === 'string' ? `${text} ${t('dataExport.nextAt', { time: format.dateTime(next) })}` : text)
    }
  }

  if (request.isSuccess) {
    return (
      <StatusScreen
        icon={<FileArchive className="size-7" />}
        title={t('dataExport.page.requestedTitle')}
        body={t('dataExport.page.requestedBody', { email: account?.email ?? '' })}
        heading={heading}
        testId="export-requested"
      >
        <Link to={ACCOUNT_SETTINGS} className={buttonClasses('dark', 'lg')}>
          {t('dataExport.page.openSettings')}
        </Link>
      </StatusScreen>
    )
  }

  return (
    <StatusScreen
      icon={<Hourglass className="size-7" />}
      title={status === 'EXPIRED' ? t('dataExport.page.expiredTitle') : t('dataExport.page.failedTitle')}
      body={status === 'EXPIRED' ? t('dataExport.page.expiredBody') : t('dataExport.failedBody')}
      heading={heading}
      testId={status === 'EXPIRED' ? 'export-expired' : 'export-failed'}
    >
      <div className="flex w-full flex-col items-center gap-3">
        {error && (
          <Alert tone="danger" className="w-full text-left" data-testid="export-request-error">
            {error}
          </Alert>
        )}
        <CtaButton
          className="max-w-sm"
          loading={request.isPending}
          hint={t('dataExport.requestHint')}
          onClick={() => void requestAgain()}
        >
          {t('dataExport.requestAgain')}
        </CtaButton>
      </div>
    </StatusScreen>
  )
}

function ReadyExport({ data, heading }: { data: DataExport; heading: Ref<HTMLHeadingElement> }) {
  const { t, i18n } = useTranslation()
  const format = useDateFormat()
  const facts = [
    { label: t('dataExport.page.created'), value: format.dateTime(data.completedAt ?? data.requestedAt) },
    { label: t('dataExport.page.size'), value: formatBytes(data.sizeBytes ?? 0, i18n.language) },
    { label: t('dataExport.page.files'), value: String(data.fileCount ?? 0) },
    { label: t('dataExport.page.until'), value: data.expiresAt ? format.dateTime(data.expiresAt) : '—' },
  ]

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-6">
      <header className="flex items-start gap-4">
        <span
          aria-hidden="true"
          className="grid size-14 shrink-0 place-items-center rounded-2xl border-[1.5px] border-ink bg-sun"
        >
          <FileArchive className="size-7" />
        </span>
        <div className="flex min-w-0 flex-col gap-1">
          <h1 ref={heading} tabIndex={-1} className="text-3xl tracking-tight outline-none">
            {t('dataExport.page.title')}
          </h1>
          <p className="leading-relaxed text-muted">{t('dataExport.page.subtitle')}</p>
        </div>
      </header>

      <section
        aria-label={t('dataExport.page.contents')}
        className="flex flex-col gap-5 rounded-card border-[1.5px] border-ink bg-surface p-5 sm:p-6"
      >
        <dl className="grid grid-cols-2 gap-4 sm:grid-cols-4">
          {facts.map((fact) => (
            <div key={fact.label} className="flex flex-col gap-0.5">
              <dt className="text-[13px] text-muted">{fact.label}</dt>
              <dd className="font-medium">{fact.value}</dd>
            </div>
          ))}
        </dl>
        <div className="flex flex-col gap-2">
          <h2 className="text-sm font-medium">{t('dataExport.page.contents')}</h2>
          <ul className="flex flex-wrap gap-2" data-testid="export-sections">
            {data.sections.map((section) => (
              <li
                key={section.id}
                className="inline-flex items-center gap-2 rounded-xl border-[1.5px] border-ink bg-sky-soft px-3 py-1.5 text-sm"
              >
                {t(`dataExport.sections.${section.id}`, { defaultValue: section.title })}
                <span className="text-[13px] text-muted">
                  {t('dataExport.page.sectionFiles', { count: section.fileCount })}
                </span>
              </li>
            ))}
          </ul>
        </div>
      </section>

      <DownloadForm data={data} />
    </div>
  )
}

/** Xác nhận lại rồi tải: nhập mật khẩu, hoặc đăng nhập lại nếu tài khoản chỉ dùng nhà cung cấp định danh. */
function DownloadForm({ data }: { data: DataExport }) {
  const { t } = useTranslation()
  const { account } = useAuth()
  const download = useDownloadDataExport(data.id)
  const passwordId = useId()
  const passwordInput = useRef<HTMLInputElement>(null)
  const [password, setPassword] = useState('')
  const [shown, setShown] = useState(false)
  const [forcedReauth, setForcedReauth] = useState(false)
  const [fieldError, setFieldError] = useState<string | undefined>()
  const [formError, setFormError] = useState<string | null>(null)
  const [lockedUntil, setLockedUntil] = useState<number | null>(null)
  const [link, setLink] = useState<DataExportLink | null>(null)
  const hasPassword = account?.hasPassword !== false
  const needsReauth = Boolean(data.reauthenticationRequired) || forcedReauth
  const relogin = useSignOutToReauthenticate()

  const focusPassword = () => requestAnimationFrame(() => passwordInput.current?.focus())
  const handleUnlocked = useCallback(() => {
    setLockedUntil(null)
    focusPassword()
  }, [])

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setFormError(null)
    setFieldError(undefined)
    setLink(null)
    if (needsReauth && hasPassword && !password) {
      setFieldError(t('validation.passwordRequired'))
      focusPassword()
      return
    }
    try {
      const result = await download.mutateAsync(needsReauth && hasPassword ? password : undefined)
      setPassword('')
      setShown(false)
      setLink(result)
      downloadFromUrl(result.url)
    } catch (caught) {
      const error = toApiError(caught)
      setPassword('')
      if (error.code === 'REAUTHENTICATION_REQUIRED') {
        setForcedReauth(true)
        if (hasPassword) focusPassword()
      } else if (error.code === 'CURRENT_PASSWORD_INVALID') {
        const remaining = detailNumber(error, 'remainingAttempts')
        setFieldError(
          [t('dataExport.page.wrongPassword'), remaining !== undefined && remaining <= 2
            ? t('dataExport.page.remaining', { count: remaining })
            : null].filter(Boolean).join(' '),
        )
        focusPassword()
      } else if (error.code === 'ACCOUNT_LOCKED') {
        setLockedUntil(deadlineIn(detailNumber(error, 'retryAfterSeconds') ?? 900))
      } else if (error.code !== 'EXPORT_EXPIRED' && error.code !== 'EXPORT_NOT_READY') {
        // Gói vừa hết hạn hoặc chưa sẵn sàng: trang tự tải lại trạng thái và đổi màn hình.
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
          className="grid size-10 shrink-0 place-items-center rounded-xl border-[1.5px] border-ink bg-lilac"
        >
          <ShieldCheck className="size-5" />
        </span>
        <div className="flex flex-col gap-1">
          <h2 id={`${passwordId}-title`} className="text-lg font-semibold">
            {t('dataExport.page.reauthTitle')}
          </h2>
          <p className="text-sm leading-relaxed text-ink-soft">
            {needsReauth
              ? hasPassword
                ? t('dataExport.page.reauthBody')
                : t('dataExport.page.reauthNoPassword')
              : t('dataExport.page.freshHint')}
          </p>
        </div>
      </div>

      {lockedUntil !== null && (
        <LockoutNotice
          lockedUntil={lockedUntil}
          onUnlocked={handleUnlocked}
          title={t('dataExport.page.lockedTitle')}
          body={t('dataExport.page.lockedBody')}
        />
      )}
      {formError && (
        <Alert tone="danger" data-testid="export-download-error">
          {formError}
        </Alert>
      )}
      {link && (
        <Alert tone="success" data-testid="export-downloading">
          <p>{t('dataExport.page.downloading', { file: data.fileName ?? '' })}</p>
          <a href={link.url} className="font-medium underline underline-offset-4">
            {t('dataExport.page.downloadAgain')}
          </a>
        </Alert>
      )}

      {/* Giúp trình quản lý mật khẩu điền đúng tài khoản. */}
      <input type="email" name="username" autoComplete="username" value={account?.email ?? ''} readOnly hidden />
      {needsReauth && hasPassword && (
        <TextField
          ref={passwordInput}
          id={passwordId}
          type={shown ? 'text' : 'password'}
          label={t('dataExport.page.password')}
          autoComplete="current-password"
          value={password}
          disabled={lockedUntil !== null}
          onChange={(event) => setPassword(event.target.value)}
          error={fieldError}
          trailing={<PasswordVisibilityToggle shown={shown} onToggle={() => setShown(!shown)} controls={passwordId} />}
        />
      )}

      {needsReauth && !hasPassword ? (
        <Button
          variant="dark"
          size="lg"
          loading={relogin.isPending}
          onClick={() => relogin.mutate()}
          icon={<LogIn className="size-5" aria-hidden="true" />}
        >
          {t('dataExport.page.relogin')}
        </Button>
      ) : (
        <CtaButton type="submit" loading={download.isPending} disabled={lockedUntil !== null} hint={data.fileName ?? undefined}>
          <span className="inline-flex items-center gap-2">
            <Download className="size-5" aria-hidden="true" />
            {t('dataExport.download')}
          </span>
        </CtaButton>
      )}
      <p className="text-[13px] leading-snug text-muted">{t('dataExport.page.keepSafe')}</p>
    </form>
  )
}
