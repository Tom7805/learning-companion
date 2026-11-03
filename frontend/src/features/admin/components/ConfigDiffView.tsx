import { useId } from 'react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/cn'
import { Alert, Skeleton } from '@/shared/ui'
import { useAiConfigDiff } from '../api/queries'
import type { AiConfigDiff, AiConfigVersion, AiFeature } from '../types'
import { AdminField, adminFieldClass } from './AdminDialog'

interface ConfigDiffViewProps {
  feature: AiFeature
  versions: AiConfigVersion[]
  from: number | null
  to: number | null
  onChange: (from: number | null, to: number | null) => void
}

/** Khác biệt giữa hai phiên bản: tham số đổi giá trị và lời nhắc hệ thống theo từng dòng thêm, bỏ (NCL-04-CN-002). */
export function ConfigDiffView({ feature, versions, from, to, onChange }: ConfigDiffViewProps) {
  const { t } = useTranslation()
  const id = useId()
  const diff = useAiConfigDiff(feature, from, to)
  const option = (version: AiConfigVersion) => (
    <option key={version.id} value={version.versionNumber}>
      {t('aiConfig.versionName', { version: version.versionNumber })} · {t(`aiConfig.status.${version.status}`)}
    </option>
  )

  return (
    <section
      aria-labelledby={`${id}-title`}
      className="flex flex-col gap-4 rounded-card border-[1.5px] border-ink bg-surface p-4 sm:p-5"
      data-testid="ai-config-diff"
    >
      <div className="flex flex-col gap-1">
        <h2 id={`${id}-title`} className="text-xl font-semibold">
          {t('aiConfig.diff.title')}
        </h2>
        <p className="text-sm text-muted">{t('aiConfig.diff.subtitle')}</p>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <AdminField label={t('aiConfig.diff.from')} htmlFor={`${id}-from`}>
          <select
            id={`${id}-from`}
            className={adminFieldClass}
            value={from ?? ''}
            onChange={(event) => onChange(Number(event.target.value), to)}
          >
            {versions.map(option)}
          </select>
        </AdminField>
        <AdminField label={t('aiConfig.diff.to')} htmlFor={`${id}-to`}>
          <select
            id={`${id}-to`}
            className={adminFieldClass}
            value={to ?? ''}
            onChange={(event) => onChange(from, Number(event.target.value))}
          >
            {versions.map(option)}
          </select>
        </AdminField>
      </div>
      {from === to && <p className="text-sm text-muted">{t('aiConfig.diff.same')}</p>}
      {diff.isPending && from !== to && <Skeleton className="h-32" />}
      {diff.isError && (
        <Alert tone="danger">
          <p>{t('aiConfig.diff.failed')}</p>
        </Alert>
      )}
      {diff.data && from !== to && <DiffBody diff={diff.data} />}
    </section>
  )
}

function DiffBody({ diff }: { diff: AiConfigDiff }) {
  const { t } = useTranslation()
  const show = (value: unknown) =>
    value === null || value === undefined || value === '' ? t('aiConfig.diff.empty') : String(value)
  const changed = diff.promptLines.some((line) => line.change !== 'SAME')
  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-2">
        <h3 className="text-sm font-semibold">{t('aiConfig.diff.fields')}</h3>
        {diff.fields.length === 0 ? (
          <p className="text-sm text-muted">{t('aiConfig.diff.noFieldChanges')}</p>
        ) : (
          <ul className="flex flex-col gap-1 text-sm" data-testid="ai-config-diff-fields">
            {diff.fields.map((field) => (
              <li key={field.field} className="flex flex-wrap gap-x-2">
                <span className="font-medium">{t(`aiConfig.fields.${field.field}`)}:</span>
                <span className="text-ink-soft line-through decoration-danger/70">{show(field.before)}</span>
                <span aria-hidden="true">→</span>
                <span className="sr-only">{t('aiConfig.diff.becomes')}</span>
                <span className="font-medium">{show(field.after)}</span>
              </li>
            ))}
          </ul>
        )}
      </div>
      <div className="flex flex-col gap-2">
        <h3 className="text-sm font-semibold">{t('aiConfig.fields.systemPrompt')}</h3>
        {!changed && <p className="text-sm text-muted">{t('aiConfig.diff.noPromptChanges')}</p>}
        <ol
          className="max-h-96 overflow-auto rounded-field border border-line bg-paper py-2 font-mono text-[0.8125rem] leading-relaxed"
          data-testid="ai-config-diff-lines"
        >
          {diff.promptLines.map((line, index) => (
            <li
              key={index}
              data-change={line.change}
              className={cn(
                'flex gap-2 whitespace-pre-wrap break-words px-3',
                line.change === 'ADDED' && 'bg-success-soft',
                line.change === 'REMOVED' && 'bg-danger-soft',
              )}
            >
              <span aria-hidden="true" className="w-3 shrink-0 select-none text-ink-soft">
                {line.change === 'ADDED' ? '+' : line.change === 'REMOVED' ? '−' : ' '}
              </span>
              {line.change !== 'SAME' && <span className="sr-only">{t(`aiConfig.diff.line.${line.change}`)}</span>}
              <span className={cn(line.change === 'REMOVED' && 'line-through decoration-danger/60')}>
                {line.text || ' '}
              </span>
            </li>
          ))}
        </ol>
      </div>
    </div>
  )
}
