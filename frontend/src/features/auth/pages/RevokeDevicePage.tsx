import { MonitorX, RotateCw, Unlink } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { Trans, useTranslation } from 'react-i18next'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { routes } from '@/app/routes'
import { toApiError } from '@/shared/api/errors'
import { formatDateTime } from '@/shared/lib/date'
import { usePageHeading } from '@/shared/hooks/usePageHeading'
import { Alert, Button, Spinner, buttonClasses } from '@/shared/ui'
import { authApi } from '../api/authApi'
import { useSession } from '../api/queries'
import { StatusIcon } from '../components/StatusIcon'
import type { RevokedByLink } from '../types'

type State =
  | { kind: 'revoking' }
  | { kind: 'done'; result: RevokedByLink }
  | { kind: 'invalid' }
  | { kind: 'failed'; code: string }

/** Mỗi đường dẫn chỉ gửi lên máy chủ một lần, kể cả khi StrictMode chạy hiệu ứng hai lần. */
const inflight = new Map<string, Promise<RevokedByLink>>()
function revokeOnce(token: string) {
  let request = inflight.get(token)
  if (!request) {
    request = authApi.revokeByLink(token)
    inflight.set(token, request)
  }
  return request
}

/** Nút "Đăng xuất thiết bị đó" trong thư cảnh báo thiết bị mới (NCL-01-CN-002, TC-03). */
export function RevokeDevicePage() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const session = useSession()
  // Giữ chuỗi bí mật trong bộ nhớ rồi xóa khỏi thanh địa chỉ.
  const [token] = useState(() => params.get('token')?.trim() || null)
  const [state, setState] = useState<State>(() => (token ? { kind: 'revoking' } : { kind: 'invalid' }))
  const heading = usePageHeading(t('revokeLink.successTitle'), state.kind)

  useEffect(() => {
    if (params.has('token')) navigate(routes.revokeDevice, { replace: true })
  }, [navigate, params])

  const run = useCallback(() => {
    if (!token) return () => undefined
    let active = true
    revokeOnce(token)
      .then((result) => {
        if (active) setState({ kind: 'done', result })
      })
      .catch((error: unknown) => {
        const apiError = toApiError(error)
        if (apiError.code !== 'REVOKE_LINK_INVALID' && apiError.code !== 'VALIDATION_FAILED') inflight.delete(token)
        if (!active) return
        setState(
          apiError.code === 'REVOKE_LINK_INVALID' || apiError.code === 'VALIDATION_FAILED'
            ? { kind: 'invalid' }
            : { kind: 'failed', code: apiError.code },
        )
      })
    return () => {
      active = false
    }
  }, [token])

  useEffect(() => run(), [run])

  const signedIn = session.data?.authenticated
  const nextLink = (
    <Link to={signedIn ? routes.devices : routes.login} className={buttonClasses('dark', 'lg')}>
      {signedIn ? t('revokeLink.toDevices') : t('revokeLink.toLogin')}
    </Link>
  )

  if (state.kind === 'revoking') {
    return (
      <div className="flex flex-col gap-7">
        <StatusIcon tone="lilac">
          <Spinner className="size-7" />
        </StatusIcon>
        <h1 ref={heading} tabIndex={-1} className="text-[28px] leading-tight tracking-tight outline-none">
          {t('revokeLink.revoking')}
        </h1>
      </div>
    )
  }

  if (state.kind === 'done') {
    const device = t('devices.deviceName', { browser: state.result.browser, os: state.result.operatingSystem })
    return (
      <div className="flex flex-col gap-7">
        <StatusIcon tone="success">
          <MonitorX className="size-8 text-success" />
        </StatusIcon>
        <header className="flex flex-col gap-3">
          <h1 ref={heading} tabIndex={-1} className="text-[28px] leading-tight tracking-tight outline-none sm:text-[32px]">
            {state.result.alreadyRevoked ? t('revokeLink.alreadyTitle') : t('revokeLink.successTitle')}
          </h1>
          <p className="text-[15px] leading-relaxed text-ink-soft" data-testid="revoke-result">
            <Trans
              i18nKey={state.result.alreadyRevoked ? 'revokeLink.alreadyBody' : 'revokeLink.successBody'}
              values={{ device, time: formatDateTime(state.result.signedInAt, i18n.language) }}
              components={{ strong: <strong className="font-semibold text-ink" /> }}
            />
          </p>
        </header>
        <Alert tone="warning">{t('revokeLink.advice')}</Alert>
        {nextLink}
      </div>
    )
  }

  if (state.kind === 'invalid') {
    return (
      <div className="flex flex-col gap-7">
        <StatusIcon tone="danger">
          <Unlink className="size-8" />
        </StatusIcon>
        <header className="flex flex-col gap-3">
          <h1 ref={heading} tabIndex={-1} className="text-[28px] leading-tight tracking-tight outline-none sm:text-[32px]">
            {t('revokeLink.invalidTitle')}
          </h1>
          <p className="text-[15px] leading-relaxed text-ink-soft">{t('revokeLink.invalidBody')}</p>
        </header>
        {nextLink}
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-7">
      <StatusIcon tone="danger">
        <Unlink className="size-8" />
      </StatusIcon>
      <h1 ref={heading} tabIndex={-1} className="text-[28px] leading-tight tracking-tight outline-none">
        {t('revokeLink.failedTitle')}
      </h1>
      <Alert tone="danger">{t(`errors.${state.code}`, { defaultValue: t('errors.INTERNAL_ERROR') })}</Alert>
      <Button
        variant="dark"
        size="lg"
        icon={<RotateCw className="size-4" aria-hidden="true" />}
        onClick={() => {
          setState({ kind: 'revoking' })
          run()
        }}
      >
        {t('common.retry')}
      </Button>
    </div>
  )
}
