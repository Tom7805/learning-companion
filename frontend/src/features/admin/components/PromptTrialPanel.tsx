import { FlaskConical, Rocket } from 'lucide-react'
import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'
import { queryKeys } from '@/shared/api/queryKeys'
import { Alert, Button } from '@/shared/ui'
import { streamPromptTrial, type PromptTrialDone } from '../lib/promptTrial'
import type { AiConfigVersion, AiFeature, AiTrialSampleSet } from '../types'
import { AdminField, adminFieldClass } from './AdminDialog'
import { aiErrorMessage } from './aiErrors'

interface PromptTrialPanelProps {
  feature: AiFeature
  versions: AiConfigVersion[]
  sampleSets: AiTrialSampleSet[]
  versionNumber: number | null
  onVersionChange: (versionNumber: number) => void
  onPublish: (version: AiConfigVersion) => void
}

/**
 * Thử nhanh một phiên bản trước khi phát hành (NCL-04-CN-002): một câu hỏi mẫu trên bộ nguồn mẫu, gửi qua lớp kết nối
 * với đúng cấu hình của phiên bản đó. Chữ hiện dần theo luồng; thử xong thì nút phát hành hiện ngay bên dưới.
 */
export function PromptTrialPanel({
  feature,
  versions,
  sampleSets,
  versionNumber,
  onVersionChange,
  onPublish,
}: PromptTrialPanelProps) {
  const { t } = useTranslation()
  const id = useId()
  const client = useQueryClient()
  const [sampleSetId, setSampleSetId] = useState(sampleSets[0]?.id ?? '')
  const sampleSet = sampleSets.find((set) => set.id === sampleSetId) ?? sampleSets[0]
  const [question, setQuestion] = useState(sampleSet?.suggestedQuestion ?? '')
  const [output, setOutput] = useState('')
  const [done, setDone] = useState<PromptTrialDone | null>(null)
  const [errorCode, setErrorCode] = useState<string | null>(null)
  const [running, setRunning] = useState(false)
  const abort = useRef<AbortController | null>(null)
  // Phiên bản đã thu hồi không phát hành lại được nên cũng không cần thử.
  const choices = versions.filter((version) => version.status !== 'REVOKED')
  const selected = choices.find((version) => version.versionNumber === versionNumber) ?? null
  const triedVersion = done ? versions.find((version) => version.versionNumber === done.version) : undefined

  useEffect(() => () => abort.current?.abort(), [])

  const run = async (event: FormEvent) => {
    event.preventDefault()
    if (!selected || !sampleSet || !question.trim() || running) return
    abort.current?.abort()
    abort.current = new AbortController()
    setOutput('')
    setDone(null)
    setErrorCode(null)
    setRunning(true)
    try {
      await streamPromptTrial(
        feature,
        selected.versionNumber,
        { question: question.trim(), sampleSetId: sampleSet.id },
        {
          onChunk: (text) => setOutput((current) => current + text),
          onDone: setDone,
          onError: setErrorCode,
        },
        abort.current.signal,
      )
    } catch (failure) {
      if (!(failure instanceof DOMException && failure.name === 'AbortError')) setErrorCode('NETWORK_ERROR')
    } finally {
      setRunning(false)
      void client.invalidateQueries({ queryKey: queryKeys.aiConfigVersions(feature) })
    }
  }

  return (
    <section
      aria-labelledby={`${id}-title`}
      className="flex flex-col gap-4 rounded-card border-[1.5px] border-ink bg-surface p-4 sm:p-5"
      data-testid="ai-config-trial"
    >
      <div className="flex flex-col gap-1">
        <h2 id={`${id}-title`} className="text-xl font-semibold">
          {t('aiConfig.trial.title')}
        </h2>
        <p className="text-sm text-muted">{t('aiConfig.trial.subtitle')}</p>
      </div>
      <form onSubmit={run} className="flex flex-col gap-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <AdminField label={t('aiConfig.trial.version')} htmlFor={`${id}-version`}>
            <select
              id={`${id}-version`}
              className={adminFieldClass}
              value={selected?.versionNumber ?? ''}
              onChange={(event) => {
                onVersionChange(Number(event.target.value))
                setDone(null)
                setOutput('')
                setErrorCode(null)
              }}
            >
              {choices.map((version) => (
                <option key={version.id} value={version.versionNumber}>
                  {t('aiConfig.versionName', { version: version.versionNumber })} ·{' '}
                  {t(`aiConfig.status.${version.status}`)}
                </option>
              ))}
            </select>
          </AdminField>
          <AdminField label={t('aiConfig.trial.sampleSet')} htmlFor={`${id}-samples`}>
            <select
              id={`${id}-samples`}
              className={adminFieldClass}
              value={sampleSet?.id ?? ''}
              onChange={(event) => {
                const next = sampleSets.find((set) => set.id === event.target.value)
                setSampleSetId(event.target.value)
                if (next) setQuestion(next.suggestedQuestion)
              }}
            >
              {sampleSets.map((set) => (
                <option key={set.id} value={set.id}>
                  {set.title}
                </option>
              ))}
            </select>
          </AdminField>
        </div>
        {sampleSet && (
          <details className="rounded-field border border-line bg-paper px-4 py-3 text-sm">
            <summary className="cursor-pointer font-medium outline-none focus-visible:ring-4 focus-visible:ring-focus/25">
              {t('aiConfig.trial.sources', { count: sampleSet.sources.length })}
            </summary>
            <ol className="mt-2 flex list-decimal flex-col gap-1.5 pl-5 text-ink-soft">
              {sampleSet.sources.map((source) => (
                <li key={source.title}>
                  <span className="font-medium text-ink">{source.title}:</span> {source.excerpt}
                </li>
              ))}
            </ol>
          </details>
        )}
        <AdminField label={t('aiConfig.trial.question')} htmlFor={`${id}-question`}>
          <textarea
            id={`${id}-question`}
            value={question}
            onChange={(event) => setQuestion(event.target.value)}
            maxLength={1000}
            rows={2}
            className="w-full rounded-field border-[1.5px] border-ink bg-surface px-3 py-2.5 text-[0.9375rem] outline-none focus:border-focus focus:ring-4 focus:ring-focus/15"
          />
        </AdminField>
        <div>
          <Button
            type="submit"
            variant="dark"
            loading={running}
            disabled={!selected || !question.trim()}
            icon={<FlaskConical className="size-4" aria-hidden="true" />}
          >
            {selected
              ? t('aiConfig.trial.run', { version: selected.versionNumber })
              : t('aiConfig.trial.runNone')}
          </Button>
        </div>
      </form>
      <div
        aria-live="polite"
        aria-busy={running || undefined}
        className="min-h-16 whitespace-pre-wrap rounded-field border border-line bg-paper px-4 py-3 text-[0.9375rem] leading-relaxed"
        data-testid="ai-config-trial-output"
      >
        {output || <span className="text-muted">{t(running ? 'aiConfig.trial.waiting' : 'aiConfig.trial.empty')}</span>}
      </div>
      {done && (
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-sm text-ink-soft" data-testid="ai-config-trial-done">
            {t('aiConfig.trial.done', {
              version: done.version,
              provider: done.providerCode,
              input: done.inputUnits,
              output: done.outputUnits,
            })}
          </p>
          {triedVersion && (triedVersion.status === 'DRAFT' || triedVersion.status === 'SUPERSEDED') && (
            <Button
              variant="accent"
              className="shrink-0 self-start sm:self-auto"
              icon={<Rocket className="size-4" aria-hidden="true" />}
              onClick={() => onPublish(triedVersion)}
            >
              {t('aiConfig.publish.action', { version: triedVersion.versionNumber })}
            </Button>
          )}
        </div>
      )}
      {errorCode && (
        <Alert tone="danger" data-testid="ai-config-trial-error">
          <p>{aiErrorMessage(t, { code: errorCode, message: '', status: 0, fieldErrors: [] }, 'aiConfig.errors')}</p>
        </Alert>
      )}
    </section>
  )
}
