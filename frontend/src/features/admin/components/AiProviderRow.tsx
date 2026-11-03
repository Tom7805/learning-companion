import { KeyRound, Pencil, ShieldAlert, ShieldCheck } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toApiError, type ApiError } from '@/shared/api/errors'
import { Alert, Badge, Button, Switch, toast } from '@/shared/ui'
import { adminApi } from '../api/adminApi'
import { useAiGatewayMutation } from '../api/queries'
import type { AiProvider } from '../types'
import { aiErrorMessage } from './aiErrors'
import { AiProviderDialog } from './AiProviderDialog'

/** Một nhà cung cấp: kiểu, khả năng, kho bí mật, cam kết không huấn luyện, công tắc bật tắt và nút sửa. */
export function AiProviderRow({ provider }: { provider: AiProvider }) {
  const { t } = useTranslation()
  const [editing, setEditing] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)
  const toggle = useAiGatewayMutation((enabled: boolean) =>
    adminApi.setAiProviderEnabled(provider.id, enabled),
  )
  const roles = [
    ...provider.primaryFor.map((capability) =>
      t('aiGateway.provider.primaryFor', { capability: t(`aiGateway.capabilities.${capability}`) }),
    ),
    ...provider.fallbackFor.map((capability) =>
      t('aiGateway.provider.fallbackFor', { capability: t(`aiGateway.capabilities.${capability}`) }),
    ),
  ]

  const change = (enabled: boolean) => {
    setError(null)
    toggle.mutate(enabled, {
      onSuccess: (saved) =>
        toast.success(
          t(saved.enabled ? 'aiGateway.provider.enabled' : 'aiGateway.provider.disabled', {
            name: saved.displayName,
          }),
        ),
      onError: (failure) => setError(toApiError(failure)),
    })
  }

  return (
    <li className="flex flex-col gap-3 py-4" data-testid={`ai-provider-${provider.code}`}>
      <div className="flex flex-wrap items-start gap-x-4 gap-y-3">
        <div className="flex min-w-0 flex-1 basis-64 flex-col gap-1.5">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="font-semibold">{provider.displayName}</h3>
            <Badge tone="outline">{t(`aiGateway.adapters.${provider.adapter}`)}</Badge>
            <code className="text-xs text-muted">{provider.code}</code>
          </div>
          <p className="text-sm text-ink-soft">
            {provider.capabilities.map((capability) => t(`aiGateway.capabilities.${capability}`)).join(' · ')}
          </p>
          <div className="flex flex-wrap gap-x-4 gap-y-1 text-[0.8125rem]">
            <span className="flex items-center gap-1.5">
              {provider.noTrainingConfirmed ? (
                <ShieldCheck className="size-4 text-success" aria-hidden="true" />
              ) : (
                <ShieldAlert className="size-4 text-danger" aria-hidden="true" />
              )}
              {t(
                provider.noTrainingConfirmed
                  ? 'aiGateway.provider.noTrainingYes'
                  : 'aiGateway.provider.noTrainingNo',
              )}
            </span>
            {provider.secretRequired && (
              <span className="flex items-center gap-1.5">
                <KeyRound
                  className={provider.secretConfigured ? 'size-4 text-success' : 'size-4 text-danger'}
                  aria-hidden="true"
                />
                {t(
                  provider.secretConfigured
                    ? 'aiGateway.provider.secretReady'
                    : 'aiGateway.provider.secretMissing',
                  {
                    name: provider.secretName ?? '—',
                  },
                )}
              </span>
            )}
            <span className="text-muted">
              {t('aiGateway.provider.price', {
                input: provider.inputPricePerMillion,
                output: provider.outputPricePerMillion,
              })}
            </span>
          </div>
          {roles.length > 0 && <p className="text-[0.8125rem] text-muted">{roles.join(' · ')}</p>}
        </div>
        <div className="flex items-center gap-3">
          <Button
            variant="ghost"
            icon={<Pencil className="size-4" aria-hidden="true" />}
            onClick={() => setEditing(true)}
            aria-label={t('aiGateway.provider.editLabel', { name: provider.displayName })}
          >
            {t('aiGateway.provider.edit')}
          </Button>
          <div className="flex items-center gap-2">
            <span className="text-sm text-ink-soft" aria-hidden="true">
              {t(provider.enabled ? 'aiGateway.provider.on' : 'aiGateway.provider.off')}
            </span>
            <Switch
              checked={provider.enabled}
              busy={toggle.isPending}
              onChange={change}
              aria-label={t('aiGateway.provider.toggleLabel', { name: provider.displayName })}
              data-testid="ai-provider-toggle"
            />
          </div>
        </div>
      </div>
      {error && (
        <Alert tone="danger" data-testid="ai-provider-error">
          <p>{aiErrorMessage(t, error)}</p>
        </Alert>
      )}
      {editing && <AiProviderDialog open={editing} provider={provider} onClose={() => setEditing(false)} />}
    </li>
  )
}
