import { useEffect, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { toApiError } from '@/shared/api/errors'
import { Button, Dialog, TextField, toast } from '@/shared/ui'
import { libraryApi } from '../api/libraryApi'
import { useLibraryMutation } from '../api/queries'
import type { Folder } from '../types'

const MAX = 120
const CHECK_DELAY_MS = 250

interface FolderNameDialogProps {
  open: boolean
  onClose: () => void
  /** Tạo thư mục mới trong {@code parent} (trống: thư mục gốc), hoặc đổi tên {@code folder}. */
  mode: 'create' | 'rename'
  parent?: Pick<Folder, 'id' | 'name'> | null
  folder?: Pick<Folder, 'id' | 'name' | 'parentId'> | null
  onDone?: (folder: Folder) => void
}

/**
 * Tạo hoặc đổi tên thư mục. Tên trùng trong cùng thư mục cha được báo ngay khi đang gõ, trước khi lưu, kèm tên gợi ý
 * bấm một lần là dùng (NCL-05-CN-001 TC-03); máy chủ vẫn kiểm lại khi lưu.
 */
export function FolderNameDialog({ open, onClose, mode, parent, folder, onDone }: FolderNameDialogProps) {
  const { t } = useTranslation()
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={mode === 'create' ? t('library.folderDialog.createTitle') : t('library.folderDialog.renameTitle')}
      description={
        mode === 'create'
          ? t('library.folderDialog.createIn', { name: parent?.name ?? t('library.shortcuts.root') })
          : undefined
      }
    >
      <FolderNameForm
        mode={mode}
        parentId={mode === 'create' ? (parent?.id ?? null) : (folder?.parentId ?? null)}
        folder={folder}
        onCancel={onClose}
        onDone={(saved) => {
          onDone?.(saved)
          onClose()
        }}
      />
    </Dialog>
  )
}

function FolderNameForm({
  mode,
  parentId,
  folder,
  onCancel,
  onDone,
}: {
  mode: 'create' | 'rename'
  parentId: string | null
  folder?: Pick<Folder, 'id' | 'name'> | null
  onCancel: () => void
  onDone: (folder: Folder) => void
}) {
  const { t } = useTranslation()
  const [name, setName] = useState(mode === 'rename' ? (folder?.name ?? '') : '')
  const [check, setCheck] = useState<{ name: string; available: boolean; suggestion?: string | null } | null>(null)
  const [error, setError] = useState<string | null>(null)
  const save = useLibraryMutation((value: string) =>
    mode === 'create' ? libraryApi.createFolder(value, parentId) : libraryApi.renameFolder(folder!.id, value),
  )
  const trimmed = name.trim().replace(/\s+/g, ' ')
  const unchanged = mode === 'rename' && trimmed === folder?.name

  useEffect(() => {
    if (!trimmed || trimmed.length > MAX || unchanged) return
    let cancelled = false
    const timer = window.setTimeout(() => {
      libraryApi
        .checkFolderName(trimmed, parentId, mode === 'rename' ? folder?.id : undefined)
        .then((result) => {
          if (!cancelled) setCheck({ name: trimmed, ...result })
        })
        .catch(() => {
          // Không kiểm tra trước được thì vẫn cho lưu: máy chủ kiểm lại và báo trùng khi lưu.
        })
    }, CHECK_DELAY_MS)
    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [trimmed, parentId, mode, folder?.id, unchanged])

  const current = check?.name === trimmed ? check : null
  const taken = current && !current.available
  const fieldError =
    error ??
    (trimmed.length > MAX
      ? t('library.folderDialog.tooLong')
      : taken
        ? t('library.folderDialog.taken', { name: trimmed })
        : undefined)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (!trimmed) {
      setError(t('library.folderDialog.required'))
      return
    }
    if (unchanged) {
      onCancel()
      return
    }
    try {
      const saved = await save.mutateAsync(trimmed)
      toast.success(
        mode === 'create'
          ? t('library.folderDialog.created', { name: saved.name })
          : t('library.folderDialog.renamed', { name: saved.name }),
      )
      onDone(saved)
    } catch (caught) {
      const apiError = toApiError(caught)
      if (apiError.code === 'LIBRARY_NAME_TAKEN') {
        const suggestion = typeof apiError.details?.suggestion === 'string' ? apiError.details.suggestion : null
        setCheck({ name: trimmed, available: false, suggestion })
      } else {
        setError(t(`errors.${apiError.code}`, { defaultValue: t('library.actionFailed') }))
      }
    }
  }

  return (
    <form noValidate onSubmit={(event) => void submit(event)} className="flex flex-col gap-5">
      <div className="flex flex-col gap-2">
        <TextField
          label={t('library.folderDialog.name')}
          value={name}
          maxLength={MAX + 20}
          autoFocus
          autoComplete="off"
          onChange={(event) => {
            setName(event.target.value)
            setError(null)
          }}
          error={fieldError}
          hint={fieldError ? undefined : t('library.folderDialog.hint')}
          valid={Boolean(current?.available && !unchanged)}
          validLabel={t('library.folderDialog.available')}
        />
        {taken && current?.suggestion && (
          <Button
            variant="ghost"
            className="self-start underline underline-offset-4"
            onClick={() => {
              setName(current.suggestion!)
              setError(null)
            }}
          >
            {t('library.folderDialog.useSuggestion', { name: current.suggestion })}
          </Button>
        )}
      </div>
      <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <Button variant="outline" onClick={onCancel}>
          {t('common.cancel')}
        </Button>
        <Button type="submit" variant="accent" loading={save.isPending} disabled={Boolean(taken)}>
          {mode === 'create' ? t('library.folderDialog.create') : t('library.folderDialog.save')}
        </Button>
      </div>
    </form>
  )
}
