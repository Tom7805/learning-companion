import { Download, FolderInput, Pencil, Pin, PinOff, Trash2 } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { FolderContextMenu, type MenuAction } from '@/features/library/components/FolderContextMenu'
import { useWorkspaces } from '@/features/workspaces/api/queries'
import { toApiError } from '@/shared/api/errors'
import { saveBlob } from '@/shared/lib/download'
import { Button, ConfirmDialog, Dialog, TextField, toast } from '@/shared/ui'
import { assistantApi } from '../api/assistantApi'
import { useConversationActions } from '../api/queries'
import type { ConversationSummary } from '../types'

const TITLE_MAX = 120

type Open = 'rename' | 'move' | 'delete' | null

/**
 * Thao tác với một hội thoại trong lịch sử (NCL-07-CN-003): đổi tên, ghim lên đầu, chuyển vào không gian học, tải về
 * dạng Markdown kèm nguồn, xóa vào thùng rác.
 */
export function ConversationMenu({
  conversation,
  onDeleted,
  onMoved,
}: {
  conversation: ConversationSummary
  onDeleted?: () => void
  onMoved?: (workspaceId: string) => void
}) {
  const { t } = useTranslation()
  const [open, setOpen] = useState<Open>(null)
  const { update, move, remove } = useConversationActions()

  const failed = (error: unknown) => {
    const { code } = toApiError(error)
    toast.error(t(`errors.${code}`, { defaultValue: t('assistant.history.failed') }))
  }

  const download = async () => {
    try {
      const file = await assistantApi.exportConversation(conversation.id)
      saveBlob(file.blob, file.fileName)
    } catch (error) {
      const { code } = toApiError(error)
      toast.error(t(`errors.${code}`, { defaultValue: t('assistant.history.downloadFailed') }))
    }
  }

  const actions: MenuAction[] = [
    { id: 'rename', label: t('assistant.history.rename'), icon: <Pencil className="size-4" aria-hidden="true" />, onSelect: () => setOpen('rename') },
    {
      id: 'pin',
      label: conversation.pinned ? t('assistant.history.unpin') : t('assistant.history.pin'),
      icon: conversation.pinned ? <PinOff className="size-4" aria-hidden="true" /> : <Pin className="size-4" aria-hidden="true" />,
      onSelect: () =>
        update.mutate(
          { id: conversation.id, pinned: !conversation.pinned },
          {
            onSuccess: () => toast.success(conversation.pinned ? t('assistant.history.unpinned') : t('assistant.history.pinned')),
            onError: failed,
          },
        ),
    },
    { id: 'move', label: t('assistant.history.move'), icon: <FolderInput className="size-4" aria-hidden="true" />, onSelect: () => setOpen('move') },
    { id: 'download', label: t('assistant.history.download'), icon: <Download className="size-4" aria-hidden="true" />, onSelect: () => void download() },
    { id: 'delete', label: t('assistant.history.delete'), icon: <Trash2 className="size-4" aria-hidden="true" />, danger: true, onSelect: () => setOpen('delete') },
  ]

  return (
    <>
      <FolderContextMenu
        label={t('assistant.history.menu', { title: conversation.title })}
        actions={actions}
        tone="night"
        floating
        className="shrink-0"
      />
      {open === 'rename' && (
        <RenameDialog
          title={conversation.title}
          busy={update.isPending}
          onClose={() => setOpen(null)}
          onSubmit={(title) =>
            update.mutate(
              { id: conversation.id, title },
              {
                onSuccess: () => {
                  setOpen(null)
                  toast.success(t('assistant.history.renamed'))
                },
                onError: failed,
              },
            )
          }
        />
      )}
      {open === 'move' && (
        <MoveDialog
          title={conversation.title}
          busy={move.isPending}
          onClose={() => setOpen(null)}
          onSubmit={(workspace) =>
            move.mutate(
              { id: conversation.id, workspaceId: workspace.id },
              {
                onSuccess: () => {
                  setOpen(null)
                  toast.success(t('assistant.history.moved', { name: workspace.name }))
                  onMoved?.(workspace.id)
                },
                onError: failed,
              },
            )
          }
        />
      )}
      <ConfirmDialog
        open={open === 'delete'}
        title={t('assistant.history.deleteDialog.title', { title: conversation.title })}
        description={t('assistant.history.deleteDialog.body')}
        confirmLabel={t('assistant.history.deleteDialog.confirm')}
        cancelLabel={t('common.cancel')}
        loading={remove.isPending}
        onCancel={() => setOpen(null)}
        onConfirm={() =>
          remove.mutate(conversation.id, {
            onSuccess: () => {
              setOpen(null)
              toast.success(t('assistant.history.deleted'))
              onDeleted?.()
            },
            onError: failed,
          })
        }
      />
    </>
  )
}

function RenameDialog({
  title,
  busy,
  onSubmit,
  onClose,
}: {
  title: string
  busy: boolean
  onSubmit: (title: string) => void
  onClose: () => void
}) {
  const { t } = useTranslation()
  const [value, setValue] = useState(title)
  const [touched, setTouched] = useState(false)
  const trimmed = value.trim().replace(/\s+/g, ' ')
  const error = !trimmed
    ? t('assistant.history.renameDialog.required')
    : trimmed.length > TITLE_MAX
      ? t('assistant.history.renameDialog.tooLong')
      : undefined
  const submit = (event: FormEvent) => {
    event.preventDefault()
    setTouched(true)
    if (!error) onSubmit(trimmed)
  }
  return (
    <Dialog open title={t('assistant.history.renameDialog.title')} onClose={onClose}>
      <form className="flex flex-col gap-4" onSubmit={submit} noValidate>
        <TextField
          label={t('assistant.history.renameDialog.label')}
          value={value}
          onChange={(event) => setValue(event.target.value)}
          error={touched ? error : undefined}
          autoFocus
          data-testid="conversation-title-input"
        />
        <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
          <Button type="button" variant="outline" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" variant="accent" loading={busy} data-testid="conversation-rename-save">
            {t('assistant.history.renameDialog.save')}
          </Button>
        </div>
      </form>
    </Dialog>
  )
}

function MoveDialog({
  title,
  busy,
  onSubmit,
  onClose,
}: {
  title: string
  busy: boolean
  onSubmit: (workspace: { id: string; name: string }) => void
  onClose: () => void
}) {
  const { t } = useTranslation()
  const workspaces = useWorkspaces()
  const list = workspaces.data ?? []
  const [chosen, setChosen] = useState('')
  const target = list.find((workspace) => workspace.id === chosen) ?? list[0]
  return (
    <Dialog
      open
      title={t('assistant.history.moveDialog.title', { title })}
      description={t('assistant.history.moveDialog.body')}
      onClose={onClose}
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button
            variant="accent"
            loading={busy}
            disabled={!target}
            onClick={() => target && onSubmit({ id: target.id, name: target.name })}
            data-testid="conversation-move-confirm"
          >
            {t('assistant.history.moveDialog.confirm')}
          </Button>
        </>
      }
    >
      {workspaces.isSuccess && list.length === 0 ? (
        <p className="text-sm text-muted">{t('assistant.history.moveDialog.empty')}</p>
      ) : (
        <label className="flex flex-col gap-1.5 text-sm font-medium">
          {t('assistant.history.moveDialog.label')}
          <select
            value={target?.id ?? ''}
            onChange={(event) => setChosen(event.target.value)}
            className="min-h-11 rounded-field border-[1.5px] border-ink bg-field px-3 text-[0.9375rem]"
            data-testid="conversation-move-target"
          >
            {list.map((workspace) => (
              <option key={workspace.id} value={workspace.id}>
                {workspace.name}
              </option>
            ))}
          </select>
        </label>
      )}
    </Dialog>
  )
}
