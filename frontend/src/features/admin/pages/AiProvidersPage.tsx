import { BellRing, Plus } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toApiError } from '@/shared/api/errors'
import { usePageHeading } from '@/shared/hooks/usePageHeading'
import { formatDateTime } from '@/shared/lib/date'
import { useDisplayTimeZone } from '@/shared/lib/timezone'
import { Alert, Button, Skeleton, toast } from '@/shared/ui'
import { adminApi } from '../api/adminApi'
import { useAiGateway, useAiGatewayMutation } from '../api/queries'
import { AiProviderDialog } from '../components/AiProviderDialog'
import { AiProviderRow } from '../components/AiProviderRow'
import { AiRouteCard } from '../components/AiRouteCard'
import { AiTestCallPanel } from '../components/AiTestCallPanel'
import { AiUsagePanel } from '../components/AiUsagePanel'
import { aiErrorMessage } from '../components/aiErrors'

/**
 * Lớp kết nối dịch vụ trí tuệ nhân tạo (NCL-04-CN-001): cảnh báo ngắt mạch, định tuyến từng khả năng với nhà cung cấp
 * chính và dự phòng, danh sách nhà cung cấp, mức sử dụng và lời gọi thử.
 */
export function AiProvidersPage() {
  const { t, i18n } = useTranslation()
  const heading = usePageHeading(t('aiGateway.title'))
  const timeZone = useDisplayTimeZone()
  const gateway = useAiGateway()
  const [adding, setAdding] = useState(false)
  const acknowledge = useAiGatewayMutation((alertId: string) => adminApi.acknowledgeAiAlert(alertId))
  const data = gateway.data

  return (
    <div className="flex flex-col gap-8">
      <header className="flex max-w-3xl flex-col gap-2">
        <h1 ref={heading} tabIndex={-1} className="text-3xl tracking-tight outline-none">
          {t('aiGateway.title')}
        </h1>
        <p className="leading-relaxed text-muted">{t('aiGateway.subtitle')}</p>
      </header>

      {gateway.isPending && (
        <div className="grid gap-4 lg:grid-cols-2" data-testid="ai-gateway-loading">
          <Skeleton className="h-56" />
          <Skeleton className="h-56" />
        </div>
      )}
      {gateway.isError && (
        <Alert tone="danger">
          <p>{t('aiGateway.loadFailed')}</p>
          <Button variant="outline" className="mt-3" onClick={() => void gateway.refetch()}>
            {t('common.retry')}
          </Button>
        </Alert>
      )}

      {data && data.alerts.length > 0 && (
        <section aria-labelledby="ai-alerts-title" className="flex flex-col gap-3" data-testid="ai-alerts">
          <h2 id="ai-alerts-title" className="flex items-center gap-2 text-xl font-semibold">
            <BellRing className="size-5 text-danger" aria-hidden="true" />
            {t('aiGateway.alerts.title')}
          </h2>
          {data.alerts.map((alert) => (
            <Alert key={alert.id} tone="danger" data-testid="ai-alert">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <p>
                  {t(
                    alert.fallbackProviderCode ? 'aiGateway.alerts.body' : 'aiGateway.alerts.bodyNoFallback',
                    {
                      provider: alert.providerCode,
                      capability: t(`aiGateway.capabilities.${alert.capability}`),
                      fallback: alert.fallbackProviderCode,
                      time: formatDateTime(alert.openedAt, i18n.language, timeZone),
                    },
                  )}
                </p>
                <Button
                  variant="outline"
                  loading={acknowledge.isPending && acknowledge.variables === alert.id}
                  onClick={() =>
                    acknowledge.mutate(alert.id, {
                      onError: (failure) => toast.error(aiErrorMessage(t, toApiError(failure))),
                    })
                  }
                >
                  {t('aiGateway.alerts.acknowledge')}
                </Button>
              </div>
            </Alert>
          ))}
        </section>
      )}

      {data && (
        <section aria-labelledby="ai-routes-title" className="flex flex-col gap-4">
          <div className="flex flex-col gap-1">
            <h2 id="ai-routes-title" className="text-xl font-semibold">
              {t('aiGateway.routes.title')}
            </h2>
            <p className="text-sm text-muted">{t('aiGateway.routes.subtitle')}</p>
          </div>
          <div className="grid gap-4 lg:grid-cols-2">
            {data.routes.map((route) => (
              <AiRouteCard key={route.capability} route={route} providers={data.providers} />
            ))}
          </div>
        </section>
      )}

      {data && (
        <section aria-labelledby="ai-providers-title" className="flex flex-col gap-4">
          <div className="flex flex-wrap items-end justify-between gap-3">
            <div className="flex max-w-2xl flex-col gap-1">
              <h2 id="ai-providers-title" className="text-xl font-semibold">
                {t('aiGateway.providers.title')}
              </h2>
              <p className="text-sm text-muted">{t('aiGateway.providers.subtitle')}</p>
            </div>
            <Button
              variant="accent"
              icon={<Plus className="size-4" aria-hidden="true" />}
              onClick={() => setAdding(true)}
            >
              {t('aiGateway.provider.add')}
            </Button>
          </div>
          <ul
            className="divide-y divide-line rounded-card border-[1.5px] border-ink bg-surface px-4 sm:px-5"
            data-testid="ai-providers"
          >
            {data.providers.map((provider) => (
              <AiProviderRow key={provider.id} provider={provider} />
            ))}
          </ul>
          {adding && <AiProviderDialog open={adding} onClose={() => setAdding(false)} />}
        </section>
      )}

      {data && <AiTestCallPanel />}
      <AiUsagePanel />
    </div>
  )
}
