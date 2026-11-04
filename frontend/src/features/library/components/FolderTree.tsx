import { ChevronRight, Clock, Folder as FolderIcon, FolderInput, FolderPlus, Home, Library, Pencil, Star, Trash2 } from 'lucide-react'
import { useMemo, useState, type DragEvent, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import { cn } from '@/shared/lib/cn'
import { IconButton } from '@/shared/ui'
import { DRAG_DOCUMENTS, DRAG_FOLDER, readDraggedDocuments } from '../lib/drag'
import { buildTree, descendantIds, type FolderNode } from '../lib/tree'
import type { Folder, FolderTree as FolderTreeData, LibraryQuery } from '../types'
import { FolderContextMenu } from './FolderContextMenu'

interface FolderTreeProps {
  tree: FolderTreeData
  query: LibraryQuery
  /** Đường dẫn của từng phạm vi, giữ nguyên bộ lọc đang chọn. */
  hrefFor: (scope: Pick<LibraryQuery, 'scope' | 'folderId'>) => string
  onNavigate?: () => void
  onCreateFolder: (parent: Folder | null) => void
  onRenameFolder: (folder: Folder) => void
  onMoveFolder: (folder: Folder) => void
  onDeleteFolder: (folder: FolderNode) => void
  onDropDocuments: (documentIds: string[], folderId: string | null, folderName: string) => void
  onDropFolder: (folderId: string, parentId: string | null, parentName: string) => void
}

/**
 * Cây thư mục bên trái kho tài liệu (NCL-05-CN-001): hai lối tắt "Gần đây" và "Có gắn sao" luôn ở đầu, rồi tất cả tài
 * liệu, thư mục gốc và cây thư mục nhiều cấp với số tài liệu của từng thư mục. Thả tài liệu hoặc thư mục lên một thư
 * mục để chuyển vào đó; mọi thao tác kéo thả đều có cách làm tương đương bằng trình đơn.
 */
export function FolderTree(props: FolderTreeProps) {
  const { t } = useTranslation()
  const { tree, query, hrefFor, onNavigate, onCreateFolder } = props
  const roots = useMemo(() => buildTree(tree.folders), [tree.folders])
  const [collapsed, setCollapsed] = useState<Set<string>>(() => new Set())
  const toggle = (id: string) =>
    setCollapsed((current) => {
      const next = new Set(current)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })

  return (
    <nav aria-label={t('library.tree.label')} className="flex flex-col gap-4" data-testid="folder-tree">
      <ul className="flex flex-col gap-0.5">
        <ShortcutLink
          to={hrefFor({ scope: 'RECENT' })}
          active={query.scope === 'RECENT'}
          icon={<Clock className="size-4" />}
          label={t('library.shortcuts.recent')}
          onNavigate={onNavigate}
        />
        <ShortcutLink
          to={hrefFor({ scope: 'STARRED' })}
          active={query.scope === 'STARRED'}
          icon={<Star className="size-4" />}
          label={t('library.shortcuts.starred')}
          count={tree.starredCount}
          onNavigate={onNavigate}
        />
        <ShortcutLink
          to={hrefFor({ scope: 'ALL' })}
          active={query.scope === 'ALL'}
          icon={<Library className="size-4" />}
          label={t('library.shortcuts.all')}
          count={tree.totalDocumentCount}
          onNavigate={onNavigate}
        />
      </ul>

      <div className="flex flex-col gap-1">
        <div className="flex items-center justify-between gap-2 pl-3">
          <h2 className="text-xs font-semibold uppercase tracking-wide text-muted">{t('library.tree.folders')}</h2>
          <IconButton
            label={t('library.tree.newFolder')}
            title={t('library.tree.newFolder')}
            icon={<FolderPlus className="size-4" />}
            onClick={() => onCreateFolder(null)}
            className="size-9"
            data-testid="new-folder"
          />
        </div>
        <ul className="flex flex-col gap-0.5" data-testid="folder-list">
          <FolderRow
            {...props}
            id={null}
            name={t('library.shortcuts.root')}
            count={tree.rootDocumentCount}
            depth={0}
            icon={<Home className="size-4" />}
            active={query.scope === 'FOLDER' && !query.folderId}
          />
          {roots.map((node) => (
            <FolderBranch key={node.id} node={node} collapsed={collapsed} onToggle={toggle} {...props} />
          ))}
        </ul>
        {roots.length === 0 && <p className="px-3 py-2 text-[0.8125rem] leading-relaxed text-muted">{t('library.tree.empty')}</p>}
      </div>
    </nav>
  )
}

function ShortcutLink({
  to,
  active,
  icon,
  label,
  count,
  onNavigate,
}: {
  to: string
  active: boolean
  icon: ReactNode
  label: string
  count?: number
  onNavigate?: () => void
}) {
  return (
    <li>
      <Link
        to={to}
        onClick={onNavigate}
        aria-current={active ? 'page' : undefined}
        className={cn(
          'flex min-h-11 items-center gap-3 rounded-xl px-3 text-[0.9375rem]',
          active ? 'bg-ink font-medium text-on-ink' : 'text-ink hover:bg-ink/5',
        )}
      >
        <span aria-hidden="true">{icon}</span>
        <span className="flex-1 truncate">{label}</span>
        {count !== undefined && <span className="text-xs tabular-nums opacity-75">{count}</span>}
      </Link>
    </li>
  )
}

function FolderBranch({
  node,
  collapsed,
  onToggle,
  ...props
}: FolderTreeProps & { node: FolderNode; collapsed: Set<string>; onToggle: (id: string) => void }) {
  const open = !collapsed.has(node.id)
  return (
    <>
      <FolderRow
        {...props}
        id={node.id}
        node={node}
        name={node.name}
        count={node.totalCount}
        depth={node.depth + 1}
        icon={<FolderIcon className="size-4" />}
        active={props.query.scope === 'FOLDER' && props.query.folderId === node.id}
        expanded={node.children.length ? open : undefined}
        onToggle={() => onToggle(node.id)}
      />
      {open &&
        node.children.map((child) => (
          <FolderBranch key={child.id} node={child} collapsed={collapsed} onToggle={onToggle} {...props} />
        ))}
    </>
  )
}

function FolderRow({
  id,
  node,
  name,
  count,
  depth,
  icon,
  active,
  expanded,
  onToggle,
  tree,
  hrefFor,
  onNavigate,
  onCreateFolder,
  onRenameFolder,
  onMoveFolder,
  onDeleteFolder,
  onDropDocuments,
  onDropFolder,
}: FolderTreeProps & {
  id: string | null
  node?: FolderNode
  name: string
  count: number
  depth: number
  icon: ReactNode
  active: boolean
  expanded?: boolean
  onToggle?: () => void
}) {
  const { t } = useTranslation()
  const [over, setOver] = useState(false)

  const accepts = (event: DragEvent) => {
    const types = Array.from(event.dataTransfer.types)
    if (types.includes(DRAG_DOCUMENTS)) return true
    // Thư mục chỉ thả được vào chỗ không phải chính nó hay con cháu của nó; lúc kéo chưa đọc được dữ liệu nên
    // kiểm lại khi thả.
    return types.includes(DRAG_FOLDER)
  }

  const onDrop = (event: DragEvent) => {
    event.preventDefault()
    setOver(false)
    const documents = readDraggedDocuments(event.dataTransfer)
    if (documents.length) {
      onDropDocuments(documents, id, name)
      return
    }
    const folderId = event.dataTransfer.getData(DRAG_FOLDER)
    if (folderId && folderId !== id && !(id && descendantIds(tree.folders, folderId).has(id))) {
      onDropFolder(folderId, id, name)
    }
  }

  return (
    <li
      data-testid="folder-row"
      data-folder-name={name}
      onDragOver={(event) => {
        if (!accepts(event)) return
        event.preventDefault()
        event.dataTransfer.dropEffect = 'move'
        setOver(true)
      }}
      onDragLeave={() => setOver(false)}
      onDrop={onDrop}
      className={cn('group relative flex items-center rounded-xl', over && 'bg-sun ring-2 ring-ink')}
      style={{ paddingLeft: `${Math.max(0, depth - 1) * 0.875}rem` }}
    >
      {expanded !== undefined ? (
        <button
          type="button"
          onClick={onToggle}
          aria-expanded={expanded}
          aria-label={expanded ? t('library.tree.collapse', { name }) : t('library.tree.expand', { name })}
          className="grid size-8 shrink-0 place-items-center rounded-lg text-muted hover:bg-ink/5 hover:text-ink"
        >
          <ChevronRight className={cn('size-4 transition-transform', expanded && 'rotate-90')} aria-hidden="true" />
        </button>
      ) : (
        <span className="w-8 shrink-0" aria-hidden="true" />
      )}
      <Link
        to={hrefFor({ scope: 'FOLDER', folderId: id })}
        onClick={onNavigate}
        aria-current={active ? 'page' : undefined}
        draggable={node ? true : undefined}
        onDragStart={(event) => {
          if (!node) return
          event.dataTransfer.setData(DRAG_FOLDER, node.id)
          event.dataTransfer.effectAllowed = 'move'
        }}
        className={cn(
          'flex min-h-11 min-w-0 flex-1 items-center gap-2.5 rounded-xl px-2 text-[0.9375rem]',
          active ? 'bg-ink font-medium text-on-ink' : 'text-ink hover:bg-ink/5',
        )}
      >
        <span aria-hidden="true" className="shrink-0">
          {icon}
        </span>
        <span className="min-w-0 flex-1 truncate" title={name}>
          {name}
        </span>
        <span className="text-xs tabular-nums opacity-75" aria-hidden="true">
          {count}
        </span>
        <span className="sr-only">({t('library.tree.count', { count })})</span>
      </Link>
      {node && (
        <FolderContextMenu
          label={t('library.tree.actions', { name })}
          className="shrink-0"
          actions={[
            {
              id: 'new',
              label: t('library.folderActions.newSubfolder'),
              icon: <FolderPlus className="size-4" aria-hidden="true" />,
              onSelect: () => onCreateFolder(node),
            },
            {
              id: 'rename',
              label: t('library.folderActions.rename'),
              icon: <Pencil className="size-4" aria-hidden="true" />,
              onSelect: () => onRenameFolder(node),
            },
            {
              id: 'move',
              label: t('library.folderActions.move'),
              icon: <FolderInput className="size-4" aria-hidden="true" />,
              onSelect: () => onMoveFolder(node),
            },
            {
              id: 'delete',
              label: t('library.folderActions.delete'),
              icon: <Trash2 className="size-4" aria-hidden="true" />,
              danger: true,
              onSelect: () => onDeleteFolder(node),
            },
          ]}
        />
      )}
    </li>
  )
}
