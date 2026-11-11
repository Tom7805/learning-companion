import { ArrowRight, Library, Paperclip, UploadCloud } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useDateFormat } from '@/shared/hooks/useDateFormat'
import { Button } from '@/shared/ui'
import type { AttachedDocument, Workspace } from '../types'
import type { AttachTab } from './AttachDocumentsDialog'

const RECENT = 3

/**
 * Phần tổng quan của không gian học (NCL-06-CN-001): chưa có tài liệu thì hướng dẫn gắn tài liệu đầu tiên; đã có thì
 * tóm tắt số tài liệu, số đã sẵn sàng và các tài liệu vừa gắn.
 */
export function WorkspaceOverview({
  workspace,
  documents,
  onAttach,
  onOpenDocument,
  onShowSources,
}: {
  workspace: Workspace
  documents: AttachedDocument[]
  onAttach: (tab: AttachTab) => void
  onOpenDocument: (id: string) => void
  onShowSources: () => void
}) {
  const { t, i18n } = useTranslation()
  const format = useDateFormat()

  if (documents.length === 0) {
    return (
      <section
        aria-labelledby="workspace-guide-title"
        data-testid="workspace-guide"
        className="flex flex-col gap-5 rounded-card border-[1.5px] border-ink bg-surface p-5 sm:p-7"
      >
        <div className="flex items-start gap-4">
          <span aria-hidden="true" className="grid size-14 shrink-0 place-items-center rounded-2xl border-[1.5px] border-ink bg-sun">
            <Paperclip className="size-6" />
          </span>
          <div className="flex min-w-0 flex-col gap-1.5">
            <h2 id="workspace-guide-title" className="text-xl font-medium">
              {t('workspaces.guide.title')}
            </h2>
            <p className="max-w-2xl leading-relaxed text-ink-soft">{t('workspaces.guide.body', { name: workspace.name })}</p>
          </div>
        </div>
        <ol className="grid gap-3 sm:grid-cols-3">
          {(['pick', 'share', 'study'] as const).map((step, index) => (
            <li key={step} className="flex gap-3 rounded-xl border-[1.5px] border-line bg-paper p-4">
              <span
                aria-hidden="true"
                className="grid size-7 shrink-0 place-items-center rounded-full border-[1.5px] border-ink bg-surface text-sm font-semibold tabular-nums"
              >
                {index + 1}
              </span>
              <span className="flex flex-col gap-1">
                <span className="font-medium">{t(`workspaces.guide.steps.${step}.title`)}</span>
                <span className="text-sm leading-relaxed text-ink-soft">{t(`workspaces.guide.steps.${step}.body`)}</span>
              </span>
            </li>
          ))}
        </ol>
        <div className="flex flex-col gap-3 sm:flex-row">
          <Button
            variant="accent"
            onClick={() => onAttach('library')}
            icon={<Library className="size-4" aria-hidden="true" />}
            data-testid="guide-attach-library"
          >
            {t('workspaces.guide.fromLibrary')}
          </Button>
          <Button
            variant="outline"
            onClick={() => onAttach('upload')}
            icon={<UploadCloud className="size-4" aria-hidden="true" />}
            data-testid="guide-attach-upload"
          >
            {t('workspaces.guide.upload')}
          </Button>
        </div>
      </section>
    )
  }

  const ready = documents.filter((attached) => (attached.document.status ?? 'READY') === 'READY').length
  const processing = documents.filter(
    (attached) => attached.document.status === 'PENDING' || attached.document.status === 'PROCESSING',
  ).length
  const failed = documents.filter((attached) => attached.document.status === 'FAILED').length
  const stats = [
    { key: 'total', value: documents.length },
    { key: 'ready', value: ready },
    ...(processing ? [{ key: 'processing', value: processing }] : []),
    ...(failed ? [{ key: 'failed', value: failed }] : []),
  ]

  return (
    <div className="flex flex-col gap-5" data-testid="workspace-summary">
      <dl className="grid grid-cols-2 gap-3 sm:flex sm:flex-wrap">
        {stats.map((stat) => (
          <div
            key={stat.key}
            className="flex min-w-36 flex-col gap-0.5 rounded-card border-[1.5px] border-ink bg-surface px-4 py-3"
            data-testid={`workspace-stat-${stat.key}`}
          >
            <dt className="text-[0.8125rem] text-muted">{t(`workspaces.overview.stats.${stat.key}`)}</dt>
            <dd className="text-2xl font-semibold tabular-nums">{stat.value.toLocaleString(i18n.language)}</dd>
          </div>
        ))}
      </dl>

      <section
        aria-labelledby="workspace-recent-title"
        className="flex flex-col gap-3 rounded-card border-[1.5px] border-ink bg-surface p-5"
      >
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 id="workspace-recent-title" className="text-lg font-medium">
            {t('workspaces.overview.recent')}
          </h2>
          <Button
            variant="ghost"
            className="h-9 px-3 text-sm"
            onClick={onShowSources}
            icon={<ArrowRight className="size-4" aria-hidden="true" />}
          >
            {t('workspaces.overview.allSources')}
          </Button>
        </div>
        <ul className="flex flex-col divide-y divide-line">
          {documents.slice(0, RECENT).map(({ document, attachedAt }) => (
            <li key={document.id} className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 py-2.5">
              <button
                type="button"
                onClick={() => onOpenDocument(document.id)}
                className="min-w-0 text-left font-medium underline-offset-4 [overflow-wrap:anywhere] hover:underline"
              >
                {document.title}
              </button>
              <span className="text-[0.8125rem] text-muted">
                {t('workspaces.sources.attachedAt', { time: format.dateTime(attachedAt) })}
              </span>
            </li>
          ))}
        </ul>
        <p className="text-sm leading-relaxed text-ink-soft">{t('workspaces.overview.next')}</p>
      </section>
    </div>
  )
}
