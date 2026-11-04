import { Folder as FolderIcon, Home } from 'lucide-react'
import { useId, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/cn'
import { Button, Dialog } from '@/shared/ui'
import { buildTree, flatten } from '../lib/tree'
import type { Folder } from '../types'

interface MoveDialogProps {
  open: boolean
  title: string
  folders: Folder[]
  /** Thư mục đang chứa các mục cần chuyển; trống: thư mục gốc. Không có giá trị: nhiều chỗ khác nhau. */
  currentFolderId?: string | null
  /** Thư mục không được chọn làm đích (chính thư mục đang chuyển và con cháu của nó). */
  excluded?: Set<string>
  /** Ẩn lựa chọn "Thư mục gốc", ví dụ khi thư mục đang chuyển đã ở gốc. */
  loading?: boolean
  error?: string | null
  onClose: () => void
  onMove: (folderId: string | null, folderName: string) => void
}

/**
 * Chọn thư mục đích bằng danh sách nút chọn: cách chuyển không cần kéo thả, dùng được bằng bàn phím, trình đọc màn
 * hình và trên điện thoại (NCL-05-CN-001).
 */
export function MoveDialog({
  open,
  title,
  folders,
  currentFolderId,
  excluded,
  loading,
  error,
  onClose,
  onMove,
}: MoveDialogProps) {
  const { t } = useTranslation()
  return (
    <Dialog open={open} onClose={onClose} title={title} description={t('library.moveDialog.destination')}>
      <MoveForm
        folders={folders}
        currentFolderId={currentFolderId}
        excluded={excluded}
        loading={loading}
        error={error}
        onCancel={onClose}
        onMove={onMove}
      />
    </Dialog>
  )
}

const ROOT = '__root__'

function MoveForm({
  folders,
  currentFolderId,
  excluded,
  loading,
  error,
  onCancel,
  onMove,
}: Omit<MoveDialogProps, 'open' | 'title' | 'onClose'> & { onCancel: () => void }) {
  const { t } = useTranslation()
  const name = useId()
  const nodes = useMemo(() => flatten(buildTree(folders)), [folders])
  const [choice, setChoice] = useState<string | null>(null)
  const isCurrent = (value: string) =>
    currentFolderId !== undefined && (currentFolderId ?? ROOT) === value

  const options = [
    { value: ROOT, label: t('library.shortcuts.root'), depth: 0, root: true },
    ...nodes.map((node) => ({ value: node.id, label: node.name, depth: node.depth + 1, root: false })),
  ]

  return (
    <form
      className="flex flex-col gap-5"
      onSubmit={(event) => {
        event.preventDefault()
        if (!choice) return
        const option = options.find((candidate) => candidate.value === choice)!
        onMove(choice === ROOT ? null : choice, option.label)
      }}
    >
      <fieldset className="flex max-h-[45dvh] flex-col gap-1 overflow-y-auto rounded-field border-[1.5px] border-line p-2">
        <legend className="sr-only">{t('library.moveDialog.destination')}</legend>
        {options.map((option) => {
          const disabled = excluded?.has(option.value) || isCurrent(option.value)
          const Icon = option.root ? Home : FolderIcon
          return (
            <label
              key={option.value}
              style={{ paddingLeft: `${0.75 + option.depth * 1.1}rem` }}
              className={cn(
                'flex min-h-11 cursor-pointer items-center gap-3 rounded-xl pr-3 text-[0.9375rem]',
                'has-[:checked]:bg-sun has-[:focus-visible]:outline-3 has-[:focus-visible]:outline-focus',
                disabled ? 'cursor-not-allowed opacity-55' : 'hover:bg-ink/5',
              )}
            >
              <input
                type="radio"
                name={name}
                value={option.value}
                disabled={disabled}
                checked={choice === option.value}
                onChange={() => setChoice(option.value)}
                className="sr-only"
              />
              <Icon className="size-4 shrink-0" aria-hidden="true" />
              <span className="min-w-0 flex-1 truncate">{option.label}</span>
              {isCurrent(option.value) && (
                <span className="text-xs text-muted">({t('library.moveDialog.here')})</span>
              )}
            </label>
          )
        })}
      </fieldset>
      {error && (
        <p role="alert" className="text-sm text-danger">
          {error}
        </p>
      )}
      <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <Button variant="outline" onClick={onCancel}>
          {t('common.cancel')}
        </Button>
        <Button type="submit" variant="accent" loading={loading} disabled={!choice}>
          {t('library.moveDialog.move')}
        </Button>
      </div>
    </form>
  )
}
