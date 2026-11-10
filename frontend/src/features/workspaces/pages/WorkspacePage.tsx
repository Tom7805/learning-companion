import { ArrowLeft, Lock, Paperclip, Pencil, SearchX, Trash2 } from 'lucide-react'
import { useId, useMemo, useState, type RefObject } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { routes } from '@/app/routes'
import { useViewerPanel } from '@/features/library/hooks/useViewerPanel'
import { toApiError } from '@/shared/api/errors'
import { usePageHeading } from '@/shared/hooks/usePageHeading'
import { Alert, Badge, Button, ConfirmDialog, Skeleton, buttonClasses, toast } from '@/shared/ui'
import { useWorkspace, useWorkspaceDocuments, useWorkspaceMutation } from '../api/queries'
import { workspacesApi } from '../api/workspacesApi'
import { AttachDocumentsDialog, type AttachTab } from '../components/AttachDocumentsDialog'
import { CreateWorkspaceDialog } from '../components/CreateWorkspaceDialog'
import { WorkspaceGlyph } from '../components/WorkspaceGlyph'
import { WorkspaceOverview } from '../components/WorkspaceOverview'
import { WorkspaceSources } from '../components/WorkspaceSources'
import { WorkspaceTabs } from '../components/WorkspaceTabs'
import { useWorkspaceUploads } from '../hooks/useWorkspaceUploads'
import { isWorkspaceTab, workspacePanelId, workspaceTabId, type WorkspaceTab } from '../lib/tabs'
import type { Workspace } from '../types'

/**
 * Trang một không gian học (NCL-06-CN-001). Không gian học không tồn tại, đã vào thùng rác hay của người khác đều hiện
 * cùng một trang "không tìm thấy" (TC-05).
 */
export function WorkspacePage() {
  const { t } = useTranslation()
  const { id = '' } = useParams()
  const workspace = useWorkspace(id)
  const notFound = workspace.isError && toApiError(workspace.error).status === 404
  const heading = usePageHeading(
    workspace.data?.name ?? (notFound ? t('workspaces.notFound.title') : t('workspaces.title')),
    workspace.data?.id ?? (notFound ? 'not-found' : undefined),
  )

  if (workspace.isPending) {
    return (
      <div className="flex flex-col gap-4" aria-busy="true">
        <Skeleton className="h-6 w-40" />
        <Skeleton className="h-20" />
        <Skeleton className="h-64" />
      </div>
    )
  }

  if (notFound) {
    return (
      <div className="mx-auto flex max-w-md flex-col items-center gap-4 py-10 text-center" data-testid="workspace-not-found">
        <span aria-hidden="true" className="grid size-16 place-items-center rounded-2xl border-[1.5px] border-ink bg-sun">
          <SearchX className="size-7" />
        </span>
        <h1 ref={heading} tabIndex={-1} className="text-2xl tracking-tight outline-none sm:text-3xl">
          {t('workspaces.notFound.title')}
        </h1>
        <p className="leading-relaxed text-muted">{t('workspaces.notFound.body')}</p>
        <Link to={routes.workspaces} className={buttonClasses('dark', 'lg')}>
          {t('workspaces.backToList')}
        </Link>
      </div>
    )
  }

  if (workspace.isError) {
    return (
      <Alert tone="danger">
        <p>{t('workspaces.loadFailed')}</p>
        <Button variant="outline" className="mt-3" onClick={() => void workspace.refetch()}>
          {t('common.retry')}
        </Button>
      </Alert>
    )
  }

  return <WorkspaceView key={workspace.data.id} workspace={workspace.data} heading={heading} />
}

type DialogState = { kind: 'attach'; tab: AttachTab } | { kind: 'edit' } | { kind: 'delete' } | null

function WorkspaceView({
  workspace,
  heading,
}: {
  workspace: Workspace
  heading: RefObject<HTMLHeadingElement | null>
}) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const baseId = useId()
  const [params, setParams] = useSearchParams()
  const documents = useWorkspaceDocuments(workspace.id)
  const uploads = useWorkspaceUploads(workspace.id)
  const [dialog, setDialog] = useState<DialogState>(null)
  const trash = useWorkspaceMutation(() => workspacesApi.trash(workspace.id))
  const list = useMemo(() => documents.data ?? [], [documents.data])
  const attachedIds = useMemo(() => new Set(list.map((attached) => attached.document.id)), [list])
  const { openViewer } = useViewerPanel((id) => list.find((attached) => attached.document.id === id)?.document.title)
  const tabParam = params.get('tab')
  const tab: WorkspaceTab = isWorkspaceTab(tabParam) ? tabParam : 'overview'

  const setTab = (next: WorkspaceTab) =>
    setParams(
      (current) => {
        const updated = new URLSearchParams(current)
        if (next === 'overview') updated.delete('tab')
        else updated.set('tab', next)
        return updated
      },
      { replace: true },
    )

  // Dùng mutateAsync: làm mới sau khi xóa khiến trang này thành "không tìm thấy" và gỡ khỏi màn hình, khi đó callback
  // của mutate() không còn được gọi.
  const confirmTrash = async () => {
    try {
      await trash.mutateAsync()
      toast.success(t('workspaces.delete.done', { name: workspace.name }))
      void navigate(routes.workspaces, { replace: true })
    } catch (error) {
      const apiError = toApiError(error)
      toast.error(t(`errors.${apiError.code}`, { defaultValue: t('workspaces.actionFailed') }))
    }
  }

  return (
    <article className="flex flex-col gap-6" data-testid="workspace-page">
      <Link
        to={routes.workspaces}
        className="-my-2 inline-flex items-center gap-1.5 self-start py-2 text-sm font-medium text-ink-soft underline-offset-4 hover:text-ink hover:underline"
      >
        <ArrowLeft className="size-4" aria-hidden="true" />
        {t('workspaces.backToList')}
      </Link>

      <header className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div className="flex min-w-0 items-start gap-4">
          <WorkspaceGlyph icon={workspace.icon} color={workspace.color} size="lg" />
          <div className="flex min-w-0 flex-col gap-2">
            <h1
              ref={heading}
              tabIndex={-1}
              className="text-3xl tracking-tight outline-none [overflow-wrap:anywhere]"
              data-testid="workspace-title"
            >
              {workspace.name}
            </h1>
            {workspace.description && (
              <p className="max-w-2xl whitespace-pre-line leading-relaxed text-ink-soft [overflow-wrap:anywhere]" data-testid="workspace-description-text">
                {workspace.description}
              </p>
            )}
            <Badge tone="lilac" className="gap-1 self-start">
              <Lock className="size-3.5" aria-hidden="true" />
              {t('library.onlyYou')}
            </Badge>
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button
            variant="accent"
            onClick={() => setDialog({ kind: 'attach', tab: 'library' })}
            icon={<Paperclip className="size-4" aria-hidden="true" />}
            data-testid="attach-documents"
          >
            {t('workspaces.attach.action')}
          </Button>
          <Button
            variant="outline"
            onClick={() => setDialog({ kind: 'edit' })}
            icon={<Pencil className="size-4" aria-hidden="true" />}
            data-testid="edit-workspace"
          >
            {t('workspaces.edit')}
          </Button>
          <Button
            variant="ghost"
            className="text-danger"
            onClick={() => setDialog({ kind: 'delete' })}
            icon={<Trash2 className="size-4" aria-hidden="true" />}
            data-testid="delete-workspace"
          >
            {t('workspaces.delete.action')}
          </Button>
        </div>
      </header>

      <WorkspaceTabs baseId={baseId} tab={tab} documentCount={list.length} onChange={setTab} />

      <div role="tabpanel" id={workspacePanelId(baseId, tab)} aria-labelledby={workspaceTabId(baseId, tab)}>
        {documents.isPending && <Skeleton className="h-56" />}
        {documents.isError && (
          <Alert tone="danger">
            <p>{t('workspaces.sources.loadFailed')}</p>
            <Button variant="outline" className="mt-3" onClick={() => void documents.refetch()}>
              {t('common.retry')}
            </Button>
          </Alert>
        )}
        {documents.data &&
          (tab === 'overview' ? (
            <WorkspaceOverview
              workspace={workspace}
              documents={list}
              onAttach={(attachTab) => setDialog({ kind: 'attach', tab: attachTab })}
              onOpenDocument={openViewer}
              onShowSources={() => setTab('sources')}
            />
          ) : (
            <WorkspaceSources
              workspace={workspace}
              documents={list}
              uploads={uploads}
              onAttach={() => setDialog({ kind: 'attach', tab: 'library' })}
              onOpenDocument={openViewer}
            />
          ))}
      </div>

      <AttachDocumentsDialog
        open={dialog?.kind === 'attach'}
        initialTab={dialog?.kind === 'attach' ? dialog.tab : 'library'}
        workspace={workspace}
        attachedIds={attachedIds}
        uploads={uploads}
        onClose={() => setDialog(null)}
      />
      <CreateWorkspaceDialog open={dialog?.kind === 'edit'} workspace={workspace} onClose={() => setDialog(null)} />
      <ConfirmDialog
        open={dialog?.kind === 'delete'}
        title={t('workspaces.delete.title', { name: workspace.name })}
        description={t('workspaces.delete.body')}
        confirmLabel={t('workspaces.delete.confirm')}
        cancelLabel={t('common.cancel')}
        loading={trash.isPending}
        onCancel={() => setDialog(null)}
        onConfirm={() => void confirmTrash()}
      />
    </article>
  )
}
