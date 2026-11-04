import { History, Undo2 } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toApiError } from '@/shared/api/errors'
import { usePageHeading } from '@/shared/hooks/usePageHeading'
import { cn } from '@/shared/lib/cn'
import { formatDateTime } from '@/shared/lib/date'
import { useDisplayTimeZone } from '@/shared/lib/timezone'
import { Alert, Button, ConfirmDialog, Skeleton, toast } from '@/shared/ui'
import { adminApi } from '../api/adminApi'
import { useAiConfigMutation, useAiConfigs, useAiConfigVersions, useAiGateway } from '../api/queries'
import { AiConfigHistory, AiConfigStatusBadge } from '../components/AiConfigHistory'
import { aiErrorMessage } from '../components/aiErrors'
import { ConfigDiffView } from '../components/ConfigDiffView'
import { PromptEditor } from '../components/PromptEditor'
import { PromptTrialPanel } from '../components/PromptTrialPanel'
import type { AiConfigFeatureSummary, AiConfigVersion, AiFeature, AiTrialSampleSet } from '../types'

/**
 * Cấu hình mô hình và lời nhắc hệ thống theo phiên bản (NCL-04-CN-002): mỗi chức năng trí tuệ nhân tạo có phiên bản
 * đang phát hành, trình soạn phiên bản mới, thử nhanh trên bộ nguồn mẫu, xem khác biệt, lịch sử và quay lại phiên bản
 * trước chỉ bằng một thao tác.
 */
export function AiConfigPage() {
  const { t } = useTranslation()
  const heading = usePageHeading(t('aiConfig.title'))
  const overview = useAiConfigs()
  const [feature, setFeature] = useState<AiFeature | null>(null)
  const features = overview.data?.features ?? []
  const current = features.find((item) => item.feature === feature) ?? features[0]

  return (
    <div className="flex flex-col gap-8">
      <header className="flex max-w-3xl flex-col gap-2">
        <h1 ref={heading} tabIndex={-1} className="text-3xl tracking-tight outline-none">
          {t('aiConfig.title')}
        </h1>
        <p className="leading-relaxed text-muted">{t('aiConfig.subtitle')}</p>
      </header>

      {overview.isPending && (
        <div className="grid gap-4 lg:grid-cols-2" data-testid="ai-config-loading">
          <Skeleton className="h-40" />
          <Skeleton className="h-40" />
        </div>
      )}
      {overview.isError && (
        <Alert tone="danger">
          <p>{t('aiConfig.loadFailed')}</p>
          <Button variant="outline" className="mt-3" onClick={() => void overview.refetch()}>
            {t('common.retry')}
          </Button>
        </Alert>
      )}

      {current && (
        <>
          <nav aria-label={t('aiConfig.featuresLabel')} className="grid gap-3 sm:grid-cols-2">
            {features.map((item) => (
              <FeatureButton
                key={item.feature}
                summary={item}
                active={item.feature === current.feature}
                onSelect={() => setFeature(item.feature)}
              />
            ))}
          </nav>
          <FeatureWorkspace
            key={current.feature}
            summary={current}
            sampleSets={overview.data?.sampleSets ?? []}
          />
        </>
      )}
    </div>
  )
}

function FeatureButton({
  summary,
  active,
  onSelect,
}: {
  summary: AiConfigFeatureSummary
  active: boolean
  onSelect: () => void
}) {
  const { t } = useTranslation()
  return (
    <button
      type="button"
      onClick={onSelect}
      aria-current={active ? 'true' : undefined}
      data-testid={`ai-config-feature-${summary.feature}`}
      className={cn(
        'flex flex-col items-start gap-1 rounded-card border-[1.5px] px-4 py-3 text-left outline-none transition-colors',
        'focus-visible:ring-4 focus-visible:ring-focus/25',
        active ? 'border-ink bg-sun' : 'border-line bg-surface hover:border-ink',
      )}
    >
      <span className="font-semibold">{t(`aiConfig.features.${summary.feature}.name`)}</span>
      <span className="text-sm text-ink-soft">
        {summary.published
          ? t('aiConfig.featureLive', { version: summary.published.versionNumber })
          : t('aiConfig.featureNone')}
        {summary.draftCount > 0 && ` · ${t('aiConfig.drafts', { count: summary.draftCount })}`}
      </span>
    </button>
  )
}

/** Mọi thao tác trên cấu hình của một chức năng. */
function FeatureWorkspace({
  summary,
  sampleSets,
}: {
  summary: AiConfigFeatureSummary
  sampleSets: AiTrialSampleSet[]
}) {
  const { t, i18n } = useTranslation()
  const timeZone = useDisplayTimeZone()
  const feature = summary.feature
  const history = useAiConfigVersions(feature)
  const gateway = useAiGateway()
  const versions = history.data ?? []
  const published = versions.find((version) => version.status === 'PUBLISHED') ?? summary.published ?? null
  const [baseNumber, setBaseNumber] = useState<number | null>(null)
  const [trialNumber, setTrialNumber] = useState<number | null>(null)
  const [compare, setCompare] = useState<{ from: number | null; to: number | null }>({ from: null, to: null })
  const [publishing, setPublishing] = useState<AiConfigVersion | null>(null)
  const [rollingBack, setRollingBack] = useState(false)
  const publish = useAiConfigMutation((versionNumber: number) => adminApi.publishAiConfig(feature, versionNumber))
  const rollback = useAiConfigMutation(() => adminApi.rollbackAiConfig(feature))
  const name = t(`aiConfig.features.${feature}.name`)
  // Chỉ chức năng sinh văn bản mới thử được bằng câu hỏi mẫu.
  const textGeneration = summary.capability === 'TEXT_GENERATION'

  // Chưa chọn gì thì soạn từ phiên bản mới nhất, thử phiên bản mới nhất và so bản đang chạy với bản mới nhất.
  const latest = versions[0]
  const base = versions.find((version) => version.versionNumber === baseNumber) ?? latest
  const trialVersion = trialNumber ?? latest?.versionNumber ?? null
  const compareFrom = compare.from ?? published?.versionNumber ?? latest?.versionNumber ?? null
  const compareTo = compare.to ?? latest?.versionNumber ?? null
  const previous = versions
    .filter((version) => version.status === 'SUPERSEDED' && version.publishedAt)
    .sort((a, b) => (b.publishedAt ?? '').localeCompare(a.publishedAt ?? ''))[0]

  const confirmPublish = () => {
    if (!publishing) return
    publish.mutate(publishing.versionNumber, {
      onSuccess: (live) => {
        setPublishing(null)
        toast.success(t('aiConfig.publish.done', { feature: name, version: live.versionNumber }))
      },
      onError: (failure) => {
        setPublishing(null)
        toast.error(aiErrorMessage(t, toApiError(failure), 'aiConfig.errors'))
      },
    })
  }

  const confirmRollback = () =>
    rollback.mutate(undefined, {
      onSuccess: (live) => {
        setRollingBack(false)
        toast.success(t('aiConfig.rollback.done', { feature: name, version: live.versionNumber }))
      },
      onError: (failure) => {
        setRollingBack(false)
        toast.error(aiErrorMessage(t, toApiError(failure), 'aiConfig.errors'))
      },
    })

  return (
    <div className="flex flex-col gap-8">
      <section
        aria-labelledby="ai-config-live-title"
        className="flex flex-col gap-4 rounded-card border-[1.5px] border-ink bg-surface p-4 sm:p-5"
        data-testid="ai-config-live"
      >
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="flex min-w-0 flex-col gap-1">
            <h2 id="ai-config-live-title" className="text-xl font-semibold">
              {name}
            </h2>
            <p className="text-sm text-muted">{t(`aiConfig.features.${feature}.hint`)}</p>
          </div>
          {published && (
            <Button
              variant="outline"
              icon={<Undo2 className="size-4" aria-hidden="true" />}
              disabled={!previous}
              title={previous ? undefined : t('aiConfig.rollback.unavailable')}
              onClick={() => setRollingBack(true)}
            >
              {previous
                ? t('aiConfig.rollback.action', { version: previous.versionNumber })
                : t('aiConfig.rollback.actionNone')}
            </Button>
          )}
        </div>
        {published ? (
          <div className="flex flex-col gap-3">
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-semibold" data-testid="ai-config-live-version">
                {t('aiConfig.versionName', { version: published.versionNumber })}
              </span>
              <AiConfigStatusBadge status="PUBLISHED" />
            </div>
            <dl className="grid gap-x-6 gap-y-1 text-sm sm:grid-cols-[max-content_1fr]">
              <dt className="text-muted">{t('aiConfig.fields.providerCode')}</dt>
              <dd>{published.providerCode ?? t('aiConfig.editor.followRoute')}</dd>
              <dt className="text-muted">{t('aiConfig.fields.model')}</dt>
              <dd>{published.model ?? t('aiConfig.defaultModel')}</dd>
              <dt className="text-muted">{t('aiConfig.fields.temperature')}</dt>
              <dd>{published.temperature}</dd>
              <dt className="text-muted">{t('aiConfig.fields.maxOutputTokens')}</dt>
              <dd>{published.maxOutputTokens}</dd>
              <dt className="text-muted">{t('aiConfig.live.publishedAt')}</dt>
              <dd data-testid="ai-config-live-published">
                {published.publishedAt
                  ? t('aiConfig.live.publishedBy', {
                      who: published.publishedBy
                        ? (published.publishedBy.displayName ?? t('aiConfig.history.unknownActor'))
                        : t('aiConfig.history.system'),
                      time: formatDateTime(published.publishedAt, i18n.language, timeZone),
                    })
                  : '—'}
              </dd>
            </dl>
            <details className="rounded-field border border-line bg-paper px-4 py-3 text-sm">
              <summary className="cursor-pointer font-medium outline-none focus-visible:ring-4 focus-visible:ring-focus/25">
                {t('aiConfig.live.showPrompt')}
              </summary>
              <p className="mt-2 whitespace-pre-wrap break-words leading-relaxed text-ink-soft">
                {published.systemPrompt}
              </p>
            </details>
          </div>
        ) : (
          <p className="text-sm text-muted">{t('aiConfig.featureNone')}</p>
        )}
      </section>

      {history.isPending && <Skeleton className="h-72" />}
      {history.isError && (
        <Alert tone="danger">
          <p>{t('aiConfig.loadFailed')}</p>
          <Button variant="outline" className="mt-3" onClick={() => void history.refetch()}>
            {t('common.retry')}
          </Button>
        </Alert>
      )}

      {base && (
        <div className="flex flex-col gap-6">
          <PromptEditor
            key={base.id}
            feature={feature}
            base={base}
            providers={gateway.data?.providers ?? []}
            onSaved={(saved) => {
              setBaseNumber(saved.versionNumber)
              setTrialNumber(saved.versionNumber)
              setCompare({ from: published?.versionNumber ?? null, to: saved.versionNumber })
            }}
          />
          {textGeneration ? (
            <PromptTrialPanel
              feature={feature}
              versions={versions}
              sampleSets={sampleSets}
              versionNumber={trialVersion}
              onVersionChange={setTrialNumber}
              onPublish={setPublishing}
            />
          ) : (
            <Alert tone="info" data-testid="ai-config-no-trial">
              {t('aiConfig.noTrial')}
            </Alert>
          )}
        </div>
      )}

      {versions.length > 1 && (
        <ConfigDiffView
          feature={feature}
          versions={versions}
          from={compareFrom}
          to={compareTo}
          onChange={(from, to) => setCompare({ from, to })}
        />
      )}

      {versions.length > 0 && (
        <AiConfigHistory
          versions={versions}
          trialRequired={textGeneration}
          onEdit={(version) => {
            setBaseNumber(version.versionNumber)
            document.querySelector('[data-testid="ai-config-editor"]')?.scrollIntoView({ block: 'start' })
          }}
          onTrial={(version) => {
            setTrialNumber(version.versionNumber)
            document.querySelector('[data-testid="ai-config-trial"]')?.scrollIntoView({ block: 'start' })
          }}
          onCompare={(version) => {
            setCompare({ from: published?.versionNumber ?? null, to: version.versionNumber })
            document.querySelector('[data-testid="ai-config-diff"]')?.scrollIntoView({ block: 'start' })
          }}
          onPublish={setPublishing}
        />
      )}

      <ConfirmDialog
        open={publishing != null}
        title={t('aiConfig.publish.confirmTitle', { version: publishing?.versionNumber ?? '' })}
        description={t('aiConfig.publish.confirmBody', {
          feature: name,
          version: publishing?.versionNumber ?? '',
          current: published?.versionNumber ?? '—',
        })}
        confirmLabel={t('aiConfig.publish.confirm')}
        cancelLabel={t('aiGateway.cancel')}
        loading={publish.isPending}
        onConfirm={confirmPublish}
        onCancel={() => setPublishing(null)}
      />
      <ConfirmDialog
        open={rollingBack}
        title={t('aiConfig.rollback.confirmTitle')}
        description={
          <span className="flex items-start gap-2">
            <History className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
            {t('aiConfig.rollback.confirmBody', {
              current: published?.versionNumber ?? '—',
              previous: previous?.versionNumber ?? '—',
            })}
          </span>
        }
        confirmLabel={t('aiConfig.rollback.confirm')}
        cancelLabel={t('aiGateway.cancel')}
        loading={rollback.isPending}
        onConfirm={confirmRollback}
        onCancel={() => setRollingBack(false)}
      />
    </div>
  )
}
