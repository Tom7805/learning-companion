import { Check, Pencil, Plus, Trash2 } from 'lucide-react'
import { useId, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { toApiError } from '@/shared/api/errors'
import { cn } from '@/shared/lib/cn'
import { Button, Checkbox, ConfirmDialog, Dialog, IconButton, TextField, toast } from '@/shared/ui'
import { libraryApi } from '../api/libraryApi'
import { useLibraryMutation, useTags } from '../api/queries'
import { TAG_COLORS, type DocumentCard, type Tag, type TagColor } from '../types'
import { tagSwatch } from '../lib/tagColors'
import { TagChip } from './TagChip'

const MAX = 40

/** Chọn màu nhãn bằng nút chọn: mỗi màu có tên đọc được, không chỉ dựa vào màu. */
function ColorChoice({ value, onChange }: { value: TagColor; onChange: (color: TagColor) => void }) {
  const { t } = useTranslation()
  const name = useId()
  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="mb-2 text-sm font-medium">{t('library.tags.color')}</legend>
      <div className="flex flex-wrap gap-2">
        {TAG_COLORS.map((color) => (
          <label
            key={color}
            title={t(`library.tags.colors.${color}`)}
            className={cn(
              'relative grid size-11 cursor-pointer place-items-center rounded-xl border-[1.5px] border-ink',
              'has-[:focus-visible]:outline-3 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-focus',
              tagSwatch[color],
            )}
          >
            <input
              type="radio"
              name={name}
              value={color}
              checked={value === color}
              onChange={() => onChange(color)}
              aria-label={t('library.tags.colorOption', { color: t(`library.tags.colors.${color}`) })}
              className="sr-only"
            />
            {value === color && <Check className="size-5" aria-hidden="true" strokeWidth={3} />}
          </label>
        ))}
      </div>
    </fieldset>
  )
}

/** Biểu mẫu tạo hoặc sửa một nhãn. */
function TagForm({ tag, onDone, onCancel }: { tag?: Tag; onDone: (tag: Tag) => void; onCancel?: () => void }) {
  const { t } = useTranslation()
  const [name, setName] = useState(tag?.name ?? '')
  const [color, setColor] = useState<TagColor>(tag?.color ?? 'SUN')
  const [error, setError] = useState<string | null>(null)
  const save = useLibraryMutation(() =>
    tag ? libraryApi.updateTag(tag.id, name.trim(), color) : libraryApi.createTag(name.trim(), color),
  )

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const trimmed = name.trim()
    if (!trimmed) return setError(t('library.tags.required'))
    if (trimmed.length > MAX) return setError(t('library.tags.tooLong'))
    try {
      const saved = await save.mutateAsync(undefined)
      setName('')
      onDone(saved)
    } catch (caught) {
      const apiError = toApiError(caught)
      setError(
        apiError.code === 'LIBRARY_TAG_NAME_TAKEN'
          ? t('library.tags.taken')
          : t(`errors.${apiError.code}`, { defaultValue: t('library.actionFailed') }),
      )
    }
  }

  return (
    <form
      noValidate
      onSubmit={(event) => void submit(event)}
      className="flex flex-col gap-4 rounded-card border-[1.5px] border-line bg-paper p-4"
      data-testid="tag-form"
    >
      <TextField
        label={tag ? t('library.tags.name') : t('library.tags.new')}
        value={name}
        maxLength={MAX + 10}
        autoComplete="off"
        onChange={(event) => {
          setName(event.target.value)
          setError(null)
        }}
        error={error ?? undefined}
      />
      <ColorChoice value={color} onChange={setColor} />
      <div className="flex flex-wrap justify-end gap-3">
        {onCancel && (
          <Button variant="outline" onClick={onCancel}>
            {t('library.tags.cancel')}
          </Button>
        )}
        <Button type="submit" variant="dark" loading={save.isPending} icon={tag ? undefined : <Plus className="size-4" />}>
          {tag ? t('library.tags.save') : t('library.tags.create')}
        </Button>
      </div>
    </form>
  )
}

/** Quản lý nhãn: tạo, đổi tên, đổi màu và xóa. Không giới hạn số nhãn. */
export function TagManagerDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { t } = useTranslation()
  return (
    <Dialog open={open} onClose={onClose} title={t('library.tags.title')}>
      <TagManagerBody />
    </Dialog>
  )
}

function TagManagerBody() {
  const { t } = useTranslation()
  const tags = useTags()
  const [editing, setEditing] = useState<string | null>(null)
  const [deleting, setDeleting] = useState<Tag | null>(null)
  const remove = useLibraryMutation((id: string) => libraryApi.deleteTag(id))

  return (
    <div className="flex flex-col gap-4">
      {tags.data?.length === 0 && <p className="text-sm leading-relaxed text-muted">{t('library.tags.empty')}</p>}
      <ul className="flex flex-col gap-2" data-testid="tag-list">
        {tags.data?.map((tag) =>
          editing === tag.id ? (
            <li key={tag.id}>
              <TagForm tag={tag} onDone={() => setEditing(null)} onCancel={() => setEditing(null)} />
            </li>
          ) : (
            <li key={tag.id} className="flex items-center gap-2">
              <TagChip tag={tag} className="text-sm" />
              <span className="flex-1 text-[0.8125rem] text-muted">
                {t('library.tags.count', { count: tag.documentCount })}
              </span>
              <IconButton
                label={t('library.tags.edit', { name: tag.name })}
                icon={<Pencil className="size-4" />}
                onClick={() => setEditing(tag.id)}
              />
              <IconButton
                label={t('library.tags.delete', { name: tag.name })}
                icon={<Trash2 className="size-4" />}
                onClick={() => setDeleting(tag)}
              />
            </li>
          ),
        )}
      </ul>
      {!editing && <TagForm onDone={() => toast.success(t('library.tags.done'))} />}
      <ConfirmDialog
        open={deleting !== null}
        title={t('library.tags.deleteTitle', { name: deleting?.name ?? '' })}
        description={t('library.tags.deleteBody')}
        confirmLabel={t('library.tags.delete', { name: deleting?.name ?? '' })}
        cancelLabel={t('common.cancel')}
        loading={remove.isPending}
        onCancel={() => setDeleting(null)}
        onConfirm={() => {
          if (!deleting) return
          remove.mutate(deleting.id, {
            onSuccess: () => setDeleting(null),
            onError: () => toast.error(t('library.actionFailed')),
          })
        }}
      />
    </div>
  )
}

interface TagAssignDialogProps {
  open: boolean
  documents: Pick<DocumentCard, 'id' | 'title' | 'tagIds'>[]
  loading?: boolean
  onClose: () => void
  /** Nhãn cần gắn thêm và cần gỡ, so với nhãn các tài liệu đang có. */
  onApply: (add: string[], remove: string[]) => void
}

/** Gắn hoặc gỡ nhãn cho một hay nhiều tài liệu; tạo được nhãn mới ngay tại đây. */
export function TagAssignDialog({ open, documents, loading, onClose, onApply }: TagAssignDialogProps) {
  const { t } = useTranslation()
  const title =
    documents.length === 1
      ? t('library.tags.editTitle', { title: documents[0].title })
      : t('library.tags.assignTitle', { count: documents.length })
  return (
    <Dialog open={open} onClose={onClose} title={title} description={t('library.tags.assignHint')}>
      <TagAssignBody documents={documents} loading={loading} onCancel={onClose} onApply={onApply} />
    </Dialog>
  )
}

function TagAssignBody({
  documents,
  loading,
  onCancel,
  onApply,
}: Omit<TagAssignDialogProps, 'open' | 'onClose'> & { onCancel: () => void }) {
  const { t } = useTranslation()
  const tags = useTags()
  const everyone = (tagId: string) => documents.every((document) => document.tagIds?.includes(tagId))
  const someone = (tagId: string) => documents.some((document) => document.tagIds?.includes(tagId))
  const [checked, setChecked] = useState<Record<string, boolean>>({})
  const isChecked = (tagId: string) => checked[tagId] ?? everyone(tagId)

  const apply = (event: FormEvent) => {
    event.preventDefault()
    const all = tags.data ?? []
    const add = all.filter((tag) => isChecked(tag.id) && !everyone(tag.id)).map((tag) => tag.id)
    const remove = all.filter((tag) => !isChecked(tag.id) && someone(tag.id)).map((tag) => tag.id)
    onApply(add, remove)
  }

  return (
    <div className="flex flex-col gap-4">
      <form id="tag-assign" onSubmit={apply} className="flex flex-col gap-2">
        {tags.data?.length === 0 && <p className="text-sm text-muted">{t('library.tags.empty')}</p>}
        <fieldset className="flex max-h-[35dvh] flex-col gap-2 overflow-y-auto">
          <legend className="sr-only">{t('library.filters.tags')}</legend>
          {tags.data?.map((tag) => (
            <Checkbox
              key={tag.id}
              checked={isChecked(tag.id)}
              onChange={(event) => setChecked((state) => ({ ...state, [tag.id]: event.target.checked }))}
              label={<TagChip tag={tag} className="text-sm" />}
              aria-label={tag.name}
            />
          ))}
        </fieldset>
      </form>
      <TagForm onDone={(tag) => setChecked((state) => ({ ...state, [tag.id]: true }))} />
      <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <Button variant="outline" onClick={onCancel}>
          {t('common.cancel')}
        </Button>
        <Button type="submit" form="tag-assign" variant="accent" loading={loading}>
          {t('library.tags.add')}
        </Button>
      </div>
    </div>
  )
}
