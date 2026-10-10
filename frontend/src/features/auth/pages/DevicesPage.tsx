import { LogOut, MapPin, Monitor, ShieldAlert, Smartphone, Tablet } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { routes } from '@/app/routes'
import { toApiError } from '@/shared/api/errors'
import { usePageHeading } from '@/shared/hooks/usePageHeading'
import { cn } from '@/shared/lib/cn'
import { formatDateTime, formatRelative } from '@/shared/lib/date'
import { Alert, Badge, Button, ConfirmDialog, Skeleton } from '@/shared/ui'
import { useDeviceSessions, useRevokeOtherSessions, useRevokeSession } from '../api/queries'
import type { DeviceSession } from '../types'

type Pending = { kind: 'one'; session: DeviceSession } | { kind: 'others'; count: number } | null
type Notice = { tone: 'success' | 'danger'; text: string } | null

const iconTone = { DESKTOP: 'bg-sky', MOBILE: 'bg-sun', TABLET: 'bg-lilac' } as const

/** Thiết bị đang đăng nhập (NCL-01-CN-002): xem, đăng xuất từng thiết bị hoặc mọi thiết bị khác. */
export function DevicesPage() {
  const { t, i18n } = useTranslation()
  const heading = usePageHeading(t('devices.title'))
  const sessions = useDeviceSessions()
  const revokeOne = useRevokeSession()
  const revokeOthers = useRevokeOtherSessions()
  const [pending, setPending] = useState<Pending>(null)
  const [notice, setNotice] = useState<Notice>(null)

  const deviceName = (session: DeviceSession) =>
    t('devices.deviceName', { browser: session.browser, os: session.operatingSystem })
  // Thiết bị đang dùng luôn đứng đầu, các thiết bị khác theo lần hoạt động gần nhất.
  const list = [...(sessions.data ?? [])].sort(
    (a, b) => Number(b.current) - Number(a.current) || b.lastActiveAt.localeCompare(a.lastActiveAt),
  )
  const others = list.filter((session) => !session.current)

  const confirm = async () => {
    if (!pending) return
    try {
      if (pending.kind === 'one') {
        await revokeOne.mutateAsync(pending.session.id)
        if (pending.session.current) {
          window.location.replace(`${routes.login}?loggedOut=1`)
          return
        }
        setNotice({ tone: 'success', text: t('devices.revoked', { device: deviceName(pending.session) }) })
      } else {
        const result = await revokeOthers.mutateAsync()
        setNotice({ tone: 'success', text: t('devices.revokedOthers', { count: result.revokedCount }) })
      }
    } catch (error) {
      const apiError = toApiError(error)
      setNotice({ tone: 'danger', text: t(`errors.${apiError.code}`, { defaultValue: t('errors.INTERNAL_ERROR') }) })
    } finally {
      setPending(null)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div className="flex max-w-xl flex-col gap-2">
          <h1 ref={heading} tabIndex={-1} className="text-3xl tracking-tight outline-none">
            {t('devices.title')}
          </h1>
          <p className="leading-relaxed text-muted">{t('devices.subtitle')}</p>
        </div>
        <Button
          variant="accent"
          size="lg"
          disabled={others.length === 0}
          onClick={() => setPending({ kind: 'others', count: others.length })}
          icon={<LogOut className="size-4" aria-hidden="true" />}
          data-testid="revoke-others"
        >
          {t('devices.revokeOthers')}
        </Button>
      </header>

      {notice && (
        <Alert tone={notice.tone} data-testid="devices-notice" key={notice.text}>
          {notice.text}
        </Alert>
      )}

      <section
        aria-labelledby="devices-list-title"
        className="rounded-card border-[1.5px] border-ink bg-surface px-5 py-4 sm:px-6"
      >
        <div className="flex items-center justify-between gap-3 pb-3">
          <h2 id="devices-list-title" className="text-lg font-medium">
            {sessions.data ? t('devices.count', { count: list.length }) : t('devices.title')}
          </h2>
        </div>
        <div
          aria-hidden="true"
          className="hidden grid-cols-[minmax(0,1.7fr)_minmax(0,1fr)_minmax(0,1fr)_120px] gap-4 border-b border-line pb-2 text-xs text-muted md:grid"
        >
          <span>{t('devices.columns.device')}</span>
          <span>{t('devices.columns.location')}</span>
          <span>{t('devices.columns.lastActive')}</span>
          <span />
        </div>

        {sessions.isPending && (
          <div className="flex flex-col gap-3 py-4">
            <Skeleton className="h-14" />
            <Skeleton className="h-14" />
          </div>
        )}
        {sessions.isError && (
          <Alert tone="danger" className="my-4">
            <p>{t('devices.loadFailed')}</p>
            <Button variant="outline" className="mt-3" onClick={() => void sessions.refetch()}>
              {t('common.retry')}
            </Button>
          </Alert>
        )}

        <ul className="divide-y divide-line" data-testid="device-list">
          {list.map((session) => {
            const type = session.deviceType ?? 'DESKTOP'
            const Icon = type === 'MOBILE' ? Smartphone : type === 'TABLET' ? Tablet : Monitor
            const name = deviceName(session)
            return (
              <li
                key={session.id}
                data-testid="device-row"
                data-current={session.current}
                className="grid gap-3 py-4 md:grid-cols-[minmax(0,1.7fr)_minmax(0,1fr)_minmax(0,1fr)_120px] md:items-center md:gap-4"
              >
                <div className="flex min-w-0 items-center gap-3">
                  <span
                    className={cn(
                      'grid size-11 shrink-0 place-items-center rounded-full border-[1.5px] border-ink',
                      iconTone[type],
                    )}
                    aria-hidden="true"
                  >
                    <Icon className="size-5" />
                  </span>
                  <div className="min-w-0">
                    <p className="flex flex-wrap items-center gap-2 font-medium">
                      <span className="truncate">{name}</span>
                      {session.current && <Badge tone="sun">{t('devices.current')}</Badge>}
                    </p>
                    <p className="text-[13px] text-muted">
                      {t(`devices.type.${type}`)} ·{' '}
                      {t('devices.signedInAt', { time: formatDateTime(session.signedInAt, i18n.language) })}
                      {session.rememberDevice && <> · {t('devices.remembered')}</>}
                    </p>
                  </div>
                </div>
                <p className="flex items-center gap-1.5 text-sm text-ink-soft">
                  <MapPin className="size-4 shrink-0 text-muted md:hidden" aria-hidden="true" />
                  <span className="sr-only md:hidden">{t('devices.columns.location')}: </span>
                  {session.location ??
                    (session.localNetwork ? t('devices.location.local') : t('devices.location.unknown'))}
                </p>
                <p className="text-sm text-ink-soft">
                  <span className="sr-only md:hidden">{t('devices.columns.lastActive')}: </span>
                  {session.current ? (
                    <span className="inline-flex items-center gap-1.5 font-medium text-success">
                      <span className="size-2 rounded-full bg-success" aria-hidden="true" />
                      {t('devices.activeNow')}
                    </span>
                  ) : (
                    <time dateTime={session.lastActiveAt} title={formatDateTime(session.lastActiveAt, i18n.language)}>
                      {formatRelative(session.lastActiveAt, i18n.language)}
                    </time>
                  )}
                </p>
                <div className="md:text-right">
                  <Button
                    variant="outline"
                    className="h-9 px-3 text-sm"
                    onClick={() => setPending({ kind: 'one', session })}
                    aria-label={t('devices.signOutDevice', { device: name })}
                  >
                    {t('devices.signOut')}
                  </Button>
                </div>
              </li>
            )
          })}
        </ul>
        {sessions.data && others.length === 0 && (
          <p className="border-t border-line pt-3 text-sm text-muted">{t('devices.onlyThis')}</p>
        )}
      </section>

      <p className="flex items-start gap-3 rounded-card border-[1.5px] border-ink bg-lilac-soft p-4 text-sm leading-relaxed">
        <ShieldAlert className="mt-0.5 size-5 shrink-0" aria-hidden="true" />
        {t('devices.tip')}
      </p>

      <ConfirmDialog
        open={pending !== null}
        title={
          pending?.kind === 'one'
            ? t('devices.confirmTitle', { device: deviceName(pending.session) })
            : t('devices.confirmOthersTitle')
        }
        description={
          pending?.kind === 'one'
            ? pending.session.current
              ? t('devices.confirmSelfBody')
              : t('devices.confirmBody')
            : t('devices.confirmOthersBody', { count: pending?.kind === 'others' ? pending.count : 0 })
        }
        confirmLabel={t('devices.confirm')}
        cancelLabel={t('devices.cancel')}
        loading={revokeOne.isPending || revokeOthers.isPending}
        onConfirm={() => void confirm()}
        onCancel={() => setPending(null)}
      />
    </div>
  )
}
