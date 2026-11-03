import { useId, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { toApiError, type ApiError } from '@/shared/api/errors'
import { Alert, Button, toast } from '@/shared/ui'
import { adminApi } from '../api/adminApi'
import { useAiGatewayMutation } from '../api/queries'
import type { AiProvider, AiRoute, AiRouteInput } from '../types'
import { AdminDialog, AdminField, adminFieldClass } from './AdminDialog'
import { aiErrorMessage } from './aiErrors'

interface AiRouteDialogProps {
  open: boolean
  route: AiRoute
  providers: AiProvider[]
  onClose: () => void
}

/** Đổi nhà cung cấp chính, dự phòng, thời gian chờ, số lần thử lại và ngưỡng ngắt mạch của một khả năng. */
export function AiRouteDialog({ open, route, providers, onClose }: AiRouteDialogProps) {
  const { t } = useTranslation()
  const id = useId()
  const [form, setForm] = useState<AiRouteInput>({
    primaryProviderId: route.primary.id,
    fallbackProviderId: route.fallback?.id ?? null,
    timeoutSeconds: route.timeoutSeconds,
    maxRetries: route.maxRetries,
    breakerFailureThreshold: route.breakerFailureThreshold,
    breakerWindowSeconds: route.breakerWindowSeconds,
    breakerOpenSeconds: route.breakerOpenSeconds,
  })
  const [error, setError] = useState<ApiError | null>(null)
  const save = useAiGatewayMutation((input: AiRouteInput) => adminApi.updateAiRoute(route.capability, input))
  const capabilityName = t(`aiGateway.capabilities.${route.capability}`)
  // Chỉ nhà cung cấp đang bật, đã xác nhận không huấn luyện và hỗ trợ khả năng này mới chọn được.
  const eligible = providers.filter(
    (provider) =>
      provider.enabled && provider.noTrainingConfirmed && provider.capabilities.includes(route.capability),
  )
  const set = (patch: Partial<AiRouteInput>) => setForm((current) => ({ ...current, ...patch }))
  const numberField = (
    key: keyof Pick<
      AiRouteInput,
      | 'timeoutSeconds'
      | 'maxRetries'
      | 'breakerFailureThreshold'
      | 'breakerWindowSeconds'
      | 'breakerOpenSeconds'
    >,
    min: number,
    max: number,
  ) => (
    <AdminField label={t(`aiGateway.route.fields.${key}`)} htmlFor={`${id}-${key}`}>
      <input
        id={`${id}-${key}`}
        type="number"
        min={min}
        max={max}
        className={adminFieldClass}
        value={form[key]}
        onChange={(event) => set({ [key]: Number(event.target.value) })}
        aria-invalid={error?.fieldErrors.some((item) => item.field === key) || undefined}
      />
    </AdminField>
  )

  const submit = (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    save.mutate(form, {
      onSuccess: () => {
        onClose()
        toast.success(t('aiGateway.route.saved', { capability: capabilityName }))
      },
      onError: (failure) => setError(toApiError(failure)),
    })
  }

  return (
    <AdminDialog
      open={open}
      onClose={onClose}
      title={t('aiGateway.route.editTitle', { capability: capabilityName })}
      description={t('aiGateway.route.dialogHint')}
      testId="ai-route-dialog"
    >
      <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
        <div className="grid gap-4 sm:grid-cols-2">
          <AdminField label={t('aiGateway.route.primary')} htmlFor={`${id}-primary`}>
            <select
              id={`${id}-primary`}
              className={adminFieldClass}
              value={form.primaryProviderId}
              onChange={(event) => set({ primaryProviderId: event.target.value })}
            >
              {eligible.map((provider) => (
                <option key={provider.id} value={provider.id}>
                  {provider.displayName} ({provider.code})
                </option>
              ))}
            </select>
          </AdminField>
          <AdminField label={t('aiGateway.route.fallback')} htmlFor={`${id}-fallback`}>
            <select
              id={`${id}-fallback`}
              className={adminFieldClass}
              value={form.fallbackProviderId ?? ''}
              onChange={(event) => set({ fallbackProviderId: event.target.value || null })}
            >
              <option value="">{t('aiGateway.route.noFallback')}</option>
              {eligible
                .filter((provider) => provider.id !== form.primaryProviderId)
                .map((provider) => (
                  <option key={provider.id} value={provider.id}>
                    {provider.displayName} ({provider.code})
                  </option>
                ))}
            </select>
          </AdminField>
          {numberField('timeoutSeconds', 1, 120)}
          {numberField('maxRetries', 0, 3)}
        </div>
        <fieldset className="grid gap-4 sm:grid-cols-3">
          <legend className="pb-2 text-sm font-semibold">{t('aiGateway.route.breaker')}</legend>
          {numberField('breakerFailureThreshold', 1, 50)}
          {numberField('breakerWindowSeconds', 10, 3600)}
          {numberField('breakerOpenSeconds', 5, 3600)}
        </fieldset>
        {error && (
          <Alert tone="danger">
            <p>{aiErrorMessage(t, error)}</p>
          </Alert>
        )}
        <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
          <Button variant="outline" onClick={onClose}>
            {t('aiGateway.cancel')}
          </Button>
          <Button type="submit" variant="accent" loading={save.isPending}>
            {t('aiGateway.route.save')}
          </Button>
        </div>
      </form>
    </AdminDialog>
  )
}
