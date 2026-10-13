import { Link2, Link2Off } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useSearchParams } from 'react-router'
import { routes } from '@/app/routes'
import { useAuth } from '@/features/auth'
import { authApi } from '@/features/auth/api/authApi'
import { useLinkedIdentities, useOAuthProviders, useUnlinkIdentity } from '@/features/auth/api/queries'
import { ProviderLogo } from '@/features/auth/components/OAuthButtons'
import type { LinkedIdentity } from '@/features/auth/types'
import { toApiError } from '@/shared/api/errors'
import { useDateFormat } from '@/shared/hooks/useDateFormat'
import { Alert, Button, ConfirmDialog, Skeleton } from '@/shared/ui'

type Dialog = { kind: 'unlink'; identity: LinkedIdentity } | { kind: 'needPassword'; identity: LinkedIdentity } | null

/**
 * Tài khoản bên ngoài đã liên kết (NCL-01-CN-007): liên kết thêm hoặc gỡ liên kết. Chỉ gỡ được khi tài khoản
 * đã có mật khẩu, nếu không người học sẽ không còn cách nào đăng nhập (TC-04).
 */
export function LinkedAccountsSection({ onNavigate }: { onNavigate: () => void }) {
  const { t } = useTranslation()
  const format = useDateFormat()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const { account } = useAuth()
  const accountId = account?.id ?? ''
  const providers = useOAuthProviders()
  const linked = useLinkedIdentities(accountId)
  const unlink = useUnlinkIdentity(accountId)
  const [dialog, setDialog] = useState<Dialog>(null)
  const [linking, setLinking] = useState<string | null>(null)
  const [notice, setNotice] = useState<{ tone: 'success' | 'danger'; text: string } | null>(() => {
    const provider = params.get('linked')
    return provider ? { tone: 'success', text: t('linked.linkedNotice', { provider: nameOf(provider) }) } : null
  })

  const startLink = async (provider: string) => {
    setLinking(provider)
    setNotice(null)
    try {
      window.location.assign(
        await authApi.oauthStart(provider, { mode: 'LINK', returnTo: window.location.pathname }),
      )
    } catch (caught) {
      setLinking(null)
      setNotice({ tone: 'danger', text: t(`errors.${toApiError(caught).code}`, { defaultValue: t('errors.INTERNAL_ERROR') }) })
    }
  }

  const askUnlink = (identity: LinkedIdentity) =>
    setDialog(account?.hasPassword === false ? { kind: 'needPassword', identity } : { kind: 'unlink', identity })

  const confirmUnlink = async () => {
    if (dialog?.kind !== 'unlink') return
    const identity = dialog.identity
    try {
      await unlink.mutateAsync(identity.id)
      setNotice({ tone: 'success', text: t('linked.unlinkedNotice', { provider: identity.providerName }) })
      setDialog(null)
    } catch (caught) {
      const code = toApiError(caught).code
      if (code === 'PASSWORD_REQUIRED_TO_UNLINK') {
        setDialog({ kind: 'needPassword', identity })
      } else {
        setDialog(null)
        setNotice({ tone: 'danger', text: t(`errors.${code}`, { defaultValue: t('errors.INTERNAL_ERROR') }) })
      }
    }
  }

  const goSetPassword = () => {
    setDialog(null)
    onNavigate()
    navigate(routes.security)
  }

  return (
    <div className="flex flex-col gap-4">
      <header className="flex flex-col gap-1">
        <h3 className="text-lg font-semibold">{t('linked.title')}</h3>
        <p className="text-sm text-muted">{t('linked.subtitle')}</p>
      </header>

      {notice && (
        <Alert tone={notice.tone} data-testid="linked-notice" key={notice.text}>
          {notice.text}
        </Alert>
      )}

      {(providers.isPending || linked.isPending) && <Skeleton className="h-24" />}
      {providers.data?.length === 0 && <Alert tone="info">{t('linked.none')}</Alert>}
      {linked.isError && (
        <Alert tone="danger">
          <p>{t('linked.loadFailed')}</p>
          <Button variant="outline" className="mt-3" onClick={() => void linked.refetch()}>
            {t('common.retry')}
          </Button>
        </Alert>
      )}

      {providers.data && linked.data && (
        <ul className="flex flex-col gap-3">
          {providers.data.map((provider) => {
            const identity = linked.data.find((item) => item.provider === provider.id)
            return (
              <li
                key={provider.id}
                data-testid={`linked-${provider.id}`}
                className="flex flex-wrap items-center gap-4 rounded-card border-[1.5px] border-ink bg-surface p-4 sm:p-5"
              >
                <span className="grid size-12 shrink-0 place-items-center rounded-2xl border-[1.5px] border-ink bg-paper">
                  <ProviderLogo id={provider.id} className="size-6" />
                </span>
                <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                  <p className="font-medium">{provider.name}</p>
                  {identity ? (
                    <>
                      <p className="truncate text-sm text-ink-soft">{t('linked.linkedAs', { email: identity.email })}</p>
                      <p className="text-[13px] text-muted">
                        {t('linked.since', { time: format.dateTime(identity.linkedAt) })}
                      </p>
                    </>
                  ) : (
                    <p className="text-sm text-muted">{t('linked.notLinked')}</p>
                  )}
                </div>
                {identity ? (
                  <Button
                    variant="outline"
                    onClick={() => askUnlink(identity)}
                    icon={<Link2Off className="size-4" aria-hidden="true" />}
                    aria-label={t('linked.unlinkProvider', { provider: provider.name })}
                  >
                    {t('linked.unlink')}
                  </Button>
                ) : (
                  <Button
                    variant="dark"
                    loading={linking === provider.id}
                    onClick={() => void startLink(provider.id)}
                    icon={<Link2 className="size-4" aria-hidden="true" />}
                  >
                    {t('linked.link', { provider: provider.name })}
                  </Button>
                )}
              </li>
            )
          })}
        </ul>
      )}

      <ConfirmDialog
        open={dialog?.kind === 'unlink'}
        title={t('linked.confirmTitle', { provider: dialog?.identity.providerName ?? '' })}
        description={t('linked.confirmBody', { provider: dialog?.identity.providerName ?? '' })}
        confirmLabel={t('linked.unlink')}
        cancelLabel={t('devices.cancel')}
        loading={unlink.isPending}
        onConfirm={() => void confirmUnlink()}
        onCancel={() => setDialog(null)}
      />
      <ConfirmDialog
        open={dialog?.kind === 'needPassword'}
        title={t('linked.needPasswordTitle')}
        description={t('linked.needPasswordBody', { provider: dialog?.identity.providerName ?? '' })}
        confirmLabel={t('linked.setPassword')}
        cancelLabel={t('devices.cancel')}
        onConfirm={goSetPassword}
        onCancel={() => setDialog(null)}
      />
    </div>
  )
}

function nameOf(provider: string) {
  return provider === 'google' ? 'Google' : provider
}
