import { Download, ExternalLink, FolderInput, Pencil, Star, Tags, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { documentPath } from '@/app/routes'
import { toApiError } from '@/shared/api/errors'
import { useDateFormat } from '@/shared/hooks/useDateFormat'
import { saveBlob } from '@/shared/lib/download'
import { formatBytes } from '@/shared/lib/format'
import { Badge, Button, ConfirmDialog, ErrorState, Skeleton, buttonClasses, toast } from '@/shared/ui'
import { useDocumentUsage } from '@/features/workspaces/api/queries'
import { DeleteImpactNote, UsedInWorkspaces } from '@/features/workspaces/components/UsedInWorkspaces'
import { useDocumentInfo, useFolderTree, useTags } from '../api/queries'
import { useDocumentActions } from '../hooks/useDocumentActions'
import { useOriginalFile } from '../hooks/useOriginalFile'
import { folderPath } from '../lib/tree'
import { ProcessingPanel } from './DocumentProcessing'
import { DocumentStatusBadge } from './DocumentStatusBadge'
import { MoveDialog } from './MoveDialog'
import { RenameDocumentDialog } from './RenameDocumentDialog'
import { TagAssignDialog } from './TagManager'
import { TagChip } from './TagChip'

type Open = 'rename' | 'move' | 'tags' | 'delete' | null

/**
 * Khung thông tin của một tài liệu trong khung xem bên (NCL-05-CN-001): loại, dung lượng, thư mục, nhãn, ngày thêm,
 * lần mở gần nhất, kèm nút tải tệp gốc và các thao tác đổi tên, chuyển, gắn nhãn, gắn sao, xóa.
 */
export function DocumentInfoPanel({ id, onClose }: { id: string; onClose: () => void }) {
  const { t, i18n } = useTranslation()
  const format = useDateFormat()
  const info = useDocumentInfo(id)
  const tree = useFolderTree()
  const tags = useTags()
  const actions = useDocumentActions()
  const file = useOriginalFile(id)
  const usage = useDocumentUsage({ documentIds: [id] })
  const [open, setOpen] = useState<Open>(null)
  const [downloading, setDownloading] = useState(false)
  const [moveError, setMoveError] = useState<string | null>(null)

  if (info.isPending) {
    return (
      <div className="flex flex-col gap-3" aria-busy="true">
        <Skeleton className="h-5 w-24" />
        <Skeleton className="h-40" />
      </div>
    )
  }
  if (info.isError) {
    const notFound = toApiError(info.error).status === 404
    return (
      <ErrorState
        title={notFound ? t('library.notFound.title') : t('library.loadFailed')}
        description={notFound ? t('library.notFound.body') : undefined}
        onRetry={notFound ? undefined : () => void info.refetch()}
      />
    )
  }

  const doc = info.data
  const folders = tree.data?.folders ?? []
  const path = folderPath(folders, doc.folderId)
  const documentTags = (doc.tagIds ?? []).flatMap((tagId) => tags.data?.find((tag) => tag.id === tagId) ?? [])

  const download = async () => {
    setDownloading(true)
    try {
      saveBlob(await file.open(true), doc.originalFilename)
    } catch {
      toast.error(t('library.info.downloadFailed'))
    } finally {
      setDownloading(false)
    }
  }

  return (
    <article className="flex flex-col gap-5" data-testid="document-info">
      <div className="flex flex-wrap items-center gap-2">
        <Badge tone="ink">{t(`library.type.${doc.type}`)}</Badge>
        <DocumentStatusBadge status={doc.status} always />
      </div>
      <ProcessingPanel document={doc} />
      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-3 text-[0.9375rem]">
        <dt className="text-muted">{t('library.info.type')}</dt>
        <dd>{t(`library.type.${doc.type}`)}</dd>
        <dt className="text-muted">{t('library.info.size')}</dt>
        <dd className="tabular-nums">{formatBytes(doc.sizeBytes, i18n.language)}</dd>
        <dt className="text-muted">{t('library.info.folder')}</dt>
        <dd data-testid="info-folder" className="[overflow-wrap:anywhere]">
          {path.length ? path.map((folder) => folder.name).join(' / ') : t('library.shortcuts.root')}
        </dd>
        <dt className="text-muted">{t('library.info.tags')}</dt>
        <dd className="flex flex-wrap gap-1.5" data-testid="info-tags">
          {documentTags.length ? documentTags.map((tag) => <TagChip key={tag.id} tag={tag} />) : t('library.info.noTags')}
        </dd>
        <dt className="text-muted">{t('library.info.workspaces')}</dt>
        <dd>
          <UsedInWorkspaces workspaces={usage.data} loading={usage.isPending} onNavigate={onClose} />
        </dd>
        <dt className="text-muted">{t('library.info.added')}</dt>
        <dd>
          <time dateTime={doc.createdAt}>{format.dateTime(doc.createdAt)}</time>
        </dd>
        <dt className="text-muted">{t('library.info.opened')}</dt>
        <dd>
          {doc.lastOpenedAt ? <time dateTime={doc.lastOpenedAt}>{format.dateTime(doc.lastOpenedAt)}</time> : t('library.info.never')}
        </dd>
        {doc.sourceUrl ? (
          <>
            <dt className="text-muted">{t('library.info.source')}</dt>
            <dd className="flex flex-col gap-0.5 [overflow-wrap:anywhere]" data-testid="info-source">
              <a href={doc.sourceUrl} target="_blank" rel="noopener noreferrer nofollow" className="underline underline-offset-2">
                {doc.sourceUrl}
              </a>
              {doc.fetchedAt && (
                <span className="text-muted">
                  {t('library.info.fetchedAt', { time: format.dateTime(doc.fetchedAt) })}
                </span>
              )}
            </dd>
          </>
        ) : (
          <>
            <dt className="text-muted">{t('library.info.file')}</dt>
            <dd className="[overflow-wrap:anywhere]">{doc.originalFilename}</dd>
          </>
        )}
      </dl>

      <div className="flex flex-wrap gap-2">
        <Button variant="dark" onClick={() => void download()} loading={downloading} icon={<Download className="size-4" aria-hidden="true" />}>
          {t('library.info.download')}
        </Button>
        <Link to={documentPath(doc.id)} onClick={onClose} className={buttonClasses('outline')}>
          <ExternalLink className="size-4" aria-hidden="true" />
          {t('library.info.openFull')}
        </Link>
      </div>
      <div className="flex flex-wrap gap-2">
        <Button
          variant="outline"
          aria-pressed={Boolean(doc.starred)}
          onClick={() => void actions.toggleStar(doc.id, doc.title, !doc.starred)}
          icon={<Star className={doc.starred ? 'size-4 fill-current' : 'size-4'} aria-hidden="true" />}
        >
          {doc.starred ? t('library.info.unstar') : t('library.info.star')}
        </Button>
        <Button variant="outline" onClick={() => setOpen('tags')} icon={<Tags className="size-4" aria-hidden="true" />}>
          {t('library.info.editTags')}
        </Button>
        <Button variant="outline" onClick={() => setOpen('rename')} icon={<Pencil className="size-4" aria-hidden="true" />}>
          {t('library.info.rename')}
        </Button>
        <Button variant="outline" onClick={() => setOpen('move')} icon={<FolderInput className="size-4" aria-hidden="true" />}>
          {t('library.info.move')}
        </Button>
        <Button variant="ghost" className="text-danger" onClick={() => setOpen('delete')} icon={<Trash2 className="size-4" aria-hidden="true" />}>
          {t('library.info.delete')}
        </Button>
      </div>

      <RenameDocumentDialog open={open === 'rename'} document={doc} onClose={() => setOpen(null)} />
      <MoveDialog
        open={open === 'move'}
        title={t('library.moveDialog.documentsTitle', { count: 1 })}
        folders={folders}
        currentFolderId={doc.folderId ?? null}
        loading={actions.pending}
        error={moveError}
        onClose={() => {
          setOpen(null)
          setMoveError(null)
        }}
        onMove={(folderId, name) =>
          void actions.move([doc.id], folderId, name).then((failure) => {
            if (!failure) setOpen(null)
            else setMoveError(t('library.moveDialog.conflicts', { names: `“${doc.title}”` }))
          })
        }
      />
      <TagAssignDialog
        open={open === 'tags'}
        documents={[doc]}
        loading={actions.pending}
        onClose={() => setOpen(null)}
        onApply={(add, remove) => {
          const next = [...(doc.tagIds ?? []).filter((tagId) => !remove.includes(tagId)), ...add]
          void actions.setTags(doc.id, next).then((done) => done && setOpen(null))
        }}
      />
      <ConfirmDialog
        open={open === 'delete'}
        title={t('library.deleteDocuments.title', { count: 1 })}
        description={
          <>
            {t('library.deleteDocuments.body')}
            <DeleteImpactNote workspaces={usage.data} />
          </>
        }
        confirmLabel={t('library.deleteDocuments.confirm')}
        cancelLabel={t('common.cancel')}
        loading={actions.pending || usage.isFetching}
        onCancel={() => setOpen(null)}
        onConfirm={() =>
          void actions.trash([doc.id], Boolean(usage.data?.length)).then((done) => {
            if (done) {
              setOpen(null)
              onClose()
            } else {
              void usage.refetch()
            }
          })
        }
      />
    </article>
  )
}
