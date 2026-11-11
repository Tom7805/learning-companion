import {
  ArrowDown,
  ArrowUp,
  ChevronDown,
  ChevronRight,
  CornerDownRight,
  CornerUpLeft,
  FileText,
  GitMerge,
  ListTree,
  Pencil,
  Plus,
  Scissors,
  Sparkles,
  Trash2,
} from 'lucide-react'
import { useState, type DragEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { FolderContextMenu, type MenuAction } from '@/features/library/components/FolderContextMenu'
import { locationLabel } from '@/features/assistant/lib/citations'
import type { ViewerLocation } from '@/features/library/hooks/useDocumentViewer'
import { toApiError } from '@/shared/api/errors'
import { cn } from '@/shared/lib/cn'
import { AiLabel, Alert, Button, ConfirmDialog, EmptyState, Skeleton, toast } from '@/shared/ui'
import { useTopicMutation, useTopics } from '../api/queries'
import { workspacesApi } from '../api/workspacesApi'
import { flattenTopics, isWithinTopic, MAX_TOPIC_DEPTH, countTopics, type PlacedTopic } from '../lib/topics'
import type { TopicNode, TopicTree, Workspace } from '../types'
import { MergeTopicDialog, SplitTopicDialog, TopicEditor } from './TopicEditor'

type Dialog =
  | { kind: 'create'; parent: TopicNode | null }
  | { kind: 'edit'; topic: TopicNode }
  | { kind: 'merge'; topic: TopicNode }
  | { kind: 'split'; topic: TopicNode }
  | { kind: 'delete'; topic: TopicNode }

/**
 * Thẻ "Dàn ý" của không gian học (NCL-06-CN-002): trợ lý đề xuất cây chủ đề tối đa ba cấp kèm mục tiêu học và trích
 * dẫn (tốn lượt), hoặc người học tự lập dàn ý (không tốn lượt). Mỗi chủ đề hiện số nguồn liên quan, nhãn "Do AI sinh"
 * khi do trợ lý đề xuất (kèm "Đã chỉnh sửa" sau khi sửa tay), và sửa, thêm, xóa, sắp xếp, gộp, tách được qua trình đơn
 * hoặc kéo thả. Thao tác vượt ba cấp bị máy chủ từ chối, cây giữ nguyên chỗ cũ.
 */
export function TopicOutline({
  workspace,
  onOpenSource,
}: {
  workspace: Workspace
  onOpenSource: (documentId: string, location: ViewerLocation) => void
}) {
  const { t } = useTranslation()
  const topics = useTopics(workspace.id)
  const [dialog, setDialog] = useState<Dialog | null>(null)
  const [dragging, setDragging] = useState<string | null>(null)

  const failed = (error: unknown) => {
    const { code } = toApiError(error)
    toast.error(t(`errors.${code}`, { defaultValue: t('workspaces.actionFailed') }))
  }
  const run = useTopicMutation<() => Promise<TopicTree>>(workspace.id, (action) => action())
  const act = (action: () => Promise<TopicTree>, onDone?: (tree: TopicTree) => void) =>
    run.mutate(action, {
      onSuccess: (tree) => {
        setDialog(null)
        onDone?.(tree)
      },
      onError: failed,
    })

  const suggest = () =>
    act(
      () => workspacesApi.suggestTopics(workspace.id),
      (tree) => {
        if (tree.safety) toast.error(t('workspaces.outline.blocked'))
        else if (tree.added) toast.success(t('workspaces.outline.added', { count: tree.added }))
        else toast.info(t('workspaces.outline.addedNone'))
      },
    )

  if (topics.isPending) return <Skeleton className="h-56" />
  if (topics.isError || !topics.data) {
    return (
      <Alert tone="danger">
        <p>{t('workspaces.loadFailed')}</p>
        <Button variant="outline" className="mt-3" onClick={() => void topics.refetch()}>
          {t('common.retry')}
        </Button>
      </Alert>
    )
  }

  const tree = topics.data
  const placed = flattenTopics(tree.topics)
  const canSuggest = tree.readySources > 0
  const suggesting = run.isPending && run.variables !== undefined && dialog === null

  const move = (topic: TopicNode, parentId: string | null, index: number) =>
    act(() => workspacesApi.moveTopic(workspace.id, topic.id, parentId, index))

  const actionsFor = ({ node, depth, parent, siblings, index }: PlacedTopic): MenuAction[] => {
    const above = siblings[index - 1]
    const grandparent = parent ? placed.find((item) => item.node.id === parent.id) : undefined
    const list: MenuAction[] = [
      { id: 'edit', label: t('workspaces.outline.edit'), icon: <Pencil className="size-4" aria-hidden="true" />, onSelect: () => setDialog({ kind: 'edit', topic: node }) },
    ]
    if (depth < MAX_TOPIC_DEPTH) {
      list.push({ id: 'child', label: t('workspaces.outline.addChild'), icon: <Plus className="size-4" aria-hidden="true" />, onSelect: () => setDialog({ kind: 'create', parent: node }) })
    }
    if (index > 0) {
      list.push({ id: 'up', label: t('workspaces.outline.moveUp'), icon: <ArrowUp className="size-4" aria-hidden="true" />, onSelect: () => move(node, parent?.id ?? null, index - 1) })
    }
    if (index < siblings.length - 1) {
      list.push({ id: 'down', label: t('workspaces.outline.moveDown'), icon: <ArrowDown className="size-4" aria-hidden="true" />, onSelect: () => move(node, parent?.id ?? null, index + 1) })
    }
    if (above) {
      list.push({ id: 'indent', label: t('workspaces.outline.indent'), icon: <CornerDownRight className="size-4" aria-hidden="true" />, onSelect: () => move(node, above.id, above.children.length) })
    }
    if (parent && grandparent) {
      list.push({ id: 'outdent', label: t('workspaces.outline.outdent'), icon: <CornerUpLeft className="size-4" aria-hidden="true" />, onSelect: () => move(node, grandparent.parent?.id ?? null, grandparent.index + 1) })
    }
    if (placed.length > 1) {
      list.push({ id: 'merge', label: t('workspaces.outline.merge'), icon: <GitMerge className="size-4" aria-hidden="true" />, onSelect: () => setDialog({ kind: 'merge', topic: node }) })
    }
    list.push(
      { id: 'split', label: t('workspaces.outline.split'), icon: <Scissors className="size-4" aria-hidden="true" />, onSelect: () => setDialog({ kind: 'split', topic: node }) },
      { id: 'delete', label: t('workspaces.outline.delete'), icon: <Trash2 className="size-4" aria-hidden="true" />, danger: true, onSelect: () => setDialog({ kind: 'delete', topic: node }) },
    )
    return list
  }

  const drop = (target: TopicNode) => {
    const moving = placed.find((item) => item.node.id === dragging)
    setDragging(null)
    if (!moving || isWithinTopic(moving.node, target.id)) return
    move(moving.node, target.id, target.children.length)
  }

  return (
    <section className="flex flex-col gap-5" aria-labelledby="outline-heading" data-testid="topic-outline">
      <header className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="flex min-w-0 flex-col gap-1">
          <h2 id="outline-heading" className="flex items-center gap-2 text-lg font-semibold">
            <ListTree className="size-5" aria-hidden="true" />
            {t('workspaces.outline.title')}
            <span className="text-sm font-normal text-muted">{t('workspaces.outline.count', { count: countTopics(tree.topics) })}</span>
          </h2>
          <p className="text-sm text-ink-soft">{canSuggest ? t('workspaces.outline.suggestHint') : t('workspaces.outline.needSources')}</p>
        </div>
        <div className="flex shrink-0 flex-wrap gap-2">
          <Button
            variant="outline"
            onClick={() => setDialog({ kind: 'create', parent: null })}
            icon={<Plus className="size-4" aria-hidden="true" />}
            data-testid="topic-add"
          >
            {t('workspaces.outline.add')}
          </Button>
          <Button
            variant="accent"
            onClick={suggest}
            disabled={!canSuggest}
            loading={suggesting}
            icon={<Sparkles className="size-4" aria-hidden="true" />}
            aria-describedby="outline-cost"
            data-testid="topic-suggest"
          >
            {suggesting ? t('workspaces.outline.suggesting') : tree.topics.length > 0 ? t('workspaces.outline.suggestAgain') : t('workspaces.outline.suggest')}
          </Button>
          <span id="outline-cost" className="sr-only">
            {t('workspaces.outline.cost')}
          </span>
        </div>
      </header>

      {tree.topics.length === 0 ? (
        <EmptyState
          icon={<ListTree className="size-6" aria-hidden="true" />}
          title={t('workspaces.outline.empty.title')}
          description={t('workspaces.outline.empty.body')}
        />
      ) : (
        <TopicList
          nodes={tree.topics}
          depth={1}
          placed={placed}
          dragging={dragging}
          onDragStart={setDragging}
          onDragEnd={() => setDragging(null)}
          onDrop={drop}
          actionsFor={actionsFor}
          onOpenSource={onOpenSource}
        />
      )}

      {(dialog?.kind === 'create' || dialog?.kind === 'edit') && (
        <TopicEditor
          open
          topic={dialog.kind === 'edit' ? dialog.topic : null}
          parent={dialog.kind === 'create' ? dialog.parent : null}
          busy={run.isPending}
          onClose={() => setDialog(null)}
          onSubmit={(input) =>
            act(() =>
              dialog.kind === 'edit'
                ? workspacesApi.updateTopic(workspace.id, dialog.topic.id, input)
                : workspacesApi.addTopic(workspace.id, { ...input, parentId: dialog.parent?.id ?? null }),
            )
          }
        />
      )}
      {dialog?.kind === 'merge' && (
        <MergeTopicDialog
          topic={dialog.topic}
          candidates={placed.filter((item) => !isWithinTopic(dialog.topic, item.node.id))}
          busy={run.isPending}
          onClose={() => setDialog(null)}
          onSubmit={(intoId) => act(() => workspacesApi.mergeTopic(workspace.id, dialog.topic.id, intoId))}
        />
      )}
      {dialog?.kind === 'split' && (
        <SplitTopicDialog
          topic={dialog.topic}
          busy={run.isPending}
          onClose={() => setDialog(null)}
          onSubmit={({ title, childIds }) => act(() => workspacesApi.splitTopic(workspace.id, dialog.topic.id, title, childIds))}
        />
      )}
      <ConfirmDialog
        open={dialog?.kind === 'delete'}
        title={dialog?.kind === 'delete' ? t('workspaces.outline.deleteConfirm.title', { title: dialog.topic.title }) : ''}
        description={t('workspaces.outline.deleteConfirm.body')}
        confirmLabel={t('workspaces.outline.deleteConfirm.confirm')}
        cancelLabel={t('common.cancel')}
        loading={run.isPending}
        onCancel={() => setDialog(null)}
        onConfirm={() => dialog?.kind === 'delete' && act(() => workspacesApi.deleteTopic(workspace.id, dialog.topic.id))}
      />
    </section>
  )
}

function TopicList({
  nodes,
  depth,
  ...rest
}: {
  nodes: TopicNode[]
  depth: number
  placed: PlacedTopic[]
  dragging: string | null
  onDragStart: (id: string) => void
  onDragEnd: () => void
  onDrop: (target: TopicNode) => void
  actionsFor: (placed: PlacedTopic) => MenuAction[]
  onOpenSource: (documentId: string, location: ViewerLocation) => void
}) {
  return (
    <ol className={cn('flex flex-col gap-2', depth > 1 && 'mt-2 border-l-2 border-line pl-3 sm:pl-5')}>
      {nodes.map((node) => (
        <TopicItem key={node.id} node={node} depth={depth} {...rest} />
      ))}
    </ol>
  )
}

function TopicItem({
  node,
  depth,
  placed,
  dragging,
  onDragStart,
  onDragEnd,
  onDrop,
  actionsFor,
  onOpenSource,
}: {
  node: TopicNode
  depth: number
  placed: PlacedTopic[]
  dragging: string | null
  onDragStart: (id: string) => void
  onDragEnd: () => void
  onDrop: (target: TopicNode) => void
  actionsFor: (placed: PlacedTopic) => MenuAction[]
  onOpenSource: (documentId: string, location: ViewerLocation) => void
}) {
  const { t } = useTranslation()
  const [showSources, setShowSources] = useState(false)
  const [over, setOver] = useState(false)
  const place = placed.find((item) => item.node.id === node.id)!
  const sourcesId = `topic-sources-${node.id}`

  const allowDrop = (event: DragEvent) => {
    if (!dragging || dragging === node.id) return
    event.preventDefault()
    setOver(true)
  }

  return (
    <li data-testid="topic" data-depth={depth} data-topic-id={node.id}>
      <div
        draggable
        onDragStart={(event) => {
          event.dataTransfer.effectAllowed = 'move'
          event.dataTransfer.setData('text/plain', node.id)
          onDragStart(node.id)
        }}
        onDragEnd={onDragEnd}
        onDragOver={allowDrop}
        onDragLeave={() => setOver(false)}
        onDrop={(event) => {
          event.preventDefault()
          setOver(false)
          onDrop(node)
        }}
        aria-roledescription={t('workspaces.outline.drag', { title: node.title })}
        className={cn(
          'flex items-start gap-3 rounded-card border-[1.5px] bg-surface p-3 sm:p-4',
          over ? 'border-accent bg-sun-soft' : 'border-line',
          dragging === node.id && 'opacity-60',
        )}
        data-testid="topic-card"
      >
        <div className="flex min-w-0 flex-1 flex-col gap-1.5">
          <div className="flex flex-wrap items-center gap-2">
            <span className="sr-only">{t('workspaces.outline.level', { level: depth })}</span>
            <span className={cn('font-semibold [overflow-wrap:anywhere]', depth === 1 ? 'text-base' : 'text-[0.9375rem]')} data-testid="topic-title">
              {node.title}
            </span>
            {node.aiGenerated && <AiLabel edited={node.edited} />}
          </div>
          <p className={cn('text-sm [overflow-wrap:anywhere]', node.goal ? 'text-ink-soft' : 'italic text-muted')} data-testid="topic-goal">
            {node.goal || t('workspaces.outline.noGoal')}
          </p>
          {node.sourceCount > 0 && (
            <button
              type="button"
              onClick={() => setShowSources((value) => !value)}
              aria-expanded={showSources}
              aria-controls={sourcesId}
              aria-label={`${t('workspaces.outline.showSources', { title: node.title })}: ${t('workspaces.outline.sources', { count: node.sourceCount })}`}
              className="inline-flex min-h-9 items-center gap-1 self-start rounded-lg px-2 text-[0.8125rem] font-medium text-ink-soft hover:bg-ink/5"
              data-testid="topic-source-count"
            >
              {showSources ? <ChevronDown className="size-4" aria-hidden="true" /> : <ChevronRight className="size-4" aria-hidden="true" />}
              {t('workspaces.outline.sources', { count: node.sourceCount })}
            </button>
          )}
          {showSources && (
            <ul id={sourcesId} className="flex flex-col gap-1" data-testid="topic-sources">
              {node.sources.map((source, index) => (
                <li key={`${source.documentId ?? source.documentTitle}-${index}`}>
                  <button
                    type="button"
                    disabled={!source.available || !source.documentId}
                    onClick={() =>
                      source.documentId &&
                      onOpenSource(source.documentId, {
                        kind: source.location.kind,
                        number: source.location.number,
                        passage: source.passage,
                      })
                    }
                    className="flex w-full items-start gap-2 rounded-lg px-2 py-1.5 text-left text-[0.8125rem] hover:bg-ink/5 disabled:cursor-not-allowed disabled:hover:bg-transparent"
                    data-testid="topic-source"
                  >
                    <FileText className="mt-0.5 size-4 shrink-0 text-muted" aria-hidden="true" />
                    <span className="min-w-0 flex-1 [overflow-wrap:anywhere]">
                      <span className={cn('font-medium', !source.available && 'text-muted line-through')}>{source.documentTitle}</span>
                      <span className="text-muted">
                        {' · '}
                        {source.available ? locationLabel(source, t) : t('workspaces.outline.sourceGone')}
                      </span>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
        <FolderContextMenu label={t('workspaces.outline.actions', { title: node.title })} actions={actionsFor(place)} />
      </div>
      {node.children.length > 0 && (
        <TopicList
          nodes={node.children}
          depth={depth + 1}
          placed={placed}
          dragging={dragging}
          onDragStart={onDragStart}
          onDragEnd={onDragEnd}
          onDrop={onDrop}
          actionsFor={actionsFor}
          onOpenSource={onOpenSource}
        />
      )}
    </li>
  )
}
