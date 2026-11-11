import { Folder as FolderIcon, Home, Library, SearchX, UploadCloud } from 'lucide-react'
import { useEffect, useId, useMemo, useRef, useState, type KeyboardEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useFolderTree, useLibraryDocuments } from '@/features/library/api/queries'
import { DocumentStatusBadge } from '@/features/library/components/DocumentStatusBadge'
import { UploadDropzone } from '@/features/library/components/UploadDropzone'
import { UploadQueue } from '@/features/library/components/UploadQueue'
import { buildTree, flatten, folderPath } from '@/features/library/lib/tree'
import { isFileDrag } from '@/features/library/lib/uploadText'
import type { LibraryQuery } from '@/features/library/types'
import { toApiError } from '@/shared/api/errors'
import { cn } from '@/shared/lib/cn'
import { formatBytes } from '@/shared/lib/format'
import { matchesSearch } from '@/shared/lib/vietnamese'
import { Alert, Button, Checkbox, Dialog, EmptyState, Skeleton, TextField, toast } from '@/shared/ui'
import { useWorkspaceMutation } from '../api/queries'
import { workspacesApi } from '../api/workspacesApi'
import type { WorkspaceUploads } from '../hooks/useWorkspaceUploads'
import type { Workspace } from '../types'

export type AttachTab = 'library' | 'upload'

const TABS: AttachTab[] = ['library', 'upload']
const ALL = '__all__'
const ROOT = '__root__'

interface AttachDocumentsDialogProps {
  open: boolean
  workspace: Workspace
  /** Tài liệu đã gắn sẵn: vẫn hiện trong danh sách nhưng không chọn lại được. */
  attachedIds: Set<string>
  initialTab?: AttachTab
  uploads: WorkspaceUploads
  onClose: () => void
}

/**
 * Gắn tài liệu vào không gian học (NCL-06-CN-001): chọn trong kho qua cây thư mục và ô tìm, hoặc tải tệp mới ngay tại
 * chỗ. Chọn từ kho chỉ thêm liên kết: tài liệu không được tải lại hay xử lý lại (QTN-17).
 */
export function AttachDocumentsDialog({
  open,
  workspace,
  attachedIds,
  initialTab = 'library',
  uploads,
  onClose,
}: AttachDocumentsDialogProps) {
  const { t } = useTranslation()
  const [tab, setTab] = useState<AttachTab>(initialTab)
  const [opened, setOpened] = useState(open)
  const baseId = useId()
  const tabRefs = useRef<Record<AttachTab, HTMLButtonElement | null>>({ library: null, upload: null })

  // Mỗi lần mở lại bắt đầu ở thẻ được chọn.
  if (open !== opened) {
    setOpened(open)
    if (open) setTab(initialTab)
  }

  const select = (next: AttachTab) => {
    setTab(next)
    tabRefs.current[next]?.focus()
  }

  const onTabKey = (event: KeyboardEvent<HTMLButtonElement>) => {
    const index = TABS.indexOf(tab)
    if (event.key === 'ArrowRight' || event.key === 'ArrowLeft') {
      event.preventDefault()
      select(TABS[(index + (event.key === 'ArrowRight' ? 1 : TABS.length - 1)) % TABS.length])
    } else if (event.key === 'Home' || event.key === 'End') {
      event.preventDefault()
      select(event.key === 'Home' ? TABS[0] : TABS[TABS.length - 1])
    }
  }

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={t('workspaces.attach.title')}
      description={t('workspaces.attach.description', { name: workspace.name })}
      className="w-[min(96vw,820px)]"
    >
      <div className="flex flex-col gap-4" data-testid="attach-dialog">
        <div
          role="tablist"
          aria-label={t('workspaces.attach.tabs')}
          className="grid grid-cols-2 gap-1 rounded-field border-[1.5px] border-ink bg-field p-1"
        >
          {TABS.map((name) => {
            const selected = tab === name
            const Icon = name === 'library' ? Library : UploadCloud
            return (
              <button
                key={name}
                ref={(element) => {
                  tabRefs.current[name] = element
                }}
                type="button"
                role="tab"
                id={`${baseId}-tab-${name}`}
                aria-selected={selected}
                aria-controls={`${baseId}-panel-${name}`}
                tabIndex={selected ? 0 : -1}
                onClick={() => setTab(name)}
                onKeyDown={onTabKey}
                data-testid={`attach-tab-${name}`}
                className={cn(
                  'inline-flex h-10 items-center justify-center gap-2 rounded-[10px] text-[0.9375rem] font-medium',
                  'transition-colors duration-150',
                  selected
                    ? 'border-[1.5px] border-ink bg-surface text-ink shadow-[2px_2px_0_var(--color-ink)]'
                    : 'text-ink-soft hover:bg-ink/5',
                )}
              >
                <Icon className="size-4" aria-hidden="true" />
                {t(`workspaces.attach.tab.${name}`)}
              </button>
            )
          })}
        </div>
        <div role="tabpanel" id={`${baseId}-panel-${tab}`} aria-labelledby={`${baseId}-tab-${tab}`}>
          {tab === 'library' ? (
            <LibraryPicker
              workspace={workspace}
              attachedIds={attachedIds}
              onCancel={onClose}
              onDone={onClose}
              onUploadInstead={() => setTab('upload')}
            />
          ) : (
            <UploadPanel workspace={workspace} uploads={uploads} onClose={onClose} />
          )}
        </div>
      </div>
    </Dialog>
  )
}

function LibraryPicker({
  workspace,
  attachedIds,
  onCancel,
  onDone,
  onUploadInstead,
}: {
  workspace: Workspace
  attachedIds: Set<string>
  onCancel: () => void
  onDone: () => void
  onUploadInstead: () => void
}) {
  const { t, i18n } = useTranslation()
  const tree = useFolderTree()
  const [scope, setScope] = useState<string>(ALL)
  const [search, setSearch] = useState('')
  const [chosen, setChosen] = useState<Set<string>>(() => new Set())
  const [error, setError] = useState<string | null>(null)
  const searchField = useRef<HTMLInputElement>(null)
  const folderGroup = useId()
  const folderSelect = useId()
  const folders = useMemo(() => tree.data?.folders ?? [], [tree.data])
  const nodes = useMemo(() => flatten(buildTree(folders)), [folders])
  const query = useMemo<LibraryQuery>(
    () => ({
      scope: scope === ALL ? 'ALL' : 'FOLDER',
      folderId: scope === ALL || scope === ROOT ? null : scope,
      types: [],
      tagIds: [],
      statuses: [],
      sort: 'NAME',
      direction: 'ASC',
    }),
    [scope],
  )
  const documents = useLibraryDocuments(query)
  const attach = useWorkspaceMutation((ids: string[]) => workspacesApi.attach(workspace.id, ids))

  // Thẻ <dialog> tự đặt tiêu điểm khi mở, nên ô tìm nhận tiêu điểm sau đó một nhịp.
  useEffect(() => {
    const timer = window.setTimeout(() => searchField.current?.focus(), 0)
    return () => window.clearTimeout(timer)
  }, [])

  const options = [
    { value: ALL, label: t('library.shortcuts.all'), depth: 0, icon: Library },
    { value: ROOT, label: t('library.shortcuts.root'), depth: 0, icon: Home },
    ...nodes.map((node) => ({ value: node.id, label: node.name, depth: node.depth + 1, icon: FolderIcon })),
  ]
  const list = documents.data ?? []
  const visible = search.trim() ? list.filter((document) => matchesSearch(document.title, search)) : list
  const libraryEmpty = tree.data?.totalDocumentCount === 0

  const toggle = (id: string) =>
    setChosen((current) => {
      const next = new Set(current)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })

  const submit = async () => {
    setError(null)
    try {
      const result = await attach.mutateAsync([...chosen])
      toast.success(
        t('workspaces.attach.done', {
          count: result.attached,
          formatted: result.attached.toLocaleString(i18n.language),
          name: workspace.name,
        }),
      )
      onDone()
    } catch (caught) {
      const apiError = toApiError(caught)
      setError(
        apiError.status === 404
          ? t('workspaces.attach.gone')
          : t(`errors.${apiError.code}`, { defaultValue: t('workspaces.actionFailed') }),
      )
    }
  }

  if (libraryEmpty) {
    return (
      <EmptyState
        icon={<Library />}
        title={t('workspaces.attach.libraryEmptyTitle')}
        description={t('workspaces.attach.libraryEmptyBody')}
        action={
          <Button variant="dark" onClick={onUploadInstead} icon={<UploadCloud className="size-4" aria-hidden="true" />}>
            {t('workspaces.attach.uploadInstead')}
          </Button>
        }
      />
    )
  }

  return (
    <form
      className="flex flex-col gap-4"
      onSubmit={(event) => {
        event.preventDefault()
        if (chosen.size) void submit()
      }}
    >
      <div className="grid gap-4 sm:grid-cols-[14rem_minmax(0,1fr)]">
        <div className="flex flex-col gap-1.5 sm:hidden">
          <label htmlFor={folderSelect} className="text-sm font-medium">
            {t('workspaces.attach.folder')}
          </label>
          <select
            id={folderSelect}
            value={scope}
            onChange={(event) => setScope(event.target.value)}
            data-testid="attach-folder-select"
            className="h-12 w-full rounded-field border-[1.5px] border-ink bg-surface px-3 text-[0.9375rem]"
          >
            {options.map((option) => (
              <option key={option.value} value={option.value}>
                {`${'  '.repeat(option.depth)}${option.label}`}
              </option>
            ))}
          </select>
        </div>
        <fieldset
          className="hidden max-h-[46dvh] flex-col gap-1 overflow-y-auto rounded-field border-[1.5px] border-line p-2 sm:flex"
          data-testid="attach-folders"
        >
          <legend className="sr-only">{t('workspaces.attach.folder')}</legend>
          {tree.isPending && <Skeleton className="h-24" />}
          {options.map((option) => (
            <label
              key={option.value}
              style={{ paddingLeft: `${0.625 + option.depth * 0.9}rem` }}
              className={cn(
                'flex min-h-10 cursor-pointer items-center gap-2 rounded-xl pr-2 text-sm hover:bg-ink/5',
                'has-[:checked]:bg-sun has-[:checked]:font-medium',
                'has-[:focus-visible]:outline-3 has-[:focus-visible]:outline-focus',
              )}
            >
              <input
                type="radio"
                name={folderGroup}
                value={option.value}
                checked={scope === option.value}
                onChange={() => setScope(option.value)}
                className="sr-only"
              />
              <option.icon className="size-4 shrink-0" aria-hidden="true" />
              <span className="min-w-0 flex-1 truncate">{option.label}</span>
            </label>
          ))}
        </fieldset>

        <div className="flex min-w-0 flex-col gap-3">
          <TextField
            ref={searchField}
            type="search"
            label={t('workspaces.attach.search')}
            value={search}
            autoComplete="off"
            onChange={(event) => setSearch(event.target.value)}
            data-testid="attach-search"
          />
          <div
            className="flex max-h-[38dvh] min-h-32 flex-col overflow-y-auto rounded-field border-[1.5px] border-line"
            aria-busy={documents.isPending || undefined}
          >
            {documents.isPending && (
              <div className="flex flex-col gap-2 p-3">
                <Skeleton className="h-10" />
                <Skeleton className="h-10" />
              </div>
            )}
            {documents.isError && (
              <p role="alert" className="p-4 text-sm text-danger">
                {t('library.loadFailed')}
              </p>
            )}
            {documents.data && visible.length === 0 && (
              <EmptyState
                size="compact"
                className="m-auto"
                icon={<SearchX />}
                title={search.trim() ? t('workspaces.attach.noMatch') : t('workspaces.attach.folderEmpty')}
              />
            )}
            {visible.length > 0 && (
              <ul className="flex flex-col divide-y divide-line" data-testid="attach-documents">
                {visible.map((document) => {
                  const attached = attachedIds.has(document.id)
                  const path = folderPath(folders, document.folderId)
                  return (
                    <li
                      key={document.id}
                      data-testid="attach-option"
                      data-title={document.title}
                      className={cn('px-3 py-2', chosen.has(document.id) && 'bg-sun-soft')}
                    >
                      <Checkbox
                        checked={attached || chosen.has(document.id)}
                        disabled={attached}
                        onChange={() => toggle(document.id)}
                        label={
                          <span className="flex flex-col gap-0.5">
                            <span className="font-medium text-ink [overflow-wrap:anywhere]">{document.title}</span>
                            <span className="flex flex-wrap items-center gap-x-2 gap-y-1 text-[0.8125rem] text-muted">
                              <span>{t(`library.type.${document.type}`)}</span>
                              <span>{formatBytes(document.sizeBytes, i18n.language)}</span>
                              <span className="[overflow-wrap:anywhere]">
                                {path.length ? path.map((folder) => folder.name).join(' / ') : t('library.shortcuts.root')}
                              </span>
                              <DocumentStatusBadge status={document.status} />
                              {attached && (
                                <span className="font-medium text-ink" data-testid="attach-already">
                                  {t('workspaces.attach.already')}
                                </span>
                              )}
                            </span>
                          </span>
                        }
                      />
                    </li>
                  )
                })}
              </ul>
            )}
          </div>
        </div>
      </div>

      {error && (
        <Alert tone="danger" role="alert">
          {error}
        </Alert>
      )}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <p className="text-sm text-muted" aria-live="polite" data-testid="attach-selected">
          {t('workspaces.attach.selected', { count: chosen.size, formatted: chosen.size.toLocaleString(i18n.language) })}
        </p>
        <div className="flex flex-col-reverse gap-3 sm:flex-row">
          <Button variant="outline" onClick={onCancel}>
            {t('common.cancel')}
          </Button>
          <Button
            type="submit"
            variant="accent"
            loading={attach.isPending}
            disabled={chosen.size === 0}
            data-testid="attach-submit"
          >
            {t('workspaces.attach.submit')}
          </Button>
        </div>
      </div>
    </form>
  )
}

function UploadPanel({
  workspace,
  uploads,
  onClose,
}: {
  workspace: Workspace
  uploads: WorkspaceUploads
  onClose: () => void
}) {
  const { t } = useTranslation()
  const [dragging, setDragging] = useState(false)
  const folderName = uploads.folder?.name ?? workspace.name
  const reused = Object.values(uploads.links).includes('reused')
  const failed = Object.values(uploads.links).includes('failed')

  const upload = (files: File[]) => {
    setDragging(false)
    void uploads.start(files)
  }

  return (
    <div
      className="flex flex-col gap-3"
      data-testid="attach-upload"
      onDragOver={(event) => {
        if (!isFileDrag(event)) return
        event.preventDefault()
        event.dataTransfer.dropEffect = 'copy'
        if (!dragging) setDragging(true)
      }}
      onDragLeave={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setDragging(false)
      }}
      onDrop={(event) => {
        if (!isFileDrag(event)) return
        event.preventDefault()
        upload(Array.from(event.dataTransfer.files))
      }}
    >
      <UploadDropzone targetName={folderName} active={dragging} onFiles={upload} />
      <p className="text-[0.8125rem] leading-relaxed text-muted" data-testid="attach-upload-hint">
        {t('workspaces.attach.uploadHint', { folder: folderName })}
      </p>
      {uploads.folderFailed && (
        <Alert tone="danger" role="alert">
          {t('workspaces.attach.folderFailed')}
        </Alert>
      )}
      <UploadQueue
        items={uploads.items}
        onCancel={uploads.cancel}
        onRetry={uploads.retry}
        onClear={uploads.clearFinished}
      />
      {reused && (
        <Alert tone="info" data-testid="attach-reused">
          {t('workspaces.attach.reused')}
        </Alert>
      )}
      {failed && (
        <Alert tone="danger" role="alert">
          {t('workspaces.attach.linkFailed')}
        </Alert>
      )}
      <div className="flex justify-end">
        <Button variant="outline" onClick={onClose} loading={uploads.preparing}>
          {t('workspaces.attach.finish')}
        </Button>
      </div>
    </div>
  )
}
