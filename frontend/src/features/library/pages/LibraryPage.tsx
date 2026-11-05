import { BookOpen, ChevronRight, ClipboardPaste, Folder as FolderIcon, FolderTree as FolderTreeIcon, Globe, SearchX } from 'lucide-react'
import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState, type DragEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useSearchParams } from 'react-router'
import { useSidePanel } from '@/app/providers/sidePanel'
import { routes } from '@/app/routes'
import { toApiError } from '@/shared/api/errors'
import { usePageHeading } from '@/shared/hooks/usePageHeading'
import { cn } from '@/shared/lib/cn'
import { formatBytes } from '@/shared/lib/format'
import { Alert, Button, ConfirmDialog, Drawer, EmptyState, Skeleton, buttonClasses, toast } from '@/shared/ui'
import { libraryApi } from '../api/libraryApi'
import { useFolderTree, useLibraryDocuments, useLibraryMutation, useTags } from '../api/queries'
import { AddSourceDialog, type AddSourceTab } from '../components/AddSourceDialog'
import { BulkActionBar } from '../components/BulkActionBar'
import { DocumentFilters, type LibraryLayout } from '../components/DocumentFilters'
import { DocumentGrid } from '../components/DocumentGrid'
import { DocumentInfoPanel } from '../components/DocumentInfoPanel'
import type { DocumentItemHandlers } from '../components/DocumentItemControls'
import { DocumentList } from '../components/DocumentList'
import { DocumentPeek } from '../components/DocumentPeek'
import { DuplicateDialog } from '../components/DuplicateDialog'
import { FolderNameDialog } from '../components/FolderNameDialog'
import { FolderTree } from '../components/FolderTree'
import { MoveDialog } from '../components/MoveDialog'
import { PrivacyNote } from '../components/PrivacyNote'
import { RenameDocumentDialog } from '../components/RenameDocumentDialog'
import { StorageMeter } from '../components/StorageMeter'
import { TagAssignDialog, TagManagerDialog } from '../components/TagManager'
import { UploadDropzone } from '../components/UploadDropzone'
import { UploadQueue } from '../components/UploadQueue'
import { useChunkedUpload, type UploadItem } from '../hooks/useChunkedUpload'
import { conflictMessage, useDocumentActions } from '../hooks/useDocumentActions'
import { DRAG_DOCUMENTS, readDraggedDocuments, writeDraggedDocuments } from '../lib/drag'
import { hasFilters, parseLibraryQuery, toSearchParams } from '../lib/query'
import { isFileDrag } from '../lib/uploadText'
import { buildTree, descendantIds, folderPath, type FolderNode } from '../lib/tree'
import type { DocumentCard, Folder, LibraryQuery } from '../types'

const LAYOUT_KEY = 'lc.library.layout'

type DialogState =
  | { kind: 'createFolder'; parent: Folder | null }
  | { kind: 'renameFolder'; folder: Folder }
  | { kind: 'moveFolder'; folder: Folder }
  | { kind: 'deleteFolder'; folder: FolderNode }
  | { kind: 'moveDocuments' }
  | { kind: 'tagDocuments' }
  | { kind: 'deleteDocuments' }
  | { kind: 'manageTags' }
  | { kind: 'duplicate'; item: UploadItem }
  | { kind: 'renameUpload'; item: UploadItem }
  | null

function storedLayout(): LibraryLayout {
  try {
    return window.localStorage.getItem(LAYOUT_KEY) === 'list' ? 'list' : 'grid'
  } catch {
    return 'grid'
  }
}

/**
 * Kho tài liệu của người học (NCL-01-CN-004, NCL-05-CN-001): cây thư mục nhiều cấp bên trái với hai lối tắt gần đây
 * và có gắn sao, danh sách tài liệu ở giữa với bộ lọc, sắp xếp, dạng lưới hoặc danh sách, chọn nhiều mục để chuyển,
 * gắn nhãn hay xóa cùng lúc. Chỉ liệt kê tài liệu của chính người đang đăng nhập.
 */
export function LibraryPage() {
  const { t, i18n } = useTranslation()
  const heading = usePageHeading(t('library.title'))
  const [params, setParams] = useSearchParams()
  const query = useMemo(() => parseLibraryQuery(params), [params])
  const [layout, setLayout] = useState<LibraryLayout>(() => (params.get('layout') === 'list' ? 'list' : storedLayout()))
  const tree = useFolderTree()
  const tags = useTags()
  const documents = useLibraryDocuments(query)
  const actions = useDocumentActions()
  const panel = useSidePanel()
  const [selection, setSelection] = useState<{ scope: string; ids: Set<string> }>(() => ({ scope: '', ids: new Set() }))
  const [dialog, setDialog] = useState<DialogState>(null)
  const [treeOpen, setTreeOpen] = useState(false)
  const [moveError, setMoveError] = useState<string | null>(null)
  const uploads = useChunkedUpload()
  const [addSource, setAddSource] = useState<AddSourceTab | null>(null)
  const [fileDragging, setFileDragging] = useState(false)
  const shownDuplicates = useRef(new Set<string>())

  // Báo trùng ngay bằng hộp thoại, một lần cho mỗi tệp; sau đó vẫn mở lại được từ hàng đợi.
  useEffect(() => {
    if (dialog !== null) return
    const next = uploads.items.find((item) => item.state === 'duplicate' && !shownDuplicates.current.has(item.key))
    if (!next) return
    shownDuplicates.current.add(next.key)
    setDialog({ kind: 'duplicate', item: next })
  }, [uploads.items, dialog])

  const folders = useMemo(() => tree.data?.folders ?? [], [tree.data])
  const tagMap = useMemo(() => new Map((tags.data ?? []).map((tag) => [tag.id, tag])), [tags.data])
  const roots = useMemo(() => buildTree(folders), [folders])
  const list = useMemo(() => documents.data ?? [], [documents.data])
  const scopeKey = `${query.scope}:${query.folderId ?? ''}`
  // Chỉ giữ lựa chọn của thư mục hay lối tắt đang xem, và chỉ tài liệu còn hiện trong danh sách (vừa chuyển đi, vừa
  // xóa hay bị lọc ra thì tự bỏ chọn), để thao tác hàng loạt không lỡ tay áp vào tài liệu đang không thấy.
  const selected = useMemo(() => {
    if (selection.scope !== scopeKey) return new Set<string>()
    return new Set(list.filter((document) => selection.ids.has(document.id)).map((document) => document.id))
  }, [selection, scopeKey, list])
  const setSelected = useCallback(
    (update: Set<string> | ((current: Set<string>) => Set<string>)) =>
      setSelection(() => ({ scope: scopeKey, ids: typeof update === 'function' ? update(selected) : update })),
    [scopeKey, selected],
  )
  const selectedDocuments = list.filter((document) => selected.has(document.id))

  const hrefFor = useCallback(
    (scope: Pick<LibraryQuery, 'scope' | 'folderId'>) => {
      const next = toSearchParams({ ...query, ...scope, folderId: scope.folderId ?? null })
      const search = next.toString()
      return search ? `${routes.library}?${search}` : routes.library
    },
    [query],
  )

  // Hai lần bấm bộ lọc liên tiếp có thể tới trước khi địa chỉ trang kịp đổi: mỗi lần đổi tính từ trạng thái mới nhất
  // chứ không từ lần hiển thị trước, để không lần nào ghi đè mất lần trước.
  const latestQuery = useRef(query)
  useLayoutEffect(() => {
    latestQuery.current = query
  }, [query])
  const setQuery = (update: LibraryQuery | ((current: LibraryQuery) => LibraryQuery)) => {
    const next = typeof update === 'function' ? update(latestQuery.current) : update
    latestQuery.current = next
    setParams(toSearchParams(next), { replace: true })
  }

  const changeLayout = (next: LibraryLayout) => {
    setLayout(next)
    try {
      window.localStorage.setItem(LAYOUT_KEY, next)
    } catch {
      // Trình duyệt chặn lưu trữ: chỉ không nhớ được lựa chọn cho lần sau.
    }
  }

  const moveFolder = useLibraryMutation(({ id, parentId }: { id: string; parentId: string | null }) =>
    libraryApi.moveFolder(id, parentId),
  )
  const trashFolder = useLibraryMutation((id: string) => libraryApi.trashFolder(id))

  const moveDocuments = async (ids: string[], folderId: string | null, name: string) => {
    const failure = await actions.move(ids, folderId, name)
    if (!failure) {
      setSelected(new Set())
      return true
    }
    setMoveError(failure.code === 'LIBRARY_NAME_TAKEN' ? conflictMessage(failure, t) : null)
    return false
  }

  const relocateFolder = (folder: Folder, parentId: string | null, parentName: string) => {
    moveFolder.mutate(
      { id: folder.id, parentId },
      {
        onSuccess: () => {
          toast.success(t('library.moveDialog.folderMoved', { name: folder.name, folder: parentName }))
          setDialog(null)
        },
        onError: (error) => {
          const apiError = toApiError(error)
          toast.error(t(`errors.${apiError.code}`, { defaultValue: t('library.actionFailed') }))
        },
      },
    )
  }

  const handlers: DocumentItemHandlers = {
    selected,
    onToggleSelect: (id) =>
      setSelected((current) => {
        const next = new Set(current)
        if (next.has(id)) next.delete(id)
        else next.add(id)
        return next
      }),
    onToggleStar: (document) => void actions.toggleStar(document.id, document.title, !document.starred),
    onInfo: (document) =>
      panel.open({
        id: `document-info:${document.id}`,
        title: document.title,
        render: () => <DocumentInfoPanel id={document.id} onClose={panel.close} />,
      }),
    onPeek: (document) =>
      panel.open({
        id: `document:${document.id}`,
        title: document.title,
        render: () => <DocumentPeek id={document.id} onOpenFull={panel.close} />,
      }),
    onDragStart: (document: DocumentCard, data: DataTransfer) =>
      writeDraggedDocuments(data, selected.has(document.id) ? [...selected] : [document.id]),
  }

  const openDialog = (next: DialogState) => {
    setTreeOpen(false)
    setDialog(next)
  }

  const treeProps = tree.data && {
    tree: tree.data,
    query,
    hrefFor,
    // Trên điện thoại cây nằm trong ngăn kéo: chọn thao tác thì đóng ngăn kéo để hộp thoại không chồng lên nó.
    onCreateFolder: (parent: Folder | null) => openDialog({ kind: 'createFolder', parent }),
    onRenameFolder: (folder: Folder) => openDialog({ kind: 'renameFolder', folder }),
    onMoveFolder: (folder: Folder) => openDialog({ kind: 'moveFolder', folder }),
    onDeleteFolder: (folder: FolderNode) => openDialog({ kind: 'deleteFolder', folder }),
    onDropDocuments: (ids: string[], folderId: string | null, name: string) => void moveDocuments(ids, folderId, name),
    onDropFolder: (folderId: string, parentId: string | null, parentName: string) => {
      const folder = folders.find((candidate) => candidate.id === folderId)
      if (folder && (folder.parentId ?? null) !== parentId) relocateFolder(folder, parentId, parentName)
    },
  }

  const currentPath = query.scope === 'FOLDER' ? folderPath(folders, query.folderId) : []
  const currentFolder = currentPath.at(-1)
  const viewTitle =
    query.scope === 'RECENT'
      ? t('library.shortcuts.recent')
      : query.scope === 'STARRED'
        ? t('library.shortcuts.starred')
        : query.scope === 'FOLDER'
          ? (currentFolder?.name ?? t('library.shortcuts.root'))
          : t('library.shortcuts.all')
  const subfolders =
    query.scope === 'FOLDER'
      ? query.folderId
        ? (flattenFind(roots, query.folderId)?.children ?? [])
        : roots
      : []
  const folderMissing =
    query.scope === 'FOLDER' && query.folderId && documents.isError && toApiError(documents.error).status === 404
  const libraryEmpty = tree.data?.totalDocumentCount === 0 && query.scope === 'ALL' && !hasFilters(query)
  const totalBytes = list.reduce((sum, document) => sum + document.sizeBytes, 0)
  // Tệp kéo vào trang đi vào thư mục đang mở; ở các lối tắt (tất cả, gần đây, có gắn sao) thì vào thư mục gốc.
  const uploadFolderId = query.scope === 'FOLDER' ? (query.folderId ?? null) : null
  const uploadFolderName = uploadFolderId ? (currentFolder?.name ?? t('library.shortcuts.root')) : t('library.shortcuts.root')
  const canUpload = !folderMissing && !(uploadFolderId && !currentFolder && tree.isPending)
  const uploadFiles = (files: File[]) => {
    setFileDragging(false)
    if (!canUpload || !files.length) return
    const added = uploads.add(files, uploadFolderId, uploadFolderName)
    toast.success(t('library.upload.started', { count: added.length, folder: uploadFolderName }))
  }

  const asideContent = treeProps && (
    <div className="flex flex-col gap-4">
      <FolderTree {...treeProps} onNavigate={() => setTreeOpen(false)} />
      <StorageMeter />
    </div>
  )

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div className="flex max-w-xl flex-col gap-2">
          <h1 ref={heading} tabIndex={-1} className="text-3xl tracking-tight outline-none">
            {t('library.title')}
          </h1>
          <p className="leading-relaxed text-muted">{t('library.subtitle')}</p>
        </div>
        {list.length > 0 && (
          <p className="text-sm font-medium tabular-nums" data-testid="library-summary">
            {t('library.summary', { count: list.length, size: formatBytes(totalBytes, i18n.language) })}
          </p>
        )}
      </header>

      <PrivacyNote />

      <div className="grid gap-6 lg:grid-cols-[17.5rem_minmax(0,1fr)]">
        <aside className="hidden lg:block">
          {tree.isPending ? <Skeleton className="h-72" /> : <div className="sticky top-4">{asideContent}</div>}
        </aside>

        <div
          className="flex min-w-0 flex-col gap-4"
          data-testid="library-main"
          onDragOver={(event) => {
            if (!canUpload || !isFileDrag(event)) return
            event.preventDefault()
            event.dataTransfer.dropEffect = 'copy'
            if (!fileDragging) setFileDragging(true)
          }}
          onDragLeave={(event) => {
            if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setFileDragging(false)
          }}
          onDrop={(event) => {
            if (!isFileDrag(event)) return
            event.preventDefault()
            uploadFiles(Array.from(event.dataTransfer.files))
          }}
        >
          <div className="lg:hidden">
            <Button
              variant="outline"
              className="w-full justify-start"
              onClick={() => setTreeOpen(true)}
              icon={<FolderTreeIcon className="size-4" aria-hidden="true" />}
              data-testid="open-folder-tree"
              aria-haspopup="dialog"
            >
              <span className="truncate">{t('library.tree.mobileToggle', { name: viewTitle })}</span>
            </Button>
            <Drawer open={treeOpen} onClose={() => setTreeOpen(false)} label={t('library.tree.label')} side="left">
              <div className="h-full overflow-y-auto bg-paper p-4">{asideContent}</div>
            </Drawer>
          </div>

          <div className="flex flex-col gap-1">
            {query.scope === 'FOLDER' && (
              <nav aria-label={t('library.tree.folders')} className="flex flex-wrap items-center gap-1 text-[0.8125rem] text-muted">
                <Link to={hrefFor({ scope: 'FOLDER', folderId: null })} className="underline-offset-4 hover:underline">
                  {t('library.shortcuts.root')}
                </Link>
                {currentPath.map((folder, index) => (
                  <span key={folder.id} className="flex items-center gap-1">
                    <ChevronRight className="size-3.5" aria-hidden="true" />
                    {index === currentPath.length - 1 ? (
                      <span aria-current="page">{folder.name}</span>
                    ) : (
                      <Link to={hrefFor({ scope: 'FOLDER', folderId: folder.id })} className="underline-offset-4 hover:underline">
                        {folder.name}
                      </Link>
                    )}
                  </span>
                ))}
              </nav>
            )}
            <h2 className="text-xl font-medium [overflow-wrap:anywhere]" data-testid="library-view-title">
              {viewTitle}
            </h2>
          </div>

          {subfolders.length > 0 && (
            <section aria-label={t('library.tree.subfolders')} className="flex flex-wrap gap-2" data-testid="subfolders">
              {subfolders.map((folder) => (
                <SubfolderChip
                  key={folder.id}
                  folder={folder}
                  href={hrefFor({ scope: 'FOLDER', folderId: folder.id })}
                  onDropDocuments={(ids) => void moveDocuments(ids, folder.id, folder.name)}
                />
              ))}
            </section>
          )}

          {canUpload && (
            <div className="flex flex-col gap-2">
              <UploadDropzone targetName={uploadFolderName} active={fileDragging} onFiles={uploadFiles} />
              <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap" data-testid="add-source-actions">
                <Button
                  variant="outline"
                  onClick={() => setAddSource('web')}
                  icon={<Globe className="size-4" aria-hidden="true" />}
                  data-testid="add-from-web"
                >
                  {t('library.addSource.fromWeb')}
                </Button>
                <Button
                  variant="outline"
                  onClick={() => setAddSource('text')}
                  icon={<ClipboardPaste className="size-4" aria-hidden="true" />}
                  data-testid="add-from-text"
                >
                  {t('library.addSource.fromText')}
                </Button>
              </div>
            </div>
          )}
          <AddSourceDialog
            open={addSource !== null}
            initialTab={addSource ?? 'web'}
            folderId={uploadFolderId}
            folderName={uploadFolderName}
            onClose={() => setAddSource(null)}
          />
          <UploadQueue
            items={uploads.items}
            onCancel={uploads.cancel}
            onRetry={uploads.retry}
            onRename={(item) => setDialog({ kind: 'renameUpload', item })}
            onShowDuplicate={(item) => setDialog({ kind: 'duplicate', item })}
            onClear={uploads.clearFinished}
          />

          <DocumentFilters
            query={query}
            tags={tags.data ?? []}
            layout={layout}
            onChange={setQuery}
            onLayoutChange={changeLayout}
            onManageTags={() => setDialog({ kind: 'manageTags' })}
          />

          {documents.isPending && (
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3" aria-busy="true">
              <Skeleton className="h-40" />
              <Skeleton className="h-40" />
              <Skeleton className="hidden h-40 xl:block" />
            </div>
          )}
          {folderMissing && (
            <EmptyState
              icon={<SearchX className="size-6" />}
              title={t('library.folderNotFound.title')}
              description={t('library.folderNotFound.body')}
              action={
                <Link to={routes.library} className={buttonClasses('outline')}>
                  {t('library.folderNotFound.back')}
                </Link>
              }
              className="border-ink/50"
            />
          )}
          {documents.isError && !folderMissing && (
            <Alert tone="danger">
              <p>{t('library.loadFailed')}</p>
              <Button variant="outline" className="mt-3" onClick={() => void documents.refetch()}>
                {t('common.retry')}
              </Button>
            </Alert>
          )}

          {documents.data && list.length > 0 && !documents.isError && (
            <div className={cn('transition-opacity', documents.isPlaceholderData && 'opacity-60')} aria-busy={documents.isPlaceholderData || undefined}>
              {layout === 'grid' ? (
                <DocumentGrid documents={list} tags={tagMap} {...handlers} />
              ) : (
                <DocumentList documents={list} tags={tagMap} {...handlers} />
              )}
            </div>
          )}

          {documents.data && list.length === 0 && !documents.isError && libraryEmpty && (
            <div
              data-testid="library-empty"
              className="flex flex-col items-center gap-3 rounded-card border-[1.5px] border-dashed border-ink/50 px-6 py-12 text-center"
            >
              <span aria-hidden="true" className="grid size-14 place-items-center rounded-2xl border-[1.5px] border-ink bg-sun">
                <BookOpen className="size-6" />
              </span>
              <h2 className="text-xl font-medium">{t('library.empty.title')}</h2>
              <p className="max-w-md text-sm leading-relaxed text-muted">{t('library.empty.body')}</p>
            </div>
          )}
          {documents.data && list.length === 0 && !documents.isError && !libraryEmpty && (
            <EmptyView query={query} onClear={() => setQuery({ ...query, types: [], tagIds: [], statuses: [], from: undefined, to: undefined })} />
          )}

          {selected.size > 0 && (
            <BulkActionBar
              count={selected.size}
              total={list.length}
              busy={actions.pending}
              onSelectAll={() => setSelected(new Set(list.map((document) => document.id)))}
              onClear={() => setSelected(new Set())}
              onMove={() => {
                setMoveError(null)
                setDialog({ kind: 'moveDocuments' })
              }}
              onTag={() => setDialog({ kind: 'tagDocuments' })}
              onStar={() => void actions.starMany([...selected], true)}
              onDelete={() => setDialog({ kind: 'deleteDocuments' })}
            />
          )}
        </div>
      </div>

      <FolderNameDialog
        open={dialog?.kind === 'createFolder' || dialog?.kind === 'renameFolder'}
        mode={dialog?.kind === 'renameFolder' ? 'rename' : 'create'}
        parent={dialog?.kind === 'createFolder' ? dialog.parent : null}
        folder={dialog?.kind === 'renameFolder' ? dialog.folder : null}
        onClose={() => setDialog(null)}
      />
      <MoveDialog
        open={dialog?.kind === 'moveFolder'}
        title={t('library.moveDialog.folderTitle', { name: dialog?.kind === 'moveFolder' ? dialog.folder.name : '' })}
        folders={folders}
        currentFolderId={dialog?.kind === 'moveFolder' ? (dialog.folder.parentId ?? null) : undefined}
        excluded={dialog?.kind === 'moveFolder' ? descendantIds(folders, dialog.folder.id) : undefined}
        loading={moveFolder.isPending}
        onClose={() => setDialog(null)}
        onMove={(parentId, name) => dialog?.kind === 'moveFolder' && relocateFolder(dialog.folder, parentId, name)}
      />
      <MoveDialog
        open={dialog?.kind === 'moveDocuments'}
        title={t('library.moveDialog.documentsTitle', { count: selected.size })}
        folders={folders}
        currentFolderId={commonFolder(selectedDocuments)}
        loading={actions.pending}
        error={moveError}
        onClose={() => setDialog(null)}
        onMove={(folderId, name) =>
          void moveDocuments([...selected], folderId, name).then((done) => done && setDialog(null))
        }
      />
      <TagAssignDialog
        open={dialog?.kind === 'tagDocuments'}
        documents={selectedDocuments}
        loading={actions.pending}
        onClose={() => setDialog(null)}
        onApply={(add, remove) =>
          void actions.tagMany([...selected], add, remove).then((done) => done && setDialog(null))
        }
      />
      <ConfirmDialog
        open={dialog?.kind === 'deleteDocuments'}
        title={t('library.deleteDocuments.title', { count: selected.size })}
        description={t('library.deleteDocuments.body')}
        confirmLabel={t('library.deleteDocuments.confirm')}
        cancelLabel={t('common.cancel')}
        loading={actions.pending}
        onCancel={() => setDialog(null)}
        onConfirm={() =>
          void actions.trash([...selected]).then((done) => {
            if (done) {
              setSelected(new Set())
              setDialog(null)
            }
          })
        }
      />
      <ConfirmDialog
        open={dialog?.kind === 'deleteFolder'}
        title={t('library.deleteFolder.title', { name: dialog?.kind === 'deleteFolder' ? dialog.folder.name : '' })}
        description={t('library.deleteFolder.body', { count: dialog?.kind === 'deleteFolder' ? dialog.folder.totalCount : 0 })}
        confirmLabel={t('library.deleteFolder.confirm')}
        cancelLabel={t('common.cancel')}
        loading={trashFolder.isPending}
        onCancel={() => setDialog(null)}
        onConfirm={() => {
          if (dialog?.kind !== 'deleteFolder') return
          const folder = dialog.folder
          trashFolder.mutate(folder.id, {
            onSuccess: (result) => {
              toast.success(t('library.deleteFolder.done', { name: folder.name, count: result.documents }))
              setDialog(null)
              // Đang xem chính thư mục vừa xóa (hay thư mục con của nó) thì về thư mục cha.
              if (query.scope === 'FOLDER' && query.folderId && descendantIds(folders, folder.id).has(query.folderId)) {
                setQuery({ ...query, folderId: folder.parentId ?? null })
              }
            },
            onError: () => toast.error(t('library.actionFailed')),
          })
        }}
      />
      <TagManagerDialog open={dialog?.kind === 'manageTags'} onClose={() => setDialog(null)} />
      <DuplicateDialog
        fileName={dialog?.kind === 'duplicate' ? dialog.item.file.name : ''}
        info={dialog?.kind === 'duplicate' ? (dialog.item.duplicate ?? null) : null}
        onClose={() => setDialog(null)}
      />
      {dialog?.kind === 'renameUpload' && dialog.item.document && (
        <RenameDocumentDialog
          open
          document={dialog.item.document}
          onClose={() => setDialog(null)}
          onRenamed={(title) => uploads.rename(dialog.item.key, title)}
        />
      )}
    </div>
  )
}

function flattenFind(nodes: FolderNode[], id: string): FolderNode | undefined {
  for (const node of nodes) {
    if (node.id === id) return node
    const found = flattenFind(node.children, id)
    if (found) return found
  }
  return undefined
}

/** Thư mục chung của các tài liệu đã chọn; nằm rải nhiều nơi thì không có. */
function commonFolder(documents: DocumentCard[]): string | null | undefined {
  if (!documents.length) return undefined
  const first = documents[0].folderId ?? null
  return documents.every((document) => (document.folderId ?? null) === first) ? first : undefined
}

function SubfolderChip({
  folder,
  href,
  onDropDocuments,
}: {
  folder: FolderNode
  href: string
  onDropDocuments: (ids: string[]) => void
}) {
  const { t } = useTranslation()
  const [over, setOver] = useState(false)
  return (
    <Link
      to={href}
      data-testid="subfolder"
      onDragOver={(event: DragEvent) => {
        if (!Array.from(event.dataTransfer.types).includes(DRAG_DOCUMENTS)) return
        event.preventDefault()
        setOver(true)
      }}
      onDragLeave={() => setOver(false)}
      onDrop={(event: DragEvent) => {
        event.preventDefault()
        setOver(false)
        const ids = readDraggedDocuments(event.dataTransfer)
        if (ids.length) onDropDocuments(ids)
      }}
      className={cn(
        'inline-flex min-h-11 items-center gap-2 rounded-xl border-[1.5px] border-ink bg-surface px-3 text-[0.9375rem]',
        'hover:bg-paper',
        over && 'bg-sun ring-2 ring-ink',
      )}
    >
      <FolderIcon className="size-4" aria-hidden="true" />
      <span className="max-w-[14rem] truncate">{folder.name}</span>
      <span className="text-xs tabular-nums text-muted" aria-hidden="true">
        {folder.totalCount}
      </span>
      <span className="sr-only">({t('library.tree.count', { count: folder.totalCount })})</span>
    </Link>
  )
}

function EmptyView({ query, onClear }: { query: LibraryQuery; onClear: () => void }) {
  const { t } = useTranslation()
  if (hasFilters(query)) {
    return (
      <EmptyState
        icon={<SearchX className="size-6" />}
        title={t('library.emptyView.filterTitle')}
        description={t('library.emptyView.filterBody')}
        action={
          <Button variant="outline" onClick={onClear}>
            {t('library.filters.clear')}
          </Button>
        }
      />
    )
  }
  const key = query.scope === 'RECENT' ? 'recent' : query.scope === 'STARRED' ? 'starred' : 'folder'
  return (
    <EmptyState
      icon={<FolderIcon className="size-6" />}
      title={t(`library.emptyView.${key}Title`)}
      description={t(`library.emptyView.${key}Body`)}
    />
  )
}
