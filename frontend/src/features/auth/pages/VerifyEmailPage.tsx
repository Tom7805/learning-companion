import { useQueryClient } from '@tanstack/react-query'
import { CircleCheckBig, Clock3, MailCheck, RotateCw, Unlink } from 'lucide-react'
import { useCallback, useEffect, useState, type ReactNode, type RefObject } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { routes } from '@/app/routes'
import { toApiError } from '@/shared/api/errors'
import { queryKeys } from '@/shared/api/queryKeys'
import { deadlineIn, formatMinutesSeconds, useCountdown } from '@/shared/hooks/useCountdown'
import { usePageHeading } from '@/shared/hooks/usePageHeading'
import { Alert, Button, CtaButton, Spinner, buttonClasses } from '@/shared/ui'
import { authApi } from '../api/authApi'
import { useResendVerification, useSession } from '../api/queries'
import { ResendByEmailForm } from '../components/ResendByEmailForm'
import { StatusIcon } from '../components/StatusIcon'
import { pendingVerification } from '../lib/pendingVerification'
import type { Account, PendingVerification, SessionStatus } from '../types'

type State =
  | { kind: 'verifying' }
  | { kind: 'success'; account: Account }
  | { kind: 'expired' }
  | { kind: 'used' }
  | { kind: 'invalid' }
  | { kind: 'failed'; code: string }

const REDIRECT_SECONDS = 5

/**
 * StrictMode chạy hiệu ứng hai lần ở môi trường phát triển; đường dẫn chỉ dùng được một lần
 * nên mỗi chuỗi bí mật chỉ được gửi lên máy chủ đúng một lần.
 */
const inflight = new Map<string, Promise<Account>>()
function verifyOnce(token: string) {
  let request = inflight.get(token)
  if (!request) {
    request = authApi.verifyEmail(token)
    inflight.set(token, request)
  }
  return request
}

export function VerifyEmailPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [params] = useSearchParams()
  // Giữ chuỗi bí mật trong bộ nhớ rồi xóa khỏi thanh địa chỉ để không lọt vào lịch sử trình duyệt.
  const [token] = useState(() => params.get('token')?.trim() || null)
  const [state, setState] = useState<State>(() => (token ? { kind: 'verifying' } : { kind: 'invalid' }))
  const heading = usePageHeading(t(titleKey(state)), state.kind)

  useEffect(() => {
    if (params.has('token')) {
      navigate(routes.verifyEmail, { replace: true })
    }
  }, [navigate, params])

  const run = useCallback(() => {
    if (!token) return () => undefined
    let active = true
    verifyOnce(token)
      .then((account) => {
        queryClient.setQueryData<SessionStatus>(queryKeys.session, { authenticated: true, account })
        pendingVerification.clear()
        if (active) setState({ kind: 'success', account })
      })
      .catch((error: unknown) => {
        const apiError = toApiError(error)
        if (apiError.code === 'NETWORK_ERROR' || apiError.code === 'INTERNAL_ERROR' || apiError.code === 'RATE_LIMITED') {
          inflight.delete(token)
        }
        if (!active) return
        setState(stateFor(apiError.code))
      })
    return () => {
      active = false
    }
  }, [queryClient, token])

  useEffect(() => run(), [run])

  return (
    <div className="flex flex-col gap-7">
      {state.kind === 'verifying' && (
        <>
          <StatusIcon tone="lilac">
            <Spinner className="size-7" />
          </StatusIcon>
          <h1 ref={heading} tabIndex={-1} className="text-[28px] leading-tight tracking-tight outline-none">
            {t('verify.verifying')}
          </h1>
        </>
      )}
      {state.kind === 'success' && <Success account={state.account} headingRef={heading} />}
      {state.kind === 'expired' && <Expired token={token!} headingRef={heading} />}
      {state.kind === 'used' && <Used headingRef={heading} />}
      {state.kind === 'invalid' && <Invalid headingRef={heading} />}
      {state.kind === 'failed' && (
        <>
          <StatusIcon tone="danger">
            <Unlink className="size-7" />
          </StatusIcon>
          <Heading headingRef={heading} title={t('verify.failed.title')} />
          <Alert tone="danger">{t(`errors.${state.code}`, { defaultValue: t('errors.INTERNAL_ERROR') })}</Alert>
          <Button
            variant="dark"
            size="lg"
            onClick={() => {
              setState({ kind: 'verifying' })
              run()
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
    case 'verifying':
      return 'verify.verifying'
    case 'success':
      return 'verify.success.title'
    case 'expired':
      return 'verify.expired.title'
    case 'used':
      return 'verify.used.title'
    case 'invalid':
      return 'verify.invalid.title'
    default:
      return 'verify.failed.title'
  }
}

function stateFor(code: string): State {
  switch (code) {
    case 'VERIFICATION_TOKEN_EXPIRED':
      return { kind: 'expired' }
    case 'VERIFICATION_TOKEN_USED':
      return { kind: 'used' }
    case 'VERIFICATION_TOKEN_INVALID':
    case 'VALIDATION_FAILED':
      return { kind: 'invalid' }
    default:
      return { kind: 'failed', code }
  }
}

type HeadingRef = RefObject<HTMLHeadingElement | null>

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

function Success({ account, headingRef }: { account: Account; headingRef: HeadingRef }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [redirectAt] = useState(() => deadlineIn(REDIRECT_SECONDS))
  const seconds = useCountdown(redirectAt)

  useEffect(() => {
    if (seconds === 0) navigate(routes.home, { replace: true })
  }, [seconds, navigate])

  return (
    <>
      <StatusIcon tone="success">
        <CircleCheckBig className="size-8 text-success" strokeWidth={2.25} />
      </StatusIcon>
      <Heading headingRef={headingRef} title={t('verify.success.title')}>
        <p>
          <Trans
            i18nKey="verify.success.body"
            values={{ name: account.displayName }}
            components={{ strong: <strong className="font-semibold text-ink" /> }}
          />
        </p>
      </Heading>
      <CtaButton onClick={() => navigate(routes.home, { replace: true })} hint={account.email}>
        {t('verify.success.cta')}
      </CtaButton>
      <p className="text-sm text-muted tabular-nums" data-testid="redirect-countdown">
        {t('verify.success.redirect', { seconds })}
      </p>
    </>
  )
}

function Expired({ token, headingRef }: { token: string; headingRef: HeadingRef }) {
  const { t } = useTranslation()
  const resend = useResendVerification()
  const [availableAt, setAvailableAt] = useState<number | null>(null)
  const [errorCode, setErrorCode] = useState<string | null>(null)
  const seconds = useCountdown(availableAt)

  const send = async () => {
    setErrorCode(null)
    try {
      const result = await resend.mutateAsync({ token })
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
      <Heading headingRef={headingRef} title={t('verify.expired.title')}>
        <p>{t('verify.expired.body')}</p>
      </Heading>
      {availableAt && (
        <Alert tone="success" data-testid="expired-resent">
          {t('verify.expired.sent')}
        </Alert>
      )}
      {errorCode && <Alert tone="danger">{t(`errors.${errorCode}`, { defaultValue: t('errors.INTERNAL_ERROR') })}</Alert>}
      <CtaButton onClick={() => void send()} loading={resend.isPending} disabled={seconds > 0}>
        {seconds > 0 ? t('checkEmail.resendIn', { time: formatMinutesSeconds(seconds) }) : t('verify.expired.cta')}
      </CtaButton>
      <BackToRegister />
    </>
  )
}

function Used({ headingRef }: { headingRef: HeadingRef }) {
  const { t } = useTranslation()
  const session = useSession()
  const signedIn = session.data?.authenticated
  return (
    <>
      <StatusIcon tone="sky">
        <MailCheck className="size-8" strokeWidth={2} />
      </StatusIcon>
      <Heading headingRef={headingRef} title={t('verify.used.title')}>
        <p>{t('verify.used.body')}</p>
      </Heading>
      <Link to={signedIn ? routes.home : routes.login} className={buttonClasses('dark', 'lg')}>
        {signedIn ? t('verify.used.ctaApp') : t('verify.used.ctaLogin')}
      </Link>
    </>
  )
}

function Invalid({ headingRef }: { headingRef: HeadingRef }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  return (
    <>
      <StatusIcon tone="danger">
        <Unlink className="size-8" strokeWidth={2} />
      </StatusIcon>
      <Heading headingRef={headingRef} title={t('verify.invalid.title')}>
        <p>{t('verify.invalid.body')}</p>
      </Heading>
      <div className="rounded-card border-[1.5px] border-ink p-5">
        <ResendByEmailForm
          onSent={(pending: PendingVerification) => {
            pendingVerification.save(pending)
            navigate(routes.checkEmail, { state: pending })
          }}
        />
      </div>
      <BackToRegister />
    </>
  )
}

function BackToRegister() {
  const { t } = useTranslation()
  return (
    <Link to={routes.register} className="text-center text-sm text-muted underline-offset-4 hover:text-ink hover:underline">
      {t('verify.backToRegister')}
    </Link>
  )
}
