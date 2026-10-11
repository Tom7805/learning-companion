import { ArrowRight, KeyRound, LogOut, MonitorSmartphone } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { routes } from '@/app/routes'
import { toApiError } from '@/shared/api/errors'
import { usePageHeading } from '@/shared/hooks/usePageHeading'
import { formatDateTime, formatRelative } from '@/shared/lib/date'
import { Alert, Button, ConfirmDialog, Skeleton } from '@/shared/ui'
import { useDeviceSessions, useRevokeOtherSessions } from '../api/queries'
import { ChangePasswordForm } from '../components/ChangePasswordForm'
import { useAuth } from '../hooks/useAuth'

type Notice = { tone: 'success' | 'danger'; text: string } | null

/** Bảo mật tài khoản (NCL-01-CN-003): đổi mật khẩu và đăng xuất khỏi mọi thiết bị khác. */
export function SecurityPage() {
  const { t, i18n } = useTranslation()
  const heading = usePageHeading(t('security.title'))
  const { account } = useAuth()
  const sessions = useDeviceSessions()
  const revokeOthers = useRevokeOtherSessions()
  const [confirming, setConfirming] = useState(false)
  const [notice, setNotice] = useState<Notice>(null)

  const otherCount = sessions.data?.filter((session) => !session.current).length ?? 0

  const signOutEverywhere = async () => {
    try {
      const result = await revokeOthers.mutateAsync()
      setNotice({ tone: 'success', text: t('devices.revokedOthers', { count: result.revokedCount }) })
    } catch (error) {
      const code = toApiError(error).code
      setNotice({ tone: 'danger', text: t(`errors.${code}`, { defaultValue: t('errors.INTERNAL_ERROR') }) })
    } finally {
      setConfirming(false)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <header className="flex max-w-xl flex-col gap-2">
        <h1 ref={heading} tabIndex={-1} className="text-3xl tracking-tight outline-none">
          {t('security.title')}
        </h1>
        <p className="leading-relaxed text-muted">{t('security.subtitle')}</p>
      </header>

      <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1.45fr)_minmax(0,1fr)]">
        <section
          aria-labelledby="change-password-title"
          className="flex flex-col gap-5 rounded-card border-[1.5px] border-ink bg-surface p-5 sm:p-6"
        >
          <div className="flex items-start gap-4">
            <span
              aria-hidden="true"
              className="grid size-12 shrink-0 place-items-center rounded-2xl border-[1.5px] border-ink bg-lilac"
            >
              <KeyRound className="size-5" />
            </span>
            <div className="flex min-w-0 flex-col gap-1">
              <h2 id="change-password-title" className="text-xl font-medium">
                {t('security.change.title')}
              </h2>
              <p className="text-sm leading-relaxed text-muted">{t('security.change.body')}</p>
              <p className="text-[13px] text-ink-soft" data-testid="password-changed-at">
                {account?.passwordChangedAt ? (
                  <time
                    dateTime={account.passwordChangedAt}
                    title={formatDateTime(account.passwordChangedAt, i18n.language)}
                  >
                    {t('security.lastChanged', { time: formatRelative(account.passwordChangedAt, i18n.language) })}
                  </time>
                ) : (
                  t('security.neverChanged')
                )}
              </p>
            </div>
          </div>
          {account && <ChangePasswordForm email={account.email} />}
        </section>

        <section
          aria-labelledby="sign-out-everywhere-title"
          className="flex flex-col gap-4 rounded-card border-[1.5px] border-ink bg-sun-soft p-5 sm:p-6"
        >
          <span
            aria-hidden="true"
            className="grid size-12 place-items-center rounded-2xl border-[1.5px] border-ink bg-sun"
          >
            <MonitorSmartphone className="size-5" />
          </span>
          <div className="flex flex-col gap-1.5">
            <h2 id="sign-out-everywhere-title" className="text-xl font-medium">
              {t('security.sessions.title')}
            </h2>
            <p className="text-sm leading-relaxed text-ink-soft">{t('security.sessions.body')}</p>
          </div>

          {sessions.isPending ? (
            <Skeleton className="h-6 w-48" />
          ) : sessions.isError ? (
            <Alert tone="danger">
              <p>{t('devices.loadFailed')}</p>
              <Button variant="outline" className="mt-3" onClick={() => void sessions.refetch()}>
                {t('common.retry')}
              </Button>
            </Alert>
          ) : (
            <p className="font-semibold tabular-nums" data-testid="other-sessions-count" aria-live="polite">
              {t('security.sessions.count', { count: otherCount })}
            </p>
          )}

          {notice && (
            <Alert tone={notice.tone} data-testid="security-notice" key={notice.text}>
              {notice.text}
            </Alert>
          )}

          <Button
            variant="accent"
            size="lg"
            disabled={!sessions.data || otherCount === 0}
            onClick={() => setConfirming(true)}
            icon={<LogOut className="size-4" aria-hidden="true" />}
            data-testid="sign-out-everywhere"
          >
            {t('security.sessions.cta')}
          </Button>
          <Link
            to={routes.devices}
            className="inline-flex items-center gap-1.5 self-start text-sm font-medium underline-offset-4 hover:underline"
          >
            {t('security.sessions.manage')}
            <ArrowRight className="size-4" aria-hidden="true" />
          </Link>
        </section>
      </div>

      <ConfirmDialog
        open={confirming}
        title={t('devices.confirmOthersTitle')}
        description={t('devices.confirmOthersBody', { count: otherCount })}
        confirmLabel={t('devices.confirm')}
        cancelLabel={t('devices.cancel')}
        loading={revokeOthers.isPending}
        onConfirm={() => void signOutEverywhere()}
        onCancel={() => setConfirming(false)}
      />
    </div>
  )
}
