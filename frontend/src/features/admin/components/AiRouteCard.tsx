import { ArrowLeftRight, Pencil } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toApiError } from '@/shared/api/errors'
import { formatDateTime } from '@/shared/lib/date'
import { useDisplayTimeZone } from '@/shared/lib/timezone'
import { cn } from '@/shared/lib/cn'
import { Button, ConfirmDialog, toast } from '@/shared/ui'
import { adminApi } from '../api/adminApi'
import { useAiGatewayMutation } from '../api/queries'
import type { AiCapability, AiProvider, AiRoute, AiRouteEndpoint } from '../types'
import { aiErrorMessage } from './aiErrors'
import { AiRouteDialog } from './AiRouteDialog'

const circuitTone = {
  CLOSED: 'border-success/50 bg-success-soft',
  HALF_OPEN: 'border-warning/50 bg-warning-soft',
  OPEN: 'border-danger/50 bg-danger-soft',
} as const

function Endpoint({ label, endpoint }: { label: string; endpoint?: AiRouteEndpoint | null }) {
  const { t, i18n } = useTranslation()
  const timeZone = useDisplayTimeZone()
  if (!endpoint) {
    return (
      <div className="flex flex-col gap-1 rounded-field border-[1.5px] border-dashed border-line px-3 py-2.5">
        <span className="text-xs font-medium uppercase tracking-wide text-muted">{label}</span>
        <span className="text-sm text-muted">{t('aiGateway.route.noFallback')}</span>
      </div>
    )
  }
  return (
    <div
      className={cn(
        'flex min-w-0 flex-col gap-1 rounded-field border-[1.5px] px-3 py-2.5',
        circuitTone[endpoint.circuit],
      )}
      data-testid="ai-route-endpoint"
    >
      <span className="text-xs font-medium uppercase tracking-wide text-muted">{label}</span>
      <span className="truncate font-medium" title={endpoint.code}>
        {endpoint.displayName}
      </span>
      <span className="text-[0.8125rem] text-ink-soft" data-testid="ai-circuit">
        {t(`aiGateway.circuit.${endpoint.circuit}`)}
        {endpoint.circuit === 'OPEN' && endpoint.retryAt
          ? ` · ${t('aiGateway.circuit.retryAt', { time: formatDateTime(endpoint.retryAt, i18n.language, timeZone) })}`
          : endpoint.recentFailures > 0
            ? ` · ${t('aiGateway.circuit.failures', { count: endpoint.recentFailures })}`
            : ''}
      </span>
    </div>
  )
}

/** Định tuyến một khả năng: nhà cung cấp chính và dự phòng kèm trạng thái mạch, các tham số, sửa và chuyển dự phòng. */
export function AiRouteCard({ route, providers }: { route: AiRoute; providers: AiProvider[] }) {
  const { t } = useTranslation()
  const [editing, setEditing] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const swap = useAiGatewayMutation((capability: AiCapability) => adminApi.switchAiFallback(capability))
  const name = t(`aiGateway.capabilities.${route.capability}`)

  const switchNow = () =>
    swap.mutate(route.capability, {
      onSuccess: (updated) => {
        setConfirming(false)
        toast.success(
          t('aiGateway.route.switched', { capability: name, provider: updated.primary.displayName }),
        )
      },
      onError: (failure) => {
        setConfirming(false)
        toast.error(aiErrorMessage(t, toApiError(failure)))
      },
    })

  return (
    <article
      className="flex flex-col gap-4 rounded-card border-[1.5px] border-ink bg-surface p-4 sm:p-5"
      aria-labelledby={`route-${route.capability}`}
      data-testid={`ai-route-${route.capability}`}
    >
      <div className="flex flex-col gap-1">
        <h3 id={`route-${route.capability}`} className="text-lg font-semibold">
          {name}
        </h3>
        <p className="text-sm text-muted">{t(`aiGateway.capabilityHints.${route.capability}`)}</p>
      </div>
      <div className="grid gap-2 sm:grid-cols-2">
        <Endpoint label={t('aiGateway.route.primary')} endpoint={route.primary} />
        <Endpoint label={t('aiGateway.route.fallback')} endpoint={route.fallback} />
      </div>
      <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
        <dt className="text-muted">{t('aiGateway.route.timeout')}</dt>
        <dd>{t('aiGateway.route.seconds', { count: route.timeoutSeconds })}</dd>
        <dt className="text-muted">{t('aiGateway.route.retries')}</dt>
        <dd>{t('aiGateway.route.retryCount', { count: route.maxRetries })}</dd>
        <dt className="text-muted">{t('aiGateway.route.breaker')}</dt>
        <dd>
          {t('aiGateway.route.breakerSummary', {
            failures: route.breakerFailureThreshold,
            window: route.breakerWindowSeconds,
            open: route.breakerOpenSeconds,
          })}
        </dd>
      </dl>
      <div className="mt-auto flex flex-wrap gap-2">
        <Button
          variant="outline"
          icon={<Pencil className="size-4" aria-hidden="true" />}
          onClick={() => setEditing(true)}
          aria-label={t('aiGateway.route.editLabel', { capability: name })}
        >
          {t('aiGateway.route.edit')}
        </Button>
        <Button
          variant="dark"
          icon={<ArrowLeftRight className="size-4" aria-hidden="true" />}
          onClick={() => setConfirming(true)}
          disabled={!route.fallback}
          aria-label={t('aiGateway.route.switchLabel', { capability: name })}
        >
          {t('aiGateway.route.switch')}
        </Button>
      </div>
      {editing && (
        <AiRouteDialog open={editing} route={route} providers={providers} onClose={() => setEditing(false)} />
      )}
      <ConfirmDialog
        open={confirming}
        title={t('aiGateway.route.switchTitle', { capability: name })}
        description={t('aiGateway.route.switchBody', {
          from: route.primary.displayName,
          to: route.fallback?.displayName ?? '',
        })}
        confirmLabel={t('aiGateway.route.switchConfirm')}
        cancelLabel={t('aiGateway.cancel')}
        loading={swap.isPending}
        onConfirm={switchNow}
        onCancel={() => setConfirming(false)}
      />
    </article>
  )
}
