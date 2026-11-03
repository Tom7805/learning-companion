import { ShieldCheck } from 'lucide-react'
import { useId, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { toApiError, type ApiError } from '@/shared/api/errors'
import { Alert, Button, Checkbox, toast } from '@/shared/ui'
import { adminApi } from '../api/adminApi'
import { useAiGatewayMutation } from '../api/queries'
import type { AiAdapter, AiCapability, AiProvider, AiProviderInput } from '../types'
import { AdminDialog, AdminField, adminFieldClass } from './AdminDialog'
import { aiErrorMessage } from './aiErrors'

const AI_CAPABILITIES: AiCapability[] = ['TEXT_GENERATION', 'EMBEDDING', 'VISION', 'OCR']
const ADAPTERS: AiAdapter[] = ['OPENAI_COMPATIBLE', 'ANTHROPIC', 'MOCK']
const ADAPTER_CAPABILITIES: Record<AiAdapter, AiCapability[]> = {
  MOCK: AI_CAPABILITIES,
  OPENAI_COMPATIBLE: AI_CAPABILITIES,
  ANTHROPIC: ['TEXT_GENERATION', 'VISION', 'OCR'],
}

function initial(provider?: AiProvider): AiProviderInput {
  return {
    code: provider?.code ?? '',
    displayName: provider?.displayName ?? '',
    adapter: provider?.adapter ?? 'OPENAI_COMPATIBLE',
    baseUrl: provider?.baseUrl ?? '',
    textModel: provider?.textModel ?? '',
    embeddingModel: provider?.embeddingModel ?? '',
    visionModel: provider?.visionModel ?? '',
    capabilities: provider?.capabilities ?? ['TEXT_GENERATION'],
    secretName: provider?.secretName ?? '',
    inputPricePerMillion: provider?.inputPricePerMillion ?? 0,
    outputPricePerMillion: provider?.outputPricePerMillion ?? 0,
    noTrainingConfirmed: provider?.noTrainingConfirmed ?? false,
  }
}

interface AiProviderDialogProps {
  open: boolean
  /** Có thì sửa nhà cung cấp này, không có thì thêm mới. */
  provider?: AiProvider
  onClose: () => void
}

/**
 * Thêm hoặc sửa nhà cung cấp (NCL-04-CN-001). Khóa bí mật không bao giờ nhập ở đây: chỉ tên khóa đã đặt trong kho bí
 * mật của máy chủ. Ô xác nhận cam kết không huấn luyện là điều kiện để bật nhà cung cấp (QTN-11).
 */
export function AiProviderDialog({ open, provider, onClose }: AiProviderDialogProps) {
  const { t } = useTranslation()
  const id = useId()
  const [form, setForm] = useState<AiProviderInput>(() => initial(provider))
  const [error, setError] = useState<ApiError | null>(null)
  const editing = provider != null
  const save = useAiGatewayMutation((input: AiProviderInput) =>
    editing ? adminApi.updateAiProvider(provider.id, input) : adminApi.createAiProvider(input),
  )
  const set = (patch: Partial<AiProviderInput>) => setForm((current) => ({ ...current, ...patch }))
  const supported = ADAPTER_CAPABILITIES[form.adapter]
  const needsModels = form.adapter !== 'MOCK'
  const fieldError = (field: string) => error?.fieldErrors.some((item) => item.field === field) || undefined

  const submit = (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    const clean = (value?: string) => (value && value.trim() ? value.trim() : undefined)
    save.mutate(
      {
        ...form,
        code: editing ? undefined : clean(form.code),
        baseUrl: clean(form.baseUrl),
        textModel: clean(form.textModel),
        embeddingModel: clean(form.embeddingModel),
        visionModel: clean(form.visionModel),
        secretName: clean(form.secretName),
        capabilities: form.capabilities.filter((capability) => supported.includes(capability)),
      },
      {
        onSuccess: (saved) => {
          onClose()
          toast.success(
            t(editing ? 'aiGateway.provider.updated' : 'aiGateway.provider.created', {
              name: saved.displayName,
            }),
          )
        },
        onError: (failure) => setError(toApiError(failure)),
      },
    )
  }

  const toggleCapability = (capability: AiCapability, checked: boolean) =>
    set({
      capabilities: checked
        ? [...form.capabilities, capability]
        : form.capabilities.filter((item) => item !== capability),
    })

  return (
    <AdminDialog
      open={open}
      onClose={onClose}
      title={t(editing ? 'aiGateway.provider.editTitle' : 'aiGateway.provider.addTitle')}
      description={t('aiGateway.provider.dialogHint')}
      testId="ai-provider-dialog"
    >
      <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
        <div className="grid gap-4 sm:grid-cols-2">
          <AdminField label={t('aiGateway.provider.displayName')} htmlFor={`${id}-name`}>
            <input
              id={`${id}-name`}
              className={adminFieldClass}
              value={form.displayName}
              onChange={(event) => set({ displayName: event.target.value })}
              required
              maxLength={80}
              aria-invalid={fieldError('displayName')}
            />
          </AdminField>
          <AdminField
            label={t('aiGateway.provider.code')}
            htmlFor={`${id}-code`}
            hint={editing ? undefined : t('aiGateway.provider.codeHint')}
          >
            <input
              id={`${id}-code`}
              className={adminFieldClass}
              value={form.code}
              onChange={(event) => set({ code: event.target.value.toLowerCase() })}
              disabled={editing}
              required
              maxLength={40}
              spellCheck={false}
              autoComplete="off"
              aria-invalid={fieldError('code')}
            />
          </AdminField>
          <AdminField label={t('aiGateway.provider.adapter')} htmlFor={`${id}-adapter`}>
            <select
              id={`${id}-adapter`}
              className={adminFieldClass}
              value={form.adapter}
              disabled={editing}
              onChange={(event) => set({ adapter: event.target.value as AiAdapter })}
            >
              {ADAPTERS.map((adapter) => (
                <option key={adapter} value={adapter}>
                  {t(`aiGateway.adapters.${adapter}`)}
                </option>
              ))}
            </select>
          </AdminField>
          {needsModels && (
            <AdminField
              label={t('aiGateway.provider.baseUrl')}
              htmlFor={`${id}-url`}
              hint={form.adapter === 'ANTHROPIC' ? t('aiGateway.provider.baseUrlOptional') : undefined}
            >
              <input
                id={`${id}-url`}
                className={adminFieldClass}
                value={form.baseUrl}
                onChange={(event) => set({ baseUrl: event.target.value })}
                placeholder="https://"
                inputMode="url"
                spellCheck={false}
                aria-invalid={fieldError('baseUrl')}
              />
            </AdminField>
          )}
        </div>

        <fieldset className="flex flex-col gap-2">
          <legend className="pb-1.5 text-sm font-medium text-ink-soft">
            {t('aiGateway.provider.capabilities')}
          </legend>
          <div className="grid gap-2 sm:grid-cols-2">
            {supported.map((capability) => (
              <Checkbox
                key={capability}
                label={t(`aiGateway.capabilities.${capability}`)}
                checked={form.capabilities.includes(capability)}
                onChange={(event) => toggleCapability(capability, event.target.checked)}
              />
            ))}
          </div>
          {fieldError('capabilities') && (
            <p className="text-sm text-danger">{t('aiGateway.provider.capabilitiesRequired')}</p>
          )}
        </fieldset>

        {needsModels && (
          <div className="grid gap-4 sm:grid-cols-3">
            <AdminField label={t('aiGateway.provider.textModel')} htmlFor={`${id}-text-model`}>
              <input
                id={`${id}-text-model`}
                className={adminFieldClass}
                value={form.textModel}
                onChange={(event) => set({ textModel: event.target.value })}
                spellCheck={false}
              />
            </AdminField>
            {supported.includes('EMBEDDING') && (
              <AdminField label={t('aiGateway.provider.embeddingModel')} htmlFor={`${id}-embed-model`}>
                <input
                  id={`${id}-embed-model`}
                  className={adminFieldClass}
                  value={form.embeddingModel}
                  onChange={(event) => set({ embeddingModel: event.target.value })}
                  spellCheck={false}
                />
              </AdminField>
            )}
            <AdminField label={t('aiGateway.provider.visionModel')} htmlFor={`${id}-vision-model`}>
              <input
                id={`${id}-vision-model`}
                className={adminFieldClass}
                value={form.visionModel}
                onChange={(event) => set({ visionModel: event.target.value })}
                spellCheck={false}
              />
            </AdminField>
          </div>
        )}

        <div className="grid gap-4 sm:grid-cols-3">
          {needsModels && (
            <AdminField
              label={t('aiGateway.provider.secretName')}
              htmlFor={`${id}-secret`}
              hint={t('aiGateway.provider.secretHint')}
            >
              <input
                id={`${id}-secret`}
                className={adminFieldClass}
                value={form.secretName}
                onChange={(event) => set({ secretName: event.target.value.toUpperCase() })}
                placeholder="LC_AI_..."
                spellCheck={false}
                autoComplete="off"
                aria-invalid={fieldError('secretName')}
              />
            </AdminField>
          )}
          <AdminField label={t('aiGateway.provider.inputPrice')} htmlFor={`${id}-in-price`}>
            <input
              id={`${id}-in-price`}
              type="number"
              min={0}
              step="0.0001"
              className={adminFieldClass}
              value={form.inputPricePerMillion}
              onChange={(event) => set({ inputPricePerMillion: Number(event.target.value) })}
            />
          </AdminField>
          <AdminField label={t('aiGateway.provider.outputPrice')} htmlFor={`${id}-out-price`}>
            <input
              id={`${id}-out-price`}
              type="number"
              min={0}
              step="0.0001"
              className={adminFieldClass}
              value={form.outputPricePerMillion}
              onChange={(event) => set({ outputPricePerMillion: Number(event.target.value) })}
            />
          </AdminField>
        </div>

        <div className="rounded-field border-[1.5px] border-ink bg-lilac/40 p-4">
          <p className="mb-2 flex items-center gap-2 text-sm font-semibold">
            <ShieldCheck className="size-4" aria-hidden="true" />
            {t('aiGateway.provider.noTrainingTitle')}
          </p>
          <Checkbox
            label={t('aiGateway.provider.noTrainingLabel')}
            checked={form.noTrainingConfirmed}
            onChange={(event) => set({ noTrainingConfirmed: event.target.checked })}
            data-testid="ai-provider-no-training"
          />
        </div>

        {error && (
          <Alert tone="danger">
            <p>
              {error.code === 'VALIDATION_FAILED'
                ? t('aiGateway.errors.VALIDATION_FAILED')
                : aiErrorMessage(t, error)}
            </p>
          </Alert>
        )}

        <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
          <Button variant="outline" onClick={onClose}>
            {t('aiGateway.cancel')}
          </Button>
          <Button type="submit" variant="accent" loading={save.isPending}>
            {t(editing ? 'aiGateway.provider.save' : 'aiGateway.provider.add')}
          </Button>
        </div>
      </form>
    </AdminDialog>
  )
}
