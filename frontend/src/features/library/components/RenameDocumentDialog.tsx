import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Button, Dialog, TextField } from '@/shared/ui'
import { useDocumentActions } from '../hooks/useDocumentActions'

const MAX = 200

/** Đổi tên tài liệu; tên trùng trong cùng thư mục được báo kèm tên gợi ý (NCL-05-CN-001). */
export function RenameDocumentDialog({
  open,
  document,
  onClose,
}: {
  open: boolean
  document: { id: string; title: string }
  onClose: () => void
}) {
  const { t } = useTranslation()
  return (
    <Dialog open={open} onClose={onClose} title={t('library.renameDocument.title')}>
      <RenameForm document={document} onDone={onClose} />
    </Dialog>
  )
}

function RenameForm({ document, onDone }: { document: { id: string; title: string }; onDone: () => void }) {
  const { t } = useTranslation()
  const actions = useDocumentActions()
  const [title, setTitle] = useState(document.title)
  const [error, setError] = useState<string | null>(null)
  const [suggestion, setSuggestion] = useState<string | null>(null)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const trimmed = title.trim()
    if (!trimmed) return setError(t('library.renameDocument.required'))
    if (trimmed.length > MAX) return setError(t('library.renameDocument.tooLong'))
    if (trimmed === document.title) return onDone()
    const failure = await actions.rename(document.id, trimmed)
    if (!failure) return onDone()
    if (failure.code === 'LIBRARY_NAME_TAKEN') {
      setError(t('library.renameDocument.taken'))
      setSuggestion(typeof failure.details?.suggestion === 'string' ? failure.details.suggestion : null)
    } else {
      setError(t(`errors.${failure.code}`, { defaultValue: t('library.actionFailed') }))
    }
  }

  return (
    <form noValidate onSubmit={(event) => void submit(event)} className="flex flex-col gap-5">
      <div className="flex flex-col gap-2">
        <TextField
          label={t('library.renameDocument.label')}
          value={title}
          autoFocus
          autoComplete="off"
          onChange={(event) => {
            setTitle(event.target.value)
            setError(null)
            setSuggestion(null)
          }}
          error={error ?? undefined}
        />
        {suggestion && (
          <Button variant="ghost" className="self-start underline underline-offset-4" onClick={() => setTitle(suggestion)}>
            {t('library.folderDialog.useSuggestion', { name: suggestion })}
          </Button>
        )}
      </div>
      <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <Button variant="outline" onClick={onDone}>
          {t('common.cancel')}
        </Button>
        <Button type="submit" variant="accent" loading={actions.pending}>
          {t('library.renameDocument.save')}
        </Button>
      </div>
    </form>
  )
}
