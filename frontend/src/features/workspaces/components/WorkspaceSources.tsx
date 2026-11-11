import { Eye, Paperclip, Unlink } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { DocumentStatusBadge } from '@/features/library/components/DocumentStatusBadge'
import { UploadQueue } from '@/features/library/components/UploadQueue'
import { toApiError } from '@/shared/api/errors'
import { useDateFormat } from '@/shared/hooks/useDateFormat'
import { formatBytes } from '@/shared/lib/format'
import { Button, ConfirmDialog, EmptyState, toast } from '@/shared/ui'
import { useWorkspaceMutation } from '../api/queries'
import { workspacesApi } from '../api/workspacesApi'
import type { WorkspaceUploads } from '../hooks/useWorkspaceUploads'
import type { AttachedDocument, Workspace } from '../types'

/**
 * Tài liệu đang gắn với không gian học (NCL-06-CN-001): mở tài liệu ở khung xem bên, hoặc gỡ khỏi không gian học. Gỡ
 * chỉ bỏ liên kết; tài liệu vẫn nguyên trong kho và trong các không gian học khác (TC-04).
 */
export function WorkspaceSources({
  workspace,
  documents,
  uploads,
  onAttach,
  onOpenDocument,
}: {
  workspace: Workspace
  documents: AttachedDocument[]
  uploads: WorkspaceUploads
  onAttach: () => void
  onOpenDocument: (id: string) => void
}) {
  const { t, i18n } = useTranslation()
  const format = useDateFormat()
  const [detaching, setDetaching] = useState<AttachedDocument['document'] | null>(null)
  const detach = useWorkspaceMutation((documentId: string) => workspacesApi.detach(workspace.id, documentId))

  const confirmDetach = () => {
    if (!detaching) return
    const document = detaching
    detach.mutate(document.id, {
      onSuccess: () => {
        toast.success(t('workspaces.sources.detached', { title: document.title }))
        setDetaching(null)
      },
      onError: (error) => {
        const apiError = toApiError(error)
        toast.error(t(`errors.${apiError.code}`, { defaultValue: t('workspaces.actionFailed') }))
      },
    })
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm leading-relaxed text-muted">{t('workspaces.sources.hint')}</p>
        <Button
          variant="dark"
          onClick={onAttach}
          icon={<Paperclip className="size-4" aria-hidden="true" />}
          data-testid="sources-attach"
        >
          {t('workspaces.attach.action')}
        </Button>
      </div>

      <UploadQueue
        items={uploads.items}
        onCancel={uploads.cancel}
        onRetry={uploads.retry}
        onClear={uploads.clearFinished}
      />

      {documents.length === 0 ? (
        <EmptyState
          icon={<Paperclip />}
          title={t('workspaces.sources.emptyTitle')}
          description={t('workspaces.sources.emptyBody')}
          className="border-ink/50"
        />
      ) : (
        <ul
          className="flex flex-col divide-y-[1.5px] divide-line overflow-hidden rounded-card border-[1.5px] border-ink bg-surface"
          data-testid="workspace-documents"
        >
          {documents.map(({ document, attachedAt }) => (
            <li
              key={document.id}
              data-testid="workspace-document"
              data-title={document.title}
              className="flex flex-wrap items-center gap-x-2 gap-y-1 px-3 py-2"
            >
              <button
                type="button"
                onClick={() => onOpenDocument(document.id)}
                data-testid="workspace-document-open"
                className="flex min-w-0 flex-1 basis-56 flex-col gap-1 rounded-lg px-2 py-2 text-left hover:bg-ink/5"
              >
                <span className="font-medium leading-snug [overflow-wrap:anywhere]">{document.title}</span>
                <span className="flex flex-wrap items-center gap-x-3 gap-y-1 text-[0.8125rem] text-ink-soft">
                  <span className="font-medium text-ink">{t(`library.type.${document.type}`)}</span>
                  <span>{formatBytes(document.sizeBytes, i18n.language)}</span>
                  <span>{t('workspaces.sources.attachedAt', { time: format.dateTime(attachedAt) })}</span>
                  <DocumentStatusBadge status={document.status} />
                </span>
              </button>
              <div className="flex shrink-0 items-center gap-1">
                <Button
                  variant="ghost"
                  className="h-11 px-3 text-sm"
                  onClick={() => onOpenDocument(document.id)}
                  icon={<Eye className="size-4" aria-hidden="true" />}
                  aria-label={t('workspaces.sources.openNamed', { title: document.title })}
                >
                  {t('workspaces.sources.open')}
                </Button>
                <Button
                  variant="ghost"
                  className="h-11 px-3 text-sm"
                  onClick={() => setDetaching(document)}
                  icon={<Unlink className="size-4" aria-hidden="true" />}
                  aria-label={t('workspaces.sources.detachNamed', { title: document.title })}
                  data-testid="workspace-document-detach"
                >
                  {t('workspaces.sources.detach')}
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}

      <ConfirmDialog
        open={detaching !== null}
        title={t('workspaces.sources.detachTitle')}
        description={t('workspaces.sources.detachBody', { title: detaching?.title ?? '', name: workspace.name })}
        confirmLabel={t('workspaces.sources.detachConfirm')}
        cancelLabel={t('common.cancel')}
        loading={detach.isPending}
        onCancel={() => setDetaching(null)}
        onConfirm={confirmDetach}
      />
    </div>
  )
}
