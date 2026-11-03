import { Save } from 'lucide-react'
import { useId, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { toApiError, type ApiError } from '@/shared/api/errors'
import { Alert, Button, toast } from '@/shared/ui'
import { adminApi } from '../api/adminApi'
import { useAiConfigMutation } from '../api/queries'
import { splitByFindings } from '../lib/promptTrial'
import type { AiConfigVersion, AiConfigVersionInput, AiFeature, AiProvider, PiiFinding } from '../types'
import { AdminField, adminFieldClass } from './AdminDialog'
import { aiErrorMessage } from './aiErrors'

export const MAX_PROMPT_LENGTH = 20000

interface PromptEditorProps {
  feature: AiFeature
  /** Phiên bản mở ra để sửa; lưu xong thành phiên bản nháp mới dựa trên phiên bản này. */
  base: AiConfigVersion
  providers: AiProvider[]
  onSaved: (version: AiConfigVersion) => void
}

/**
 * Soạn một phiên bản cấu hình mới (NCL-04-CN-002): nhà cung cấp, mô hình, nhiệt độ, độ dài tối đa và lời nhắc hệ
 * thống. Lời nhắc có địa chỉ thư hay số điện thoại thì máy chủ chặn lưu; các đoạn đó được tô đánh dấu ngay dưới ô soạn.
 */
export function PromptEditor({ feature, base, providers, onSaved }: PromptEditorProps) {
  const { t } = useTranslation()
  const id = useId()
  const [form, setForm] = useState<AiConfigVersionInput>(() => ({
    providerCode: base.providerCode ?? null,
    model: base.model ?? null,
    temperature: base.temperature,
    maxOutputTokens: base.maxOutputTokens,
    systemPrompt: base.systemPrompt,
    basedOnVersion: base.versionNumber,
  }))
  const [error, setError] = useState<ApiError | null>(null)
  const [pii, setPii] = useState<{ prompt: string; findings: PiiFinding[] } | null>(null)
  const save = useAiConfigMutation((input: AiConfigVersionInput) => adminApi.saveAiConfig(feature, input))
  const set = (patch: Partial<AiConfigVersionInput>) => setForm((current) => ({ ...current, ...patch }))
  const invalid = (field: string) => error?.fieldErrors.some((item) => item.field === field) || undefined
  const textProviders = providers.filter((provider) => provider.capabilities.includes('TEXT_GENERATION'))
  const unchanged =
    (form.providerCode ?? null) === (base.providerCode ?? null) &&
    (form.model ?? null) === (base.model ?? null) &&
    Number(form.temperature) === Number(base.temperature) &&
    Number(form.maxOutputTokens) === Number(base.maxOutputTokens) &&
    form.systemPrompt.trim() === base.systemPrompt.trim()

  const submit = (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    setPii(null)
    const input = { ...form, model: form.providerCode ? form.model?.trim() || null : null }
    save.mutate(input, {
      onSuccess: (saved) => {
        toast.success(t('aiConfig.editor.saved', { version: saved.versionNumber }))
        onSaved(saved)
      },
      onError: (failure) => {
        const apiError = toApiError(failure)
        setError(apiError)
        const findings = apiError.details?.findings
        if (apiError.code === 'AI_CONFIG_PROMPT_HAS_PII' && Array.isArray(findings)) {
          setPii({ prompt: input.systemPrompt, findings: findings as PiiFinding[] })
        }
      },
    })
  }

  return (
    <section
      aria-labelledby={`${id}-title`}
      className="flex flex-col gap-4 rounded-card border-[1.5px] border-ink bg-surface p-4 sm:p-5"
      data-testid="ai-config-editor"
    >
      <div className="flex flex-col gap-1">
        <h2 id={`${id}-title`} className="text-xl font-semibold">
          {t('aiConfig.editor.title')}
        </h2>
        <p className="text-sm text-muted">
          {t('aiConfig.editor.subtitle', { version: base.versionNumber })}
        </p>
      </div>
      <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
        <div className="grid gap-4 sm:grid-cols-2">
          <AdminField
            label={t('aiConfig.fields.providerCode')}
            htmlFor={`${id}-provider`}
            hint={t('aiConfig.editor.providerHint')}
          >
            <select
              id={`${id}-provider`}
              className={adminFieldClass}
              value={form.providerCode ?? ''}
              aria-invalid={invalid('providerCode')}
              onChange={(event) => set({ providerCode: event.target.value || null })}
            >
              <option value="">{t('aiConfig.editor.followRoute')}</option>
              {textProviders.map((provider) => (
                <option key={provider.id} value={provider.code}>
                  {provider.displayName} ({provider.code})
                  {provider.enabled ? '' : ` · ${t('aiConfig.editor.providerOff')}`}
                </option>
              ))}
            </select>
          </AdminField>
          <AdminField
            label={t('aiConfig.fields.model')}
            htmlFor={`${id}-model`}
            hint={t(form.providerCode ? 'aiConfig.editor.modelHint' : 'aiConfig.editor.modelNeedsProvider')}
          >
            <input
              id={`${id}-model`}
              className={adminFieldClass}
              value={form.providerCode ? (form.model ?? '') : ''}
              disabled={!form.providerCode}
              maxLength={120}
              placeholder={t('aiConfig.editor.modelPlaceholder')}
              aria-invalid={invalid('model')}
              onChange={(event) => set({ model: event.target.value })}
            />
          </AdminField>
          <AdminField
            label={t('aiConfig.fields.temperature')}
            htmlFor={`${id}-temperature`}
            hint={t('aiConfig.editor.temperatureHint')}
          >
            <input
              id={`${id}-temperature`}
              type="number"
              inputMode="decimal"
              min={0}
              max={2}
              step={0.05}
              className={adminFieldClass}
              value={form.temperature}
              aria-invalid={invalid('temperature')}
              onChange={(event) => set({ temperature: Number(event.target.value) })}
            />
          </AdminField>
          <AdminField
            label={t('aiConfig.fields.maxOutputTokens')}
            htmlFor={`${id}-tokens`}
            hint={t('aiConfig.editor.tokensHint')}
          >
            <input
              id={`${id}-tokens`}
              type="number"
              inputMode="numeric"
              min={1}
              max={32000}
              className={adminFieldClass}
              value={form.maxOutputTokens}
              aria-invalid={invalid('maxOutputTokens')}
              onChange={(event) => set({ maxOutputTokens: Number(event.target.value) })}
            />
          </AdminField>
        </div>
        <AdminField
          label={t('aiConfig.fields.systemPrompt')}
          htmlFor={`${id}-prompt`}
          hint={t('aiConfig.editor.promptHint', {
            count: form.systemPrompt.length,
            max: MAX_PROMPT_LENGTH.toLocaleString(),
          })}
        >
          <textarea
            id={`${id}-prompt`}
            value={form.systemPrompt}
            maxLength={MAX_PROMPT_LENGTH}
            rows={9}
            spellCheck={false}
            aria-invalid={invalid('systemPrompt')}
            aria-describedby={pii ? `${id}-pii` : undefined}
            onChange={(event) => {
              set({ systemPrompt: event.target.value })
              if (pii) setPii(null)
            }}
            className="w-full resize-y rounded-field border-[1.5px] border-ink bg-surface px-3 py-2.5 text-[0.9375rem] leading-relaxed outline-none focus:border-focus focus:ring-4 focus:ring-focus/15 aria-invalid:border-danger"
          />
        </AdminField>
        {pii && (
          <div id={`${id}-pii`} className="flex flex-col gap-2" data-testid="ai-config-pii">
            <Alert tone="danger">
              <p>{t('aiConfig.errors.AI_CONFIG_PROMPT_HAS_PII')}</p>
              <p className="mt-1 text-sm">
                {t('aiConfig.editor.piiSummary', {
                  kinds: [...new Set(pii.findings.map((finding) => t(`aiConfig.pii.${finding.kind}`)))].join(', '),
                  count: pii.findings.length,
                })}
              </p>
            </Alert>
            <p className="text-sm font-medium text-ink-soft">{t('aiConfig.editor.piiPreview')}</p>
            <div className="max-h-56 overflow-auto whitespace-pre-wrap break-words rounded-field border border-line bg-paper px-3 py-2.5 text-[0.9375rem] leading-relaxed">
              {splitByFindings(pii.prompt, pii.findings).map((part, index) =>
                part.kind ? (
                  <mark
                    key={index}
                    className="rounded bg-danger-soft px-0.5 font-medium text-ink underline decoration-danger decoration-2 underline-offset-2"
                    data-testid="ai-config-pii-mark"
                  >
                    <span className="sr-only">{t(`aiConfig.pii.${part.kind}`)}: </span>
                    {part.text}
                  </mark>
                ) : (
                  <span key={index}>{part.text}</span>
                ),
              )}
            </div>
          </div>
        )}
        {error && !pii && (
          <Alert tone="danger">
            <p>{aiErrorMessage(t, error, 'aiConfig.errors')}</p>
          </Alert>
        )}
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-sm text-muted">{t('aiConfig.editor.versionHint')}</p>
          <Button
            className="shrink-0 self-start sm:self-auto"
            type="submit"
            variant="accent"
            loading={save.isPending}
            disabled={unchanged || !form.systemPrompt.trim()}
            icon={<Save className="size-4" aria-hidden="true" />}
          >
            {t('aiConfig.editor.save')}
          </Button>
        </div>
      </form>
    </section>
  )
}
