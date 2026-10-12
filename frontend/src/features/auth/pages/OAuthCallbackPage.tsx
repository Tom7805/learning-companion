import { useQueryClient } from '@tanstack/react-query'
import { CircleAlert, Link2, ShieldAlert, UserPlus } from 'lucide-react'
import { useEffect, useState, type ReactNode, type RefObject } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { routes } from '@/app/routes'
import { useLegalCurrent } from '@/features/privacy/api/queries'
import { detailNumber, toApiError, type ApiError } from '@/shared/api/errors'
import { queryKeys } from '@/shared/api/queryKeys'
import { deadlineIn } from '@/shared/hooks/useCountdown'
import { usePageHeading } from '@/shared/hooks/usePageHeading'
import { Alert, Checkbox, CtaButton, Spinner, TextField, buttonClasses } from '@/shared/ui'
import { authApi } from '../api/authApi'
import { LockoutNotice } from '../components/LockoutNotice'
import { ProviderLogo } from '../components/OAuthButtons'
import { PasswordVisibilityToggle } from '../components/PasswordVisibilityToggle'
import { StatusIcon } from '../components/StatusIcon'
import type { OAuthResult, SessionStatus } from '../types'

type State =
  | { kind: 'processing' }
  | { kind: 'terms'; result: OAuthResult }
  | { kind: 'link'; result: OAuthResult }
  | { kind: 'failed'; code: string }

type HeadingRef = RefObject<HTMLHeadingElement | null>

const PROVIDER_NAMES: Record<string, string> = { google: 'Google' }

/**
 * Mã ủy quyền chỉ đổi được một lần: StrictMode chạy hiệu ứng hai lần ở môi trường phát triển nên mỗi bộ
 * mã chỉ được gửi lên máy chủ đúng một lần.
 */
const inflight = new Map<string, Promise<OAuthResult>>()
function callbackOnce(provider: string, code: string | null, state: string | null, error: string | null) {
  const key = `${provider}:${state}:${code}:${error}`
  let request = inflight.get(key)
  if (!request) {
    request = authApi.oauthCallback(provider, { code, state, error })
    inflight.set(key, request)
  }
  return request
}

/** Nhà cung cấp định danh chuyển người dùng về đây (NCL-01-CN-007). */
export function OAuthCallbackPage() {
  const { t } = useTranslation()
  const { provider = 'google' } = useParams()
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  // Giữ mã trong bộ nhớ rồi xóa khỏi thanh địa chỉ để không lọt vào lịch sử trình duyệt.
  const [callback] = useState(() => ({
    code: params.get('code'),
    state: params.get('state'),
    error: params.get('error'),
  }))
  const [state, setState] = useState<State>({ kind: 'processing' })
  const providerName = PROVIDER_NAMES[provider] ?? provider
  const heading = usePageHeading(t(titleKey(state)), state.kind)

  useEffect(() => {
    if (params.has('state') || params.has('code') || params.has('error')) {
      navigate(routes.oauthCallback.replace(':provider', provider), { replace: true })
    }
  }, [navigate, params, provider])

  useEffect(() => {
    let active = true
    callbackOnce(provider, callback.code, callback.state, callback.error)
      .then((result) => {
        if (active) handleResult(result)
      })
      .catch((error: unknown) => {
        if (active) setState({ kind: 'failed', code: toApiError(error).code })
      })
    return () => {
      active = false
    }
    // Chỉ chạy một lần cho bộ mã nhận được lúc mở trang.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function handleResult(result: OAuthResult) {
    if (result.status === 'SIGNED_IN' && result.account) {
      queryClient.setQueryData<SessionStatus>(queryKeys.session, { authenticated: true, account: result.account })
      navigate(result.returnTo ?? routes.home, { replace: true })
    } else if (result.status === 'LINKED') {
      const base = result.returnTo ?? routes.home
      navigate(`${base}${base.includes('?') ? '&' : '?'}settings=accounts&linked=${result.provider}`, { replace: true })
    } else if (result.status === 'TERMS_REQUIRED') {
      setState({ kind: 'terms', result })
    } else {
      setState({ kind: 'link', result })
    }
  }

  return (
    <div className="flex flex-col gap-7">
      {state.kind === 'processing' && (
        <>
          <StatusIcon tone="lilac">
            <Spinner className="size-7" />
          </StatusIcon>
          <h1 ref={heading} tabIndex={-1} className="text-[28px] leading-tight tracking-tight outline-none">
            {t('oauth.processing', { provider: providerName })}
          </h1>
        </>
      )}
      {state.kind === 'terms' && (
        <CompleteSignup
          result={state.result}
          providerName={providerName}
          headingRef={heading}
          onResult={handleResult}
          onExpired={() => setState({ kind: 'failed', code: 'OAUTH_PENDING_INVALID' })}
        />
      )}
      {state.kind === 'link' && (
        <LinkWithPassword
          result={state.result}
          providerName={providerName}
          headingRef={heading}
          onResult={handleResult}
          onExpired={() => setState({ kind: 'failed', code: 'OAUTH_PENDING_INVALID' })}
        />
      )}
      {state.kind === 'failed' && <Failed code={state.code} providerName={providerName} headingRef={heading} />}
    </div>
  )
}

function titleKey(state: State) {
  switch (state.kind) {
    case 'processing':
      return 'oauth.processingTitle'
    case 'terms':
      return 'oauth.signup.title'
    case 'link':
      return 'oauth.link.title'
    default:
      return 'oauth.failed.title'
  }
}

function Heading({ headingRef, title, children }: { headingRef: HeadingRef; title: string; children?: ReactNode }) {
  return (
    <header className="flex flex-col gap-3">
      <h1 ref={headingRef} tabIndex={-1} className="text-[28px] leading-tight tracking-tight outline-none sm:text-[32px]">
        {title}
      </h1>
      {children && <div className="text-[15px] leading-relaxed text-ink-soft">{children}</div>}
    </header>
  )
}

function VerifiedEmail({ email, provider, providerName }: { email: string; provider: string; providerName: string }) {
  const { t } = useTranslation()
  return (
    <div className="flex items-center gap-3 rounded-field border-[1.5px] border-ink bg-paper px-4 py-3" data-testid="oauth-email">
      <span className="grid size-9 shrink-0 place-items-center rounded-full border-[1.5px] border-ink bg-surface">
        <ProviderLogo id={provider} />
      </span>
      <div className="flex min-w-0 flex-col">
        <span className="truncate font-medium">{email}</span>
        <span className="text-[13px] text-muted">{t('oauth.verifiedBy', { provider: providerName })}</span>
      </div>
    </div>
  )
}

interface StepProps {
  result: OAuthResult
  providerName: string
  headingRef: HeadingRef
  onResult: (result: OAuthResult) => void
  onExpired: () => void
}

/** Lần đầu dùng nhà cung cấp: đồng ý điều khoản như khi đăng ký thường rồi mới tạo tài khoản (TC-01). */
function CompleteSignup({ result, providerName, headingRef, onResult, onExpired }: StepProps) {
  const { t } = useTranslation()
  const legal = useLegalCurrent()
  const [displayName, setDisplayName] = useState(result.displayName ?? '')
  const [accepted, setAccepted] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [nameError, setNameError] = useState<string | null>(null)
  const [termsError, setTermsError] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)

  const submit = async () => {
    setFormError(null)
    setNameError(null)
    setTermsError(null)
    const name = displayName.trim().replace(/\s+/g, ' ')
    if (!name) return setNameError(t('validation.displayNameRequired'))
    if ([...name].length > 50) return setNameError(t('validation.displayNameTooLong'))
    if (!accepted) return setTermsError(t('validation.termsRequired'))
    const terms = legal.data ?? (await legal.refetch()).data
    if (!terms) return setFormError(t('errors.NETWORK_ERROR'))
    setSubmitting(true)
    try {
      onResult(
        await authApi.oauthSignup({
          token: result.pendingToken!,
          displayName: name,
          acceptTerms: true,
          termsVersion: terms.terms.version,
          privacyVersion: terms.privacy.version,
        }),
      )
    } catch (caught) {
      const error = toApiError(caught)
      if (error.code === 'OAUTH_PENDING_INVALID') return onExpired()
      if (error.code.startsWith('DISPLAY_NAME')) setNameError(t(`errors.${error.code}`))
      else if (error.code === 'TERMS_VERSION_OUTDATED' || error.code === 'TERMS_NOT_ACCEPTED') {
        setAccepted(false)
        void legal.refetch()
        setTermsError(t(`errors.${error.code}`))
      } else setFormError(t(`errors.${error.code}`, { defaultValue: t('errors.INTERNAL_ERROR') }))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form
      noValidate
      className="flex flex-col gap-6"
      onSubmit={(event) => {
        event.preventDefault()
        void submit()
      }}
    >
      <StatusIcon tone="sun">
        <UserPlus className="size-7" strokeWidth={2} />
      </StatusIcon>
      <Heading headingRef={headingRef} title={t('oauth.signup.title')}>
        <p>{t('oauth.signup.body', { provider: providerName })}</p>
      </Heading>
      <VerifiedEmail email={result.email ?? ''} provider={result.provider} providerName={providerName} />
      {formError && <Alert tone="danger">{formError}</Alert>}
      <fieldset disabled={submitting} className="flex flex-col gap-5">
        <TextField
          label={t('register.displayName.label')}
          value={displayName}
          onChange={(event) => setDisplayName(event.target.value)}
          autoComplete="nickname"
          error={nameError ?? undefined}
          hint={t('register.displayName.hint')}
        />
        <div className="flex flex-col gap-1.5">
          <Checkbox
            checked={accepted}
            onChange={(event) => setAccepted(event.target.checked)}
            error={termsError ?? undefined}
            label={
              <Trans
                i18nKey="register.terms"
                components={{
                  terms: <TermsLink href={routes.terms} />,
                  privacy: <TermsLink href={routes.privacy} />,
                }}
              />
            }
          />
          {legal.data && (
            <p className="pl-9 text-xs text-subtle">{t('register.termsVersion', { version: legal.data.terms.version })}</p>
          )}
        </div>
      </fieldset>
      <CtaButton type="submit" loading={submitting} hint={submitting ? undefined : t('oauth.signup.hint')}>
        {t('oauth.signup.submit')}
      </CtaButton>
      <Link to={routes.login} className="text-center text-sm text-muted underline-offset-4 hover:text-ink hover:underline">
        {t('oauth.cancel')}
      </Link>
    </form>
  )
}

function TermsLink({ href, children }: { href: string; children?: ReactNode }) {
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

/**
 * Địa chỉ thư đã có tài khoản dùng mật khẩu: không tự gộp, người học nhập mật khẩu một lần để chứng minh là
 * chủ tài khoản rồi mới liên kết (TC-02). Nhập sai được tính như đăng nhập sai.
 */
function LinkWithPassword({ result, providerName, headingRef, onResult, onExpired }: StepProps) {
  const { t } = useTranslation()
  const [password, setPassword] = useState('')
  const [shown, setShown] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)
  const [lockedUntil, setLockedUntil] = useState<number | null>(null)
  const email = result.email ?? ''

  const submit = async () => {
    if (!password) {
      setError({ code: 'PASSWORD_REQUIRED', message: '', status: 400, fieldErrors: [] })
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      onResult(await authApi.oauthLink(result.pendingToken!, password))
    } catch (caught) {
      const apiError = toApiError(caught)
      setPassword('')
      if (apiError.code === 'OAUTH_PENDING_INVALID') return onExpired()
      if (apiError.code === 'ACCOUNT_LOCKED') {
        setLockedUntil(deadlineIn(detailNumber(apiError, 'retryAfterSeconds') ?? 900))
        return
      }
      setError(apiError)
    } finally {
      setSubmitting(false)
    }
  }

  const attemptsLeft = detailNumber(error, 'remainingAttempts')
  const message =
    error?.code === 'PASSWORD_REQUIRED'
      ? t('validation.passwordRequired')
      : error?.code === 'INVALID_CREDENTIALS'
        ? t('oauth.link.wrongPassword')
        : error
          ? t(`errors.${error.code}`, { defaultValue: t('errors.INTERNAL_ERROR') })
          : undefined

  return (
    <form
      noValidate
      className="flex flex-col gap-6"
      onSubmit={(event) => {
        event.preventDefault()
        void submit()
      }}
    >
      <StatusIcon tone="sky">
        <Link2 className="size-7" strokeWidth={2} />
      </StatusIcon>
      <Heading headingRef={headingRef} title={t('oauth.link.title')}>
        <p>
          <Trans
            i18nKey="oauth.link.body"
            values={{ email, provider: providerName }}
            components={{ strong: <strong className="font-semibold break-words text-ink" /> }}
          />
        </p>
      </Heading>
      <p className="flex items-start gap-3 rounded-card border-[1.5px] border-ink bg-lilac-soft p-4 text-sm leading-relaxed">
        <ShieldAlert className="mt-0.5 size-5 shrink-0" aria-hidden="true" />
        {t('oauth.link.whyPassword')}
      </p>
      {lockedUntil !== null && <LockoutNotice lockedUntil={lockedUntil} onUnlocked={() => setLockedUntil(null)} />}
      <fieldset disabled={submitting || lockedUntil !== null} className="flex flex-col gap-3">
        <input type="email" name="username" autoComplete="username" value={email} readOnly hidden />
        <TextField
          label={t('register.password.label')}
          type={shown ? 'text' : 'password'}
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          autoComplete="current-password"
          autoFocus
          error={message}
          hint={
            attemptsLeft !== undefined && attemptsLeft <= 2 ? (
              <span className="font-medium text-danger" data-testid="link-attempts-left">
                {t('login.attemptsLeft', { count: attemptsLeft })}
              </span>
            ) : undefined
          }
          trailing={<PasswordVisibilityToggle shown={shown} onToggle={() => setShown((value) => !value)} />}
        />
        <Link
          to={routes.forgotPassword}
          state={{ email }}
          className="self-end text-sm font-medium text-accent-ink underline-offset-4 hover:underline"
        >
          {t('login.forgot')}
        </Link>
      </fieldset>
      <CtaButton type="submit" loading={submitting} disabled={lockedUntil !== null} hint={t('oauth.link.hint', { provider: providerName })}>
        {t('oauth.link.submit')}
      </CtaButton>
      <Link to={routes.login} className="text-center text-sm text-muted underline-offset-4 hover:text-ink hover:underline">
        {t('oauth.cancel')}
      </Link>
    </form>
  )
}

function Failed({ code, providerName, headingRef }: { code: string; providerName: string; headingRef: HeadingRef }) {
  const { t, i18n } = useTranslation()
  const key = `oauth.failed.${code}`
  const body = i18n.exists(key) ? t(key, { provider: providerName }) : t(`errors.${code}`, { defaultValue: t('errors.INTERNAL_ERROR') })
  const linking = code === 'EXTERNAL_IDENTITY_IN_USE' || code === 'EXTERNAL_IDENTITY_ALREADY_LINKED'
  return (
    <>
      <StatusIcon tone="danger">
        <CircleAlert className="size-8" strokeWidth={2} />
      </StatusIcon>
      <Heading headingRef={headingRef} title={t('oauth.failed.title')}>
        <p data-testid="oauth-failed">{body}</p>
      </Heading>
      <Link to={linking ? `${routes.home}?settings=accounts` : routes.login} className={buttonClasses('dark', 'lg')}>
        {linking ? t('oauth.failed.backToSettings') : t('oauth.failed.retry')}
      </Link>
      {!linking && <p className="text-center text-sm text-muted">{t('oauth.failed.safe')}</p>}
    </>
  )
}
